package com.caseforge.scanner.transfer

import android.content.Context
import com.caseforge.scanner.agent.AgentActionLog
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.BindException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Embedded LAN HTTP server: pass-code gate + streaming zip download of cnlaunch data.
 * Binds [NetworkInterfaceHelper.BIND_ALL] so any interface can reach the port.
 */
class LanFileServer private constructor(
    requestedPort: Int,
    val passCode: String,
    val displayHost: String,
    private val appContext: Context,
    private val zipper: CnlaunchZipper,
    private val actionLog: AgentActionLog,
    private val scope: CoroutineScope,
    private val onAutoStop: () -> Unit,
) : NanoHTTPD(NetworkInterfaceHelper.BIND_ALL, requestedPort) {

    enum class ServerState {
        STARTING,
        LISTENING,
        STOPPED,
        ERROR,
    }

    companion object {
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val COOKIE_NAME = "together_export"
        private const val ZIP_NAME = "cnlaunch-bundle.zip"
        private val PORT_CANDIDATES = listOf(8765) + (8766..8799) + (8000..8099)

        fun randomPassCode(): String = (1..6).joinToString("") { Random.nextInt(0, 10).toString() }

        /**
         * Try ports until one binds; returns configured server or failure.
         */
        fun create(
            context: Context,
            displayHost: String,
            passCode: String,
            zipper: CnlaunchZipper,
            actionLog: AgentActionLog,
            scope: CoroutineScope,
            onAutoStop: () -> Unit,
        ): Result<LanFileServer> {
            var lastEx: Exception? = null
            for (port in PORT_CANDIDATES) {
                val server = LanFileServer(
                    requestedPort = port,
                    passCode = passCode,
                    displayHost = displayHost,
                    appContext = context.applicationContext,
                    zipper = zipper,
                    actionLog = actionLog,
                    scope = scope,
                    onAutoStop = onAutoStop,
                )
                try {
                    server._state.value = ServerState.STARTING
                    server.start(SOCKET_READ_TIMEOUT, false)
                    server._state.value = ServerState.LISTENING
                    server.startIdleWatcher()
                    actionLog.event("lan_export.start", "url=${server.publicUrl} port=${server.listeningPort}")
                    return Result.success(server)
                } catch (e: Exception) {
                    lastEx = e
                    server._state.value = ServerState.ERROR
                    server._lastError.value = e.message
                    runCatching { server.stop() }
                    if (e !is BindException && e !is IOException) break
                }
            }
            return Result.failure(lastEx ?: IOException("No port available"))
        }
    }

    private val _state = MutableStateFlow(ServerState.STOPPED)
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val tokens = mutableSetOf<String>()
    private val lastActivity = AtomicLong(System.currentTimeMillis())
    private val downloadStarted = AtomicBoolean(false)
    private var idleJob: Job? = null

    val boundPort: Int get() = listeningPort

    val publicUrl: String get() = LanNetwork.baseUrl(displayHost, listeningPort)

    fun touchActivity() {
        lastActivity.set(System.currentTimeMillis())
    }

    fun startIdleWatcher() {
        idleJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(5_000)
                if (downloadStarted.get()) continue
                val idle = System.currentTimeMillis() - lastActivity.get()
                if (idle >= IDLE_TIMEOUT_MS) {
                    actionLog.event("lan_export.idle_stop", "idleMs=$idle")
                    stopServer()
                    break
                }
            }
        }
    }

    fun stopServer() {
        idleJob?.cancel()
        idleJob = null
        runCatching { stop() }
        tokens.clear()
        _state.value = ServerState.STOPPED
        _lastError.value = null
        onAutoStop()
        actionLog.event("lan_export.stop", "port=$listeningPort")
    }

    override fun serve(session: IHTTPSession): Response {
        touchActivity()
        val uri = session.uri
        val method = session.method
        actionLog.event("lan_export.request", "$method $uri from ${session.remoteIpAddress}")

        return when {
            uri == "/health" && method == Method.GET -> serveHealth()
            uri == "/" && method == Method.GET -> serveLanding(session)
            uri == "/auth" && method == Method.POST -> serveAuth(session)
            uri == "/download" && method == Method.GET -> serveDownload(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveHealth(): Response =
        newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")

    private fun serveLanding(session: IHTTPSession): Response {
        if (isAuthed(session)) {
            return html(
                """
                <html><body>
                <h2>Together — cnlaunch export</h2>
                <p>Authenticated. Download the bundle:</p>
                <p><a href="/download">$ZIP_NAME</a></p>
                </body></html>
                """.trimIndent(),
            )
        }
        return html(
            """
            <html><body>
            <h2>Together — cnlaunch export</h2>
            <p>Enter the 6-digit code shown on the tablet:</p>
            <form method="POST" action="/auth">
              <input name="code" maxlength="6" inputmode="numeric" autocomplete="one-time-code"/>
              <button type="submit">Unlock download</button>
            </form>
            </body></html>
            """.trimIndent(),
        )
    }

    private fun serveAuth(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        runCatching { session.parseBody(files) }
        val code = session.parms["code"]?.trim().orEmpty()
        if (code != passCode) {
            actionLog.event("lan_export.auth_fail", session.remoteIpAddress)
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED,
                MIME_PLAINTEXT,
                "Invalid code",
            )
        }
        val token = UUID.randomUUID().toString()
        synchronized(tokens) { tokens.add(token) }
        actionLog.event("lan_export.auth_ok", session.remoteIpAddress)
        val resp = newFixedLengthResponse(
            Response.Status.REDIRECT,
            MIME_HTML,
            "",
        )
        resp.addHeader("Location", "/")
        resp.addHeader("Set-Cookie", "$COOKIE_NAME=$token; Path=/; HttpOnly")
        return resp
    }

    private fun serveDownload(session: IHTTPSession): Response {
        if (!isAuthed(session)) {
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
        }
        if (!zipper.exists) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "cnlaunch folder not found on tablet",
            )
        }
        if (!zipper.hasExportableData) {
            val inv = zipper.inventory
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                MIME_PLAINTEXT,
                CnlaunchZipper.EmptyCnlaunchException.emptyMessage(inv),
            )
        }
        downloadStarted.set(true)
        val zipFile = java.io.File(appContext.cacheDir, "cnlaunch-export-${System.currentTimeMillis()}.zip")
        return try {
            val progress = zipper.zipToFileBlocking(zipFile)
            actionLog.event(
                "lan_export.zip_ready",
                "files=${progress.filesZipped} bytes=${progress.bytesWritten} path=${zipFile.absolutePath}",
            )
            val resp = newFixedLengthResponse(
                Response.Status.OK,
                "application/zip",
                zipFile.inputStream(),
                zipFile.length(),
            )
            resp.addHeader("Content-Disposition", "attachment; filename=\"$ZIP_NAME\"")
            scope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(120_000)
                runCatching { zipFile.delete() }
                actionLog.event("lan_export.download_complete", ZIP_NAME)
                stopServer()
            }
            resp
        } catch (t: Throwable) {
            runCatching { zipFile.delete() }
            actionLog.event("lan_export.zip_error", t.message ?: "zip failed")
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                t.message ?: "zip failed",
            )
        }
    }

    private fun isAuthed(session: IHTTPSession): Boolean {
        val cookie = session.headers["cookie"] ?: return false
        val token = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$COOKIE_NAME=") }
            ?.substringAfter("=")
            ?: return false
        return synchronized(tokens) { tokens.contains(token) }
    }

    private fun html(body: String): Response =
        newFixedLengthResponse(Response.Status.OK, MIME_HTML, body)
}
