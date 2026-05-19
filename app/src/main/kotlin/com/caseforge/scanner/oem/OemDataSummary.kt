package com.caseforge.scanner.oem

import kotlinx.serialization.Serializable

/** High-level availability of the on-tablet vehicle data store. */
@Serializable
enum class DataStoreStatus {
    /** Resolver found a readable tree with at least one file. */
    READY,
    /** Folder exists but is empty or unreadable. */
    EMPTY,
    /** No candidate root contained data. */
    NOT_FOUND,
    /** Walk aborted (I/O, safety cap, or unexpected error). */
    ERROR,
}

/** Counts and byte totals for a classified file bucket. */
@Serializable
data class FileBucketStats(
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val extensions: Map<String, Int> = emptyMap(),
)

/**
 * Structured inventory of the OEM vehicle data tree — safe for Diagnostics UI and AI Copilot.
 * Contains no absolute paths, package names, or vendor tokens in [displayLines].
 */
@Serializable
data class OemDataSummary(
    val status: DataStoreStatus,
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    /** Extension (lowercase, no dot) → file count. */
    val extensionCounts: Map<String, Int> = emptyMap(),
    val reportFiles: FileBucketStats = FileBucketStats(),
    val catalogFiles: FileBucketStats = FileBucketStats(),
    val databaseFiles: FileBucketStats = FileBucketStats(),
    val otherFiles: FileBucketStats = FileBucketStats(),
    /** Epoch millis of the newest readable file, if any. */
    val latestModifiedEpochMs: Long? = null,
    /** Redacted file labels only — never raw paths. */
    val sampleNames: List<String> = emptyList(),
    val rootsChecked: Int = 0,
    val scanDurationMs: Long = 0L,
    val notes: List<String> = emptyList(),
) {
    fun hasUsableData(): Boolean =
        status == DataStoreStatus.READY && fileCount > 0

    /** Operator-safe lines for Compose / diagnostics lists. */
    fun displayLines(): List<String> = buildList {
        add(
            when (status) {
                DataStoreStatus.READY -> "Vehicle data store: ready"
                DataStoreStatus.EMPTY -> "Vehicle data store: empty"
                DataStoreStatus.NOT_FOUND -> "Vehicle data store: not found"
                DataStoreStatus.ERROR -> "Vehicle data store: scan error"
            },
        )
        if (fileCount > 0) {
            add("${fileCount} files · ${formatBytes(totalBytes)}")
        }
        if (reportFiles.fileCount > 0) {
            add("Reports: ${reportFiles.fileCount} (${formatBytes(reportFiles.totalBytes)})")
        }
        if (catalogFiles.fileCount > 0) {
            add("Catalog / menu assets: ${catalogFiles.fileCount} (${formatBytes(catalogFiles.totalBytes)})")
        }
        if (databaseFiles.fileCount > 0) {
            add("Databases: ${databaseFiles.fileCount} (${formatBytes(databaseFiles.totalBytes)})")
        }
        latestModifiedEpochMs?.let { add("Latest file activity: ${formatEpoch(it)}") }
        if (extensionCounts.isNotEmpty()) {
            val top = extensionCounts.entries
                .sortedByDescending { it.value }
                .take(6)
                .joinToString(", ") { (ext, n) -> ".$ext×$n" }
            add("Extensions: $top")
        }
        sampleNames.take(5).forEach { add("Sample: $it") }
        notes.forEach { add(it) }
    }

    companion object {
        fun notFound(rootsChecked: Int, durationMs: Long = 0L, notes: List<String> = emptyList()) =
            OemDataSummary(
                status = DataStoreStatus.NOT_FOUND,
                rootsChecked = rootsChecked,
                scanDurationMs = durationMs,
                notes = notes.ifEmpty { listOf("Checked $rootsChecked candidate location(s).") },
            )

        fun error(message: String, rootsChecked: Int = 0, durationMs: Long = 0L) =
            OemDataSummary(
                status = DataStoreStatus.ERROR,
                rootsChecked = rootsChecked,
                scanDurationMs = durationMs,
                notes = listOf(message),
            )

        internal fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }

        private fun formatEpoch(epochMs: Long): String {
            val delta = System.currentTimeMillis() - epochMs
            return when {
                delta < 60_000 -> "just now"
                delta < 3_600_000 -> "${delta / 60_000} min ago"
                delta < 86_400_000 -> "${delta / 3_600_000} hr ago"
                else -> "${delta / 86_400_000} days ago"
            }
        }
    }
}
