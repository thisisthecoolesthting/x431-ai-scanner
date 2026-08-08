package com.caseforge.scanner.agent

import android.content.pm.PackageManager
import android.os.Build

/**
 * Visibility-safe checks for OEM diagnostic APKs (matches [ScannerAccessibilityService.OEM_DIAG_PACKAGES]).
 * Uses per-package visibility only — no QUERY_ALL_PACKAGES.
 */
object X431InstalledProbe {

    fun installedFlags(pm: PackageManager): Map<String, Boolean> =
        ScannerAccessibilityService.OEM_DIAG_PACKAGES.associateWith { isPackageInstalled(pm, it) }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
}
