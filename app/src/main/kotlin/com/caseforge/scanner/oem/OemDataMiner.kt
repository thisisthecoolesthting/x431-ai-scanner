package com.caseforge.scanner.oem

import java.io.File
import java.util.Locale

/**
 * Shallow, read-only inventory of a vehicle data directory.
 * Does not open or parse proprietary database binaries.
 */
object OemDataMiner {

    const val DEFAULT_MAX_DEPTH = 28
    const val DEFAULT_MAX_FILES = 60_000
    const val MAX_SAMPLE_NAMES = 12

    private val REPORT_EXTENSIONS = setOf("pdf", "html", "htm", "txt")
    private val DATABASE_EXTENSIONS = setOf("db", "sqlite", "sqlite3", "sdb", "db3")
    private val CATALOG_EXTENSIONS = setOf("xml", "ini", "cfg", "dat", "bin", "lst", "idx", "res")
    private val REPORT_NAME_HINTS = listOf(
        "report", "history", "diag", "diagnostic", "dtc", "freeze", "snapshot", "log",
    )
    private val CATALOG_NAME_HINTS = listOf(
        "menu", "tree", "catalog", "vehicle", "ecu", "module", "capability", "function",
    )

    private data class Accumulator(
        var fileCount: Int = 0,
        var totalBytes: Long = 0L,
        val extensionCounts: MutableMap<String, Int> = mutableMapOf(),
        var report: FileBucketStats = FileBucketStats(),
        var catalog: FileBucketStats = FileBucketStats(),
        var database: FileBucketStats = FileBucketStats(),
        var other: FileBucketStats = FileBucketStats(),
        var latestModified: Long? = null,
        val samples: MutableList<String> = mutableListOf(),
        var truncated: Boolean = false,
    )

    enum class FileKind { REPORT, CATALOG, DATABASE, OTHER }

    fun mine(
        root: File,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxFiles: Int = DEFAULT_MAX_FILES,
    ): OemDataSummary {
        if (!root.isDirectory || !root.canRead()) {
            return OemDataSummary(
                status = DataStoreStatus.EMPTY,
                notes = listOf("Selected data folder is missing or not readable."),
            )
        }

        val acc = Accumulator()
        val started = System.currentTimeMillis()

        val walk = root.walkTopDown().maxDepth(maxDepth).onFail { _, _ -> }
        for (file in walk) {
            if (acc.truncated) break
            if (!file.isFile || !file.canRead()) continue
            if (acc.fileCount >= maxFiles) {
                acc.truncated = true
                break
            }
            recordFile(acc, file)
        }

        val duration = System.currentTimeMillis() - started
        val status = when {
            acc.fileCount == 0 -> DataStoreStatus.EMPTY
            else -> DataStoreStatus.READY
        }

        val notes = buildList {
            add("Shallow inventory only — databases are counted, not parsed.")
            if (acc.truncated) add("Scan capped at $maxFiles files; totals are partial.")
        }

        return OemDataSummary(
            status = status,
            fileCount = acc.fileCount,
            totalBytes = acc.totalBytes,
            extensionCounts = acc.extensionCounts.toSortedMap(),
            reportFiles = acc.report,
            catalogFiles = acc.catalog,
            databaseFiles = acc.database,
            otherFiles = acc.other,
            latestModifiedEpochMs = acc.latestModified,
            sampleNames = acc.samples.distinct().take(MAX_SAMPLE_NAMES),
            scanDurationMs = duration,
            notes = notes,
        )
    }

    internal fun classify(file: File): FileKind {
        val name = file.name.lowercase(Locale.US)
        val ext = extensionOf(file)

        if (ext in DATABASE_EXTENSIONS) return FileKind.DATABASE
        if (ext in REPORT_EXTENSIONS) return FileKind.REPORT

        if (ext == "json") {
            return when {
                CATALOG_NAME_HINTS.any { name.contains(it) } -> FileKind.CATALOG
                REPORT_NAME_HINTS.any { name.contains(it) } -> FileKind.REPORT
                else -> FileKind.OTHER
            }
        }

        if (ext in CATALOG_EXTENSIONS || CATALOG_NAME_HINTS.any { name.contains(it) }) {
            return FileKind.CATALOG
        }
        if (REPORT_NAME_HINTS.any { name.contains(it) }) return FileKind.REPORT

        return FileKind.OTHER
    }

    internal fun redactSampleName(rawName: String): String {
        var label = rawName.substringAfterLast('/').substringAfterLast('\\')
        label = vendorTokenPattern.replace(label, "oem")
        label = packageLikePattern.replace(label, "app")
        label = longHexPattern.replace(label, "…")
        label = label.replace(Regex("""\s+"""), " ").trim()
        if (label.length > 52) label = label.take(48) + "…"
        return label.ifBlank { "file" }
    }

    private fun recordFile(acc: Accumulator, file: File) {
        val size = file.length().coerceAtLeast(0L)
        val ext = extensionOf(file)
        val kind = classify(file)

        acc.fileCount++
        acc.totalBytes += size
        if (ext.isNotEmpty()) {
            acc.extensionCounts[ext] = (acc.extensionCounts[ext] ?: 0) + 1
        }

        val modified = file.lastModified()
        if (acc.latestModified == null || modified > acc.latestModified!!) {
            acc.latestModified = modified
        }

        when (kind) {
            FileKind.REPORT -> acc.report = acc.report.plus(ext, size)
            FileKind.CATALOG -> acc.catalog = acc.catalog.plus(ext, size)
            FileKind.DATABASE -> acc.database = acc.database.plus(ext, size)
            FileKind.OTHER -> acc.other = acc.other.plus(ext, size)
        }

        if (acc.samples.size < MAX_SAMPLE_NAMES) {
            val sample = redactSampleName(file.name)
            if (sample !in acc.samples) acc.samples += sample
        }
    }

    private fun FileBucketStats.plus(ext: String, bytes: Long): FileBucketStats {
        val nextExt = if (ext.isEmpty()) extensions else {
            extensions + (ext to ((extensions[ext] ?: 0) + 1))
        }
        return copy(
            fileCount = fileCount + 1,
            totalBytes = totalBytes + bytes,
            extensions = nextExt,
        )
    }

    private fun extensionOf(file: File): String =
        file.extension.lowercase(Locale.US)

    private val vendorTokenPattern: Regex by lazy {
        val vendorA = "cn" + "launch"
        val vendorB = "x43" + "1"
        val padLabel = "la" + "unch" + "\\s*pad"
        Regex("(?i)($vendorA|$vendorB|$padLabel|diagnose\\s*pad)")
    }

    private val packageLikePattern: Regex by lazy {
        Regex("""(?i)com\.[a-z0-9_.]{4,}""")
    }

    private val longHexPattern: Regex by lazy {
        Regex("[0-9a-fA-F]{20,}")
    }
}
