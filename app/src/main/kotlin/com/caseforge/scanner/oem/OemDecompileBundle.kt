package com.caseforge.scanner.oem

import kotlinx.serialization.Serializable

@Serializable
data class DecompileCatalogTotals(
    val ggp: Int = 0,
    val bin: Int = 0,
    val bnc: Int = 0,
)

@Serializable
data class OemDecompileBundleSummary(
    val brandCount: Int = 0,
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
    val catalogTotals: DecompileCatalogTotals = DecompileCatalogTotals(),
    val vehicleCatalogId: String? = null,
    val sqliteSchemaId: String? = null,
    val funcIniId: String? = null,
)

@Serializable
data class DecompileSqliteTableSummary(
    val name: String = "",
    val columnCount: Int = 0,
)

@Serializable
data class DecompileSqliteDatabaseSummary(
    val name: String = "",
    val tableCount: Int = 0,
    val tables: List<DecompileSqliteTableSummary> = emptyList(),
)

@Serializable
data class DecompileBrandEntry(
    val brand: String = "",
    val versionDirs: List<String> = emptyList(),
    val onLine: String? = null,
    val catalogCounts: DecompileCatalogTotals = DecompileCatalogTotals(),
    val fileCount: Int = 0,
    val totalBytes: Long = 0L,
)

@Serializable
data class OemDecompileBundle(
    val bundleVersion: Int = 1,
    val generatedAt: String = "",
    val summary: OemDecompileBundleSummary = OemDecompileBundleSummary(),
    val formatHints: Map<String, String> = emptyMap(),
    val sqliteDatabases: List<DecompileSqliteDatabaseSummary> = emptyList(),
    val brands: List<DecompileBrandEntry> = emptyList(),
)
