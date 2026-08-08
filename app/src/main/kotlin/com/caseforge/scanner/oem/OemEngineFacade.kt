package com.caseforge.scanner.oem

import android.content.Context
import com.caseforge.scanner.data.SettingsRepo
import com.caseforge.scanner.obd.Elm327ObdTransport
import com.caseforge.scanner.obd.ObdEngine
import com.caseforge.scanner.obd.ObdSession
import com.caseforge.scanner.obd.ObdTransport
import com.caseforge.scanner.obd.StubObdTransport
import com.caseforge.scanner.obd.VciObdTransport
import com.caseforge.scanner.planb.MarqueWedgeConfig
import com.caseforge.scanner.planb.PlanBEngine
import com.caseforge.scanner.vci.DiagnosticConnector
import com.caseforge.scanner.vin.DodgeVinDetector
import com.caseforge.scanner.vin.FordVinDetector
import com.caseforge.scanner.vin.GmVinDetector
import com.caseforge.scanner.vin.HondaVinDetector
import com.caseforge.scanner.vin.HyundaiVinDetector
import com.caseforge.scanner.vin.JeepVinDetector
import com.caseforge.scanner.vin.NissanVinDetector
import com.caseforge.scanner.vin.ToyotaVinDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Plan B OEM native path: thin facade over [ObdEngine].
 * Uses [StubObdTransport] when VCI is not selected for this path;
 * otherwise [VciObdTransport] per [SettingsRepo.useVciNativeObdTransport].
 *
 * @param transportOverride optional factory for tests (bypasses VCI/stub selection).
 */
