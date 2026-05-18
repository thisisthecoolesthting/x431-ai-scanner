package com.caseforge.scanner.transfer

import android.os.Environment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Streams `/sdcard/cnlaunch/` into a zip without a temp file on device storage.
 */
class CnlaunchZipper(
    private val sourceRoot: File = File(Environment.getExternalStorageDirectory(), "cnlaunch"),
) {

    data class ZipProgress(val bytesWritten: Long, val filesZipped: Int)

    val exists: Boolean get() = sourceRoot.isDirectory

    fun totalBytesEstimate(): Long {
        if (!sourceRoot.isDirectory) return 0L
        var sum = 0L
        sourceRoot.walkTopDown().maxDepth(32).forEach { f ->
            if (f.isFile) sum += f.length()
        }
        return sum
    }

    fun zipProgressFlow(output: OutputStream): Flow<ZipProgress> = flow {
        var bytes = 0L
        var files = 0
        val zos = ZipOutputStream(BufferedOutputStream(output, 64 * 1024))
        try {
            if (!sourceRoot.isDirectory) {
                emit(ZipProgress(0, 0))
                return@flow
            }
            sourceRoot.walkTopDown().maxDepth(32).forEach { file ->
                if (!file.isFile) return@forEach
                files++
                val rel = file.relativeTo(sourceRoot).path.replace('\\', '/')
                val entry = ZipEntry("cnlaunch/$rel")
                zos.putNextEntry(entry)
                file.inputStream().use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        zos.write(buf, 0, n)
                        bytes += n
                        if (bytes % (256 * 1024) < 8192) emit(ZipProgress(bytes, files))
                    }
                }
                zos.closeEntry()
            }
            zos.finish()
            emit(ZipProgress(bytes, files))
        } finally {
            zos.close()
        }
    }

}
