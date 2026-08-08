package com.caseforge.scanner.planb

import android.content.Context

/**
 * Legacy entry point for wedge JSON — redirects to bundled [MarqueWedgeConfig] / `marque-wedge-matrix.json`.
 */
@Deprecated(
    message = "Use MarqueWedgeConfig",
    replaceWith = ReplaceWith("MarqueWedgeConfig", "com.caseforge.scanner.planb.MarqueWedgeConfig"),
)
object JeepWedgeConfig {

    val ASSET_NAME: String get() = MarqueWedgeConfig.ASSET_NAME

    fun isJeepVin(vin: String): Boolean = MarqueWedgeConfig.isJeepVin(vin)

    fun load(context: Context): MarqueWedgeMatrix? = MarqueWedgeConfig.load(context)
}
