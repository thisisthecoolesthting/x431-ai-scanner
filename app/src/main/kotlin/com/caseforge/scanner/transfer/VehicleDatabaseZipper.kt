package com.caseforge.scanner.transfer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Streams vehicle database files into a zip archive.
 * Resolves the real OEM storage folder and refuses empty exports.
 */
class VehicleDatabaseZipper(
    private val sourceRoot: File = VehicleDatabasePathResolver.bestRootOrDefault(),
) {

    data class ZipProgress(val bytesWritten: Long, val filesZipped: Int)

    class EmptyVehicleDatabaseException(
        val inventory: VehicleDatabasePathResolver.Inventory,
    ) : Exception(emptyMessage(inventory)) {
        companion object {
            fun emptyMessage(inv: VehicleDatabasePathResolver.Inventory): String =
                buildString {
                    append("No readable files in vehicle database (")
                    append(inv.fileCount)
                    append(" files, ")
                    append(inv.totalBytes / 1024)
                    append(" KB at ")
                    append(inv.root.absolutePath)
                    append("). ")
                    append("Grant \"All files access\" for Together Car Works in Settings, ")
                    append("connect to a vehicle in the diagnostic app to download databases, then retry. ")
                    append("Tried: ")
                    append(inv.pathsTried.take(5).joinToString())
                }
        }
    }

    val inventory: VehicleDatabasePathResolver.Inventory
        get() = VehicleDatabasePathResolver.scan().let {
            if (it.root.absolutePath == sourceRoot.absolutePath) it
            else {
                val (c, b) = countAt(sourceRoot)
                it.copy(root = sourceRoot, fileCount = c, totalBytes = b)
            }
        }

    val exists: Boolean get() = sourceRoot.isDirectory

    val hasExportableData: Boolean get() = inventory.hasData

    fun totalBytesEstimate(): Long = inventory.totalBytes

    fun zipProgressFlow(output: OutputStream): Flow<ZipProgress> = flow {
        val inv = inventory
        if (!inv.hasData) throw EmptyVehicleDatabaseException(inv)

        var bytes = 0L
        var files = 0
        val zos = ZipOutputStream(BufferedOutputStream(output, 64 * 1024))
        try {
            walkFiles(sourceRoot).forEach { file ->
                files++
                val rel = file.relativeTo(sourceRoot).path.replace('\\', '/')
                val entry = ZipEntry("vehicle-database/$rel")
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
            if (files == 0) throw EmptyVehicleDatabaseException(inv)
            zos.finish()
            emit(ZipProgress(bytes, files))
        } finally {
            zos.close()
        }
    }

    fun zipToFileBlocking(dest: File): ZipProgress {
        var result = ZipProgress(0, 0)
        dest.outputStream().use { out ->
            kotlinx.coroutines.runBlocking {
                zipProgressFlow(out).collect { result = it }
            }
        }
        if (dest.length() < 512) {
            throw EmptyVehicleDatabaseException(inventory)
        }
        return result
    }

    private fun walkFiles(root: File): Sequence<File> =
        root.walkTopDown().maxDepth(32).onFail { _, _ -> }.filter { it.isFile && it.canRead() }

    private fun countAt(root: File): Pair<Int, Long> {
        var count = 0
        var bytes = 0L
        walkFiles(root).forEach {
            count++
            bytes += it.length()
        }
        return count to bytes
    }
}
