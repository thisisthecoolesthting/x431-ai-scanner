package com.caseforge.scanner.vci

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** Shared binary / hex frame I/O for SPP and USB serial transports. */
internal object VciFramePump {
    private const val TAG = "VciFramePump"

    fun sendFrame(
        outputStream: OutputStream,
        frame: VciFrame,
        useHexEncoding: Boolean,
        onDisconnected: () -> Unit,
    ) {
        val bytes = if (useHexEncoding) {
            (frame.encodeHex() + "\n").toByteArray(Charsets.US_ASCII)
        } else {
            frame.encode()
        }
        synchronized(outputStream) {
            try {
                outputStream.write(bytes)
                outputStream.flush()
            } catch (e: IOException) {
                if (e.message?.lowercase()?.contains("broken pipe") == true) {
                    onDisconnected()
                }
                throw VciException.SendFailed(e.message ?: "IO error on send")
            }
        }
    }

    fun startReceiveJob(
        scope: CoroutineScope,
        inputStream: InputStream,
        useHexEncoding: Boolean,
        receiveBufferSize: Int,
        isActiveLink: () -> Boolean,
        frameChannel: Channel<VciFrame>,
        onDisconnected: () -> Unit,
    ): Job = scope.launch {
        try {
            if (useHexEncoding) {
                receiveHexLines(inputStream, receiveBufferSize, frameChannel, isActiveLink)
            } else {
                receiveBinaryFrames(inputStream, receiveBufferSize, frameChannel, isActiveLink)
            }
        } finally {
            if (isActiveLink()) onDisconnected()
        }
    }

    private suspend fun receiveHexLines(
        inputStream: InputStream,
        receiveBufferSize: Int,
        frameChannel: Channel<VciFrame>,
        isActiveLink: () -> Boolean,
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream), receiveBufferSize)
        while (coroutineContext.isActive && isActiveLink()) {
            try {
                val line = reader.readLine() ?: break
                when (val result = VciFrame.decodeHex(line)) {
                    is VciFrame.DecodeResult.Ok -> frameChannel.trySend(result.frame)
                    is VciFrame.DecodeResult.ChecksumMismatch -> frameChannel.trySend(result.frame)
                    is VciFrame.DecodeResult.Error -> Log.e(TAG, "Hex decode: ${result.reason}")
                }
            } catch (e: IOException) {
                Log.w(TAG, "Hex read: ${e.message}")
                break
            }
        }
    }

    private suspend fun receiveBinaryFrames(
        inputStream: InputStream,
        receiveBufferSize: Int,
        frameChannel: Channel<VciFrame>,
        isActiveLink: () -> Boolean,
    ) {
        val headerBuf = ByteArray(VciFrame.HEADER_SIZE + VciFrame.OPCODE_SIZE + VciFrame.LENGTH_SIZE)
        val tempPayload = ByteArray(receiveBufferSize)
        while (coroutineContext.isActive && isActiveLink()) {
            try {
                var bytesRead = 0
                while (bytesRead < headerBuf.size) {
                    val n = inputStream.read(headerBuf, bytesRead, headerBuf.size - bytesRead)
                    if (n < 0) return
                    bytesRead += n
                }
                val payloadLen = ((headerBuf[4].toInt() and 0xFF) shl 8) or (headerBuf[5].toInt() and 0xFF)
                if (payloadLen > receiveBufferSize - VciFrame.MIN_FRAME_SIZE) {
                    Log.e(TAG, "Unreasonable payload length: $payloadLen")
                    continue
                }
                val totalRemaining = payloadLen + VciFrame.CHECKSUM_SIZE
                var payloadRead = 0
                while (payloadRead < totalRemaining) {
                    val n = inputStream.read(tempPayload, payloadRead, totalRemaining - payloadRead)
                    if (n < 0) return
                    payloadRead += n
                }
                val fullFrame = ByteArray(headerBuf.size + totalRemaining)
                headerBuf.copyInto(fullFrame)
                tempPayload.copyInto(fullFrame, destinationOffset = headerBuf.size, endIndex = totalRemaining)
                when (val result = VciFrame.decode(fullFrame)) {
                    is VciFrame.DecodeResult.Ok -> frameChannel.trySend(result.frame)
                    is VciFrame.DecodeResult.ChecksumMismatch -> frameChannel.trySend(result.frame)
                    is VciFrame.DecodeResult.Error -> Log.e(TAG, "Binary decode: ${result.reason}")
                }
            } catch (e: IOException) {
                Log.w(TAG, "Binary read: ${e.message}")
                break
            }
        }
    }
}
