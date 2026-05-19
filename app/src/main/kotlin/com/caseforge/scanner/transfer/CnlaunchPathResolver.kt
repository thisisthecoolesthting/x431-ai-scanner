package com.caseforge.scanner.transfer

import android.os.Environment
import java.io.File

/**
 * X431 stores vehicle databases under shared storage; path varies by tablet OS and app version.
 */
object CnlaunchPathResolver {

    data class Inventory(
        val root: File,
        val fileCount: Int,
        val totalBytes: Long,
        val pathsTried: List<String>,
    ) {
        val hasData: Boolean get() = fileCount > 0 && totalBytes > 0
    }

    private val X431_PACKAGES = listOf(
        "com.cnlaunch.x431padv",
        "com.cnlaunch.x431padv2",
        "com.cnlaunch.diagnose.x431pro",
        "com.cnlaunch.diagnosemodule",
        "com.cnlaunch.x431pro",
        "com.cnlaunch.x431pro3",
        "com.x431.diagnose",
    )

    fun candidateRoots(): List<File> {
        val storage = Environment.getExternalStorageDirectory()
        val bases = listOf(
            storage,
            File("/storage/emulated/0"),
            File("/sdcard"),
        ).distinctBy { it.absolutePath }

        val roots = mutableListOf<File>()
        for (base in bases) {
            roots += File(base, "cnlaunch")
        }
        for (pkg in X431_PACKAGES) {
            roots += File(storage, "Android/data/$pkg/files/cnlaunch")
            roots += File(storage, "Android/data/$pkg/cnlaunch")
            roots += File("/storage/emulated/0/Android/data/$pkg/files/cnlaunch")
        }
        return roots.distinctBy { it.absolutePath }
    }

    fun scan(): Inventory {
        val tried = mutableListOf<String>()
        var best = Inventory(
            root = File(Environment.getExternalStorageDirectory(), "cnlaunch"),
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

    fun bestRootOrDefault(): File = scan().let { if (it.hasData) it.root else File(Environment.getExternalStorageDirectory(), "cnlaunch") }

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
