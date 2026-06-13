package com.caseforge.scanner.transfer

import android.os.Environment
import com.caseforge.scanner.oem.OemTabletCompat
import java.io.File

/**
 * Locates vehicle database files written by the factory diagnostic app.
 */
object VehicleDatabasePathResolver {

    private val OEM_PACKAGES = OemTabletCompat.diagnosticAppPackages.toList()
    private val OEM_DATA_PATH = OemTabletCompat.oemVehicleDataDir
    private val OEM_DIR_NAME = OemTabletCompat.oemVehicleDataDirName

    data class Inventory(
        val root: File,
        val fileCount: Int,
        val totalBytes: Long,
        val pathsTried: List<String>,
    ) {
        val hasData: Boolean get() = fileCount > 0 && totalBytes > 0
    }

    fun candidateRoots(): List<File> {
        val storage = Environment.getExternalStorageDirectory()
        val bases = listOf(
            storage,
            File("/storage/emulated/0"),
            File(OEM_DATA_PATH).parentFile ?: File("/sdcard"),
        ).distinctBy { it.absolutePath }

        val roots = mutableListOf<File>()
        for (base in bases) {
            roots += File(base, OEM_DIR_NAME)
        }
        for (pkg in OEM_PACKAGES) {
            roots += File(storage, "Android/data/$pkg/files/$OEM_DIR_NAME")
            roots += File(storage, "Android/data/$pkg/$OEM_DIR_NAME")
            roots += File("/storage/emulated/0/Android/data/$pkg/files/$OEM_DIR_NAME")
        }
        return roots.distinctBy { it.absolutePath }
    }

    fun scan(): Inventory {
        val tried = mutableListOf<String>()
        var best = Inventory(
            root = File(OEM_DATA_PATH),
            fileCount = 0,
            totalBytes = 0,
            pathsTried = emptyList(),
        )
        for (candidate in candidateRoots()) {
            tried += candidate.absolutePath
            if (!candidate.isDirectory) continue
            val (count, bytes) = countReadableFiles(candidate)
            if (count > best.fileCount || (count == best.fileCount && bytes > best.totalBytes)) {
                best = Inventory(candidate, count, bytes, tried.toList())
            }
        }
        return best.copy(pathsTried = tried)
    }

    fun bestRootOrDefault(): File =
        scan().let { if (it.hasData) it.root else File(OEM_DATA_PATH) }

    private fun countReadableFiles(root: File): Pair<Int, Long> {
        if (!root.isDirectory) return 0 to 0L
        var count = 0
        var bytes = 0L
        root.walkTopDown().maxDepth(32).onFail { _, _ -> }.forEach { f ->
            if (f.isFile && f.canRead()) {
                count++
                bytes += f.length()
            }
        }
        return count to bytes
    }
}