class OemEngineFacade(
    context: Context,
    private val settings: SettingsRepo,
    private val transportOverride: (() -> ObdTransport)? = null,
) {
    private val appContext = context.applicationContext

    /** Loaded once per facade — shared marque wedge matrix bundle. */
    private val wedgeMatrixSingleton by lazy { MarqueWedgeConfig.load(appContext) }

    private val planBEngine by lazy { PlanBEngine(settings) }

    /** Mirrors settings; kept for call sites that prefer the wedge naming. */
    val nativeObdWedgeEnabled: Boolean
        get() = settings.nativeObdWedgeEnabled

    private val refreshMutex = Mutex()

    private var heldTransport: ObdTransport? = null
    private var heldSession: ObdSession? = null

    /** Last [useVciNativeObdTransport] used for [heldSession]; null when [transportOverride] is set. */
    private var heldWantVci: Boolean? = null

    @Volatile
    private var cachedLines: List<String> = defaultDisabledLines()

    @Volatile
    private var lastRefreshedVinHolder: String? = null

    /** True while [heldTransport] reports connected after last [acquireSession]. */
    @Volatile
    var transportConnected: Boolean = false
        private set

    /** Tier indices captured at last transport session open (from prefs-backed snapshot). */
    val connectedAtTierSnapshot: Set<Int>
        get() = settings.snapshotTierIndicesAtLastConnect()

    /** Last VIN captured from native OBD [refreshSuspend] ([lastRefreshedVinHolder]); preserved when wedge is idle. */
    val lastRefreshedVin: String?
        get() = lastRefreshedVinHolder

    fun statusLines(): List<String> {
        val core = cachedLines.toList()
        if (!shouldAppendMarqueBanner()) return core
        val banner =
            planBEngine.marqueWedgeStatusBanner(
                wedgeMatrixSingleton,
                lastRefreshedVinHolder,
            )
        return banner?.let { core + it } ?: core
    }

    private fun shouldAppendMarqueBanner(): Boolean =
        settings.nativeObdExperimental ||
            settings.nativeObdUseVci ||
            settings.planbBodyRead ||
            settings.planbCoding ||
            settings.planbImmoInfo ||
            settings.planbProgramming

    /**
     * Marque / wedge matrix summary for a VIN (defaults to [lastRefreshedVin]).
     * Matrix tier flags come from bundled [com.caseforge.scanner.planb.MarqueWedgeMatrix.supportedTiers]
     * when the wedge asset loads; [MarqueWedgeConfig.findCardForVin] picks Ford / Dodge / Jeep rows by WMI + MY.
     */
    fun marqueWedgeLines(vin: String? = lastRefreshedVin): List<String> {
        val v = vin?.trim()?.takeIf { it.isNotEmpty() }
        if (v == null) {
            return listOf("Marque wedge: no VIN")
        }
        val matrix = wedgeMatrixSingleton
        val card = matrix?.let { MarqueWedgeConfig.findCardForVin(v, it) }
        val marque =
            when {
                card != null -> "${card.marque} (matrix card)"
                FordVinDetector.isLikelyFordVin(v) -> "Ford"
                JeepVinDetector.isLikelyJeepVin(v) -> "Jeep"
                DodgeVinDetector.isLikelyDodgeVin(v) -> "Dodge"
                GmVinDetector.isLikelyGmVin(v) -> "Chevrolet / GM"
                ToyotaVinDetector.isLikelyToyotaVin(v) -> "Toyota / Lexus"
                HondaVinDetector.isLikelyHondaVin(v) -> "Honda / Acura"
                NissanVinDetector.isLikelyNissanVin(v) -> "Nissan / Infiniti"
                HyundaiVinDetector.isLikelyHyundaiVin(v) -> "Hyundai / Kia"
                else -> "Unknown"
            }
        val cardLine =
            when {
                card != null ->
                    "Card: ${card.id} · ${card.platformCode} ${card.model} ${card.modelYearStart}-${card.modelYearEnd}"
                matrix != null ->
                    "Card: no WMI/year match · ${matrix.matrixSummaryLine()}"
                else -> "Card: —"
            }
        val tierBits =
            if (matrix != null) {
                val maxTier = matrix.supportedTiers.maxOrNull() ?: 3
                (0..maxTier).joinToString(" ") { tier ->
                    val on = matrix.supportedTiers.contains(tier)
                    "$tier=${if (on) "on" else "off"}"
                }
            } else {
                "—"
            }
        return listOf(
            "Marque wedge: $marque",
            cardLine,
            "Matrix tiers: $tierBits",
        )
    }

    /**
     * Refreshes OBD-derived status. Call from [kotlinx.coroutines.Dispatchers.IO].
     * @param preserveConnection when true and the held session still matches settings, skips [ObdSession.disconnect]
     * in `finally` so VCI/OBD transport stays up (Settings tier toggles).
     */
    suspend fun refreshSuspend(preserveConnection: Boolean = false) = refreshMutex.withLock {
        refreshSuspendLocked(preserveConnection)
    }

    /** Settings tier toggles while already linked — avoids a full disconnect/reconnect cycle. */
    suspend fun refreshSuspendPreserveConnection() = refreshSuspend(preserveConnection = true)

    private suspend fun refreshSuspendLocked(preserveConnection: Boolean) {
        if (!settings.nativeObdExperimental) {
            releaseHeldSession()
            cachedLines = defaultDisabledLines()
            return
        }

        val session = acquireSessionLocked(preserveConnection)
        val transport = heldTransport ?: error("heldTransport null after acquireSession")

        try {
            val engine = ObdEngine(session)
            val vin = kotlin.runCatching { engine.readVin() }.getOrNull()?.takeUnless { it.isBlank() }
            vin?.trim()?.takeIf { it.isNotEmpty() }?.let { lastRefreshedVinHolder = it }
            val storedN = kotlin.runCatching { engine.readStoredDtcs().size }.getOrElse { -1 }
            val pendingN = kotlin.runCatching { engine.readPendingDtcs().size }.getOrElse { -1 }
            val snap = kotlin.runCatching { engine.readLiveSnapshot() }.getOrNull()

            cachedLines = wedgeStatusLines(
                wedgeOn = true,
                transportConnected = transport.isConnected(),
                transportDescribe = transport.describe(),
                vin = vin,
                storedCount = storedN,
                pendingCount = pendingN,
                rpm = snap?.rpm,
                coolantCelsius = snap?.coolantCelsius,
                speedKmh = snap?.speedKmh,
            )
            settings.markObdRefreshSucceeded()
        } catch (ex: Throwable) {
            cachedLines =
                wedgeErrorLines(
                    describe = transport.describe(),
                    connected = transport.isConnected(),
                    error = ex,
                )
        } finally {
            transportConnected = heldTransport?.isConnected() == true
            if (!preserveConnection) {
                releaseHeldSession()
                transportConnected = false
            }
        }
    }

    private suspend fun acquireSessionLocked(preserveConnection: Boolean): ObdSession {
        if (preserveConnection && sessionMatchesSettings()) {
            return heldSession!!
        }
        releaseHeldSession()
        val transport =
            transportOverride?.invoke()
                ?: createNativeObdTransportLocked()
        heldTransport = transport
        heldWantVci =
            if (transportOverride != null) {
                null
            } else {
                settings.useVciNativeObdTransport()
            }
        val session = ObdSession(transport)
        withContext(Dispatchers.IO) {
            when (transport) {
                is VciObdTransport -> transport.connectSuspend()
                is Elm327ObdTransport -> transport.connect()
                else -> Unit
            }
            session.connect()
        }
        heldSession = session
        settings.applyTierSnapshotForNewTransportSession()
        transportConnected = transport.isConnected()
        return session
    }

    private suspend fun createNativeObdTransportLocked(): ObdTransport {
        if (!settings.useVciNativeObdTransport()) return StubObdTransport()
        val link = DiagnosticConnector.connect(appContext, settings).getOrNull()
        val elm = link?.elmEngine
        if (elm != null) {
            return Elm327ObdTransport(link, elm)
        }
        link?.disconnect()
        return VciObdTransport(appContext, settings)
    }

    private fun sessionMatchesSettings(): Boolean {
        if (heldSession == null) return false
        if (heldTransport?.isConnected() != true) return false
        if (transportOverride != null) return true
        return heldWantVci == settings.useVciNativeObdTransport()
    }

    private fun releaseHeldSession() {
        runCatching { heldSession?.disconnect() }
        heldSession = null
        runCatching {
            when (val t = heldTransport) {
                is Elm327ObdTransport -> t.close()
                else -> t?.disconnect()
            }
        }
        heldTransport = null
        heldWantVci = null
    }

    private fun defaultDisabledLines(): List<String> =
        wedgeStatusLines(
            wedgeOn = false,
            transportConnected = false,
            transportDescribe = "—",
            vin = null,
            storedCount = -1,
            pendingCount = -1,
            rpm = null,
            coolantCelsius = null,
            speedKmh = null,
        )
}

