package com.caseforge.scanner.vci

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * BLE fallback when classic SPP fails (newer OEM VCIs).
 * Service/characteristic UUIDs from decompile (sb.b.java).
 */
class VciBleClient(private val context: Context) {

    companion object {
        private const val TAG = "VciBleClient"
        val SERVICE_FFF0: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val CHAR_ISSC: UUID = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455")
    }

    private var gatt: BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun probeDevice(device: BluetoothDevice): Result<Unit> = suspendCancellableCoroutine { cont ->
        Log.i(TAG, "BLE probe ${device.name} / ${device.address}")
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.i(TAG, "BLE connected, discovering services")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (!cont.isCompleted) {
                            cont.resume(
                                Result.failure(
                                    Exception("BLE disconnected status=$status"),
                                ),
                            )
                        }
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    if (!cont.isCompleted) {
                        cont.resume(Result.failure(Exception("service discovery status=$status")))
                    }
                    return
                }
                val svc = gatt.getService(SERVICE_FFF0)
                val ch = svc?.getCharacteristic(CHAR_ISSC)
                if (svc == null || ch == null) {
                    if (!cont.isCompleted) {
                        cont.resume(
                            Result.failure(
                                Exception("fff0/ISSC characteristic not found on ${device.name}"),
                            ),
                        )
                    }
                    return
                }
                Log.i(TAG, "BLE fff0 + ISSC present on ${device.name}")
                if (!cont.isCompleted) cont.resume(Result.success(Unit))
            }
        }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        cont.invokeOnCancellation { gatt?.close() }
    }

    fun close() {
        gatt?.close()
        gatt = null
    }
}
