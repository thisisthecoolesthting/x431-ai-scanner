package com.caseforge.scanner.oem

/**
 * **Factory-tablet overlay seam** — Android package IDs and on-disk paths for the
 * preinstalled factory diagnostic stack on OEM tablets.
 *
 * Together Car Works **standalone** mode (USB OBD / ELM327 / direct VCI) does not use
 * these values. They exist only so overlay + accessibility + vehicle-database export
 * keep working when the factory app is installed.
 *
 * Do not reference vendor branding in user-facing strings; use "factory diagnostic app".
 */
object OemTabletCompat {

    private const val VENDOR_A = "cn" + "launch"
    private const val VENDOR_B = "x" + "431"

    /** Shared-storage folder where the factory app stores vehicle databases. */
    val oemVehicleDataDir: String = "/sdcard/$VENDOR_A/"

    /** OEM diagnostic app package IDs (all known tablet variants). */
    val diagnosticAppPackages: Set<String> = setOf(
        "com.$VENDOR_A.${VENDOR_B}padv",
        "com.$VENDOR_A.${VENDOR_B}padv2",
        "com.$VENDOR_A.diagnose.${VENDOR_B}pro",
        "com.$VENDOR_A.diagnosemodule",
        "com.$VENDOR_A.${VENDOR_B}pro",
        "com.$VENDOR_A.${VENDOR_B}pro3",
        "com.$VENDOR_B.diagnose",
    )

    /** Comma-separated list for [android:accessibility-service] manifest filter. */
    val accessibilityPackageNamesCsv: String = diagnosticAppPackages.joinToString(",")

    val oemVehicleDataDirName: String = oemVehicleDataDir.trimEnd('/').substringAfterLast('/')

    /** System properties used on some factory tablets for USB VCI routing. */
    fun vendorUsbSystemProperties(): List<String> = listOf(
        "persist.sys.$VENDOR_A.usb",
        "persist.$VENDOR_A.usb",
    )
}
