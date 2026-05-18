package com.caseforge.scanner.vci

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.caseforge.scanner.App
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.withTimeout
data class VciDiagnosticStep(
    val name: String,
    val pass: Boolean,
    val detail: String,
)

/**
 * End-to-end VCI connect chain for the diagnostics screen and [DirectVciSession].
 */
object VciConnectionDiagnostics {

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun adapterOrNull(context: Context): BluetoothAdapter? {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return mgr?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    @Suppress("MissingPermission")
    suspend fun runChain(
        context: Context,
        settings: SettingsRepo,
        tryLiveConnect: Boolean = true,
        macOverride: String? = null,
    ): List<VciDiagnosticStep> {
        val steps = mutableListOf<VciDiagnosticStep>()
        fun step(name: String, pass: Boolean, detail: String) {
            steps += VciDiagnosticStep(name, pass, detail)
        }

        val adapter = adapterOrNull(context)
        if (adapter == null) {
            step("Bluetooth adapter", false, "getDefaultAdapter() returned null")
            return steps
        }
        step("Bluetooth adapter", true, adapter.name ?: "ok")

        if (!adapter.isEnabled) {
            step("Bluetooth enabled", false, "Turn on Bluetooth in system settings")
            return steps
        }
        step("Bluetooth enabled", true, "radio on")

        val permOk = hasBluetoothConnectPermission(context)
        step(
            "BLUETOOTH_CONNECT",
            permOk,
            if (permOk) "granted" else "denied — grant in app settings",
        )
        if (!permOk) return steps

        val x431Up = App.isX431Foreground(context)
        step(
            "X431 not holding foreground",
            !x431Up,
            if (x431Up) {
                "X431 is foreground — force-stop X431 so it releases the VCI SPP socket"
            } else {
                "X431 not in foreground"
            },
        )

        val bonded = adapter.bondedDevices.orEmpty()
        step("Bonded devices", true, "${bonded.size} paired")
        bonded.forEach { d ->
            step(
                "  • ${d.name ?: "?"}",
                true,
                d.address,
            )
        }

        val savedMac = macOverride ?: settings.vciSelectedBtAddress
        val matched = VciSocketClient(context).findBondedVciDevices()
        val target: BluetoothDevice? = when {
            savedMac != null -> bonded.firstOrNull { it.address.equals(savedMac, true) }
            matched.isNotEmpty() -> matched.first()
            else -> null
        }

        val prefixHit = matched.firstOrNull()?.let { dev ->
            VciSocketClient.VCI_NAME_PREFIXES.firstOrNull { p ->
                (dev.name ?: "").startsWith(p, ignoreCase = true)
            }
        }
        step(
            "VCI name match",
            target != null,
            when {
                target == null -> "No prefix match (${VciSocketClient.VCI_NAME_PREFIXES.joinToString()}) — pick device below"
                prefixHit != null -> "prefix \"$prefixHit\" → ${target.name}"
                else -> "using saved/selected ${target.name}"
            },
        )
        if (target == null) return steps

        if (!tryLiveConnect) return steps

        // SPP attempt
        val sppClient = VciSocketClient(context, useHexEncoding = settings.vciUseHexEncoding)
        val sppResult = runCatching {
            withTimeout(25_000) { sppClient.connect(target.address) }
        }
        sppResult.fold(
            onSuccess = { r ->
                r.fold(
                    onSuccess = {
                        step("SPP connect", true, "connected ${target.address}; receive loop running")
                        sppClient.disconnect()
                    },
                    onFailure = { e ->
                        step("SPP connect", false, e.message ?: e.toString())
                    },
                )
            },
            onFailure = { e ->
                step("SPP connect", false, "timeout/error: ${e.message}")
            },
        )

        if (sppResult.getOrNull()?.isSuccess == true) return steps

        // BLE fallback probe
        step("BLE fallback", true, "SPP failed — trying BLE GATT (fff0 / ISSC)")
        val ble = VciBleClient(context)
        val bleResult = ble.probeDevice(target)
        step("BLE GATT", bleResult.isSuccess, bleResult.exceptionOrNull()?.message ?: "probe ok")
        ble.close()

        return steps
    }
}
