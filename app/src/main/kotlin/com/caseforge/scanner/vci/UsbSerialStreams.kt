package com.caseforge.scanner.vci

import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal class UsbSerialInputStream(
    private val port: UsbSerialPort,
    private val readTimeoutMs: Int = 1000,
) : InputStream() {
    override fun read(): Int {
        val buf = ByteArray(1)
        return when (val n = port.read(buf, readTimeoutMs)) {
            0 -> 0
            -1 -> -1
            else -> buf[0].toInt() and 0xFF
        }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val tmp = ByteArray(len)
        val n = port.read(tmp, readTimeoutMs)
        if (n <= 0) return if (n == 0) 0 else -1
        tmp.copyInto(b, off, 0, n)
        return n
    }
}

internal class UsbSerialOutputStream(
    private val port: UsbSerialPort,
    private val writeTimeoutMs: Int = 1000,
) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()))
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        val chunk = b.copyOfRange(off, off + len)
        port.write(chunk, writeTimeoutMs)
    }
}
