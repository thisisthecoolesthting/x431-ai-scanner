package com.caseforge.scanner.transfer

import android.os.Environment
import java.io.File

/**
 * Locates vehicle database files written by the OEM diagnostic app.
 * The OEM stores data under shared storage; the exact path varies by tablet
 * OS version, app variant, and Android storage policy.
 *
 * The canonical storage path is encapsulated in [OEM_DATA_PATH] and nowhere else.
 */
object VehicleDatabasePathResolver {

    /** Canonical OEM data path — the only place this literal may appear. */
    private const val OEM_DATA_PATH = "/sdcard/cnlaunch/"

    /** OEM diagnostic app package names (all variants). */
    private val OEM_PACKAGES = listOf(
        "com.cnlaunch.x431padv",
        "com.cnlaunch.x431padv2",
        "com.cnlaunch.diagnose.x431pro",
        "com.cnlaunch.diagnosemodule",
        "com.cnlaunch.x431pro",
        "com.cnlaunch.x431pro3",
        "com.x431.diagnose",
    )

    private val OEM_DIR_NAME = OEM_DATA_PATH.trimEnd('/').substringAfterLast('/')

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