private fun wedgeStatusLines(
    wedgeOn: Boolean,
    transportConnected: Boolean,
    transportDescribe: String,
    vin: String?,
    storedCount: Int,
    pendingCount: Int,
    rpm: Int?,
    coolantCelsius: Int?,
    speedKmh: Int?,
): List<String> {
    fun countOrDash(n: Int): String = if (n < 0) "—" else n.toString()

    fun intOrDash(v: Int?): String = if (v == null) "—" else v.toString()

    return buildList {
        add(
            if (wedgeOn) {
                "Native OBD wedge: on"
            } else {
                "Native OBD wedge: off (settings)"
            },
        )
        add(
            "Transport: ${if (transportConnected) "connected" else "disconnected"} ($transportDescribe)",
        )
        add("VIN: ${vin ?: "—"}")
        add("Stored DTCs: ${countOrDash(storedCount)}")
        add("Pending DTCs: ${countOrDash(pendingCount)}")
        add(
            "RPM: ${intOrDash(rpm)} | Coolant: ${intOrDash(coolantCelsius)}°C | Speed: ${intOrDash(speedKmh)} km/h",
        )
    }
}

private fun wedgeErrorLines(
    describe: String,
    connected: Boolean,
    error: Throwable,
): List<String> {
    val base =
        wedgeStatusLines(
            wedgeOn = true,
            transportConnected = connected,
            transportDescribe = describe,
            vin = null,
            storedCount = -1,
            pendingCount = -1,
            rpm = null,
            coolantCelsius = null,
            speedKmh = null,
        )
    return base + ("Refresh error: ${error.message ?: error.javaClass.simpleName}")
}
