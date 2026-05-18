package com.caseforge.scanner.transfer

import com.caseforge.scanner.agent.AgentActionLog
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Embedded LAN HTTP server: pass-code gate + streaming zip download of cnlaunch data.
 */
class LanFileServer(
    private val bindHost: String,
    port: Int,
    val passCode: String,
    private val zipper: CnlaunchZipper,
    private val actionLog: AgentActionLog,
    private val scope: CoroutineScope,
    private val onAutoStop: () -> Unit,
) : NanoHTTPD(bindHost, port) {

    companion object {
        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L
        private const val COOKIE_NAME = "together_export"
        private const val ZIP_NAME = "cnlaunch-bundle.zip"

        fun randomPassCode(): String = (1..6).joinToString("") { Random.nextInt(0, 10).toString() }
    }

    private val tokens = mutableSetOf<String>()
    private val lastActivity = AtomicLong(System.currentTimeMillis())
    private val downloadStarted = AtomicBoolean(false)
    private var idleJob: Job? = null

    val url: String get() = LanNetwork.baseUrl(bindHost, listeningPort)

    fun touchActivity() {
        lastActivity.set(System.currentTimeMillis())
    }

    fun startServer() {
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
        start(SOCKET_READ_TIMEOUT, false)
        actionLog.event("lan_export.start", "url=$url")
    }

    fun stopServer() {
        idleJob?.cancel()
        idleJob = null
        runCatching { stop() }
        tokens.clear()
        onAutoStop()
        actionLog.event("lan_export.stop", "port=$listeningPort")
    }

    override fun serve(session: IHTTPSession): Response {
        touchActivity()
        val uri = session.uri
        val method = session.method
        actionLog.event("lan_export.request", "$method $uri from ${session.remoteIpAddress}")

        return when {
            uri == "/" && method == Method.GET -> serveLanding(session)
            uri == "/auth" && method == Method.POST -> serveAuth(session)
            uri == "/download" && method == Method.GET -> serveDownload(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

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
        downloadStarted.set(true)
        val pipeIn = PipedInputStream(256 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)
        scope.launch(Dispatchers.IO) {
            try {
                zipper.zipProgressFlow(pipeOut).collect { }
                pipeOut.close()
            } catch (t: Throwable) {
                actionLog.event("lan_export.zip_error", t.message ?: "zip failed")
                runCatching { pipeOut.close() }
            } finally {
                actionLog.event("lan_export.download_complete", ZIP_NAME)
                stopServer()
            }
        }
        val resp = newChunkedResponse(Response.Status.OK, "application/zip", pipeIn)
        resp.addHeader("Content-Disposition", "attachment; filename=\"$ZIP_NAME\"")
        return resp
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
