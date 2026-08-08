package com.caseforge.scanner.planb

import com.caseforge.scanner.engine.CapabilityEntry

/**
 * Display-only helpers: filter bundled [capabilities.json] rows by marque wedge card + SKREEM routing hints.
 */
object CapabilityWedgeFilter {

    /** True for SKIM/SKREEM immobilizer programming rows (links to Immo + Programming screens). */
    fun isSkreemImmobilizerRow(entry: CapabilityEntry): Boolean {
        val id = entry.id.lowercase()
        val label = entry.label.lowercase()
        if ("skreem" in id || "skreem" in label) return true
        if (id.contains("skim") && (id.contains("key") || id.contains("immobil"))) return true
        if (label.contains("skim") && label.contains("immobilizer")) return true
        return false
    }

    /**
     * Capability matches the current wedge when [oem_scope] intersects the marque token set
     * derived from [MarquePlatformCard.marque]. Empty [CapabilityEntry.oemScope] = global (always show).
     */
    fun matchesWedge(card: MarquePlatformCard?, entry: CapabilityEntry): Boolean {
        if (entry.oemScope.isEmpty()) return true
        if (card == null) return false
        val allowed = expandedScopeTokensForMarque(card.marque)
        return entry.oemScope.any { scope -> allowed.any { it.equals(scope.trim(), ignoreCase = true) } }
    }

    private fun expandedScopeTokensForMarque(marque: String): Set<String> {
        val m = marque.trim().lowercase()
        val out = mutableSetOf(m)
        when (m) {
            "ford" -> {
                out += "lincoln"
                out += "mercury"
            }
            "dodge" -> {
                out += "ram"
                out += "chrysler"
                out += "stellantis"
                out += "fiat"
                out += "alfa_romeo"
            }
            "ram", "chrysler" -> {
                out += "dodge"
                out += "jeep"
                out += "stellantis"
                out += "fiat"
                out += "alfa_romeo"
            }
            "jeep" -> {
                out += "stellantis"
                out += "chrysler"
                out += "ram"
                out += "dodge"
            }
            "chevrolet" -> {
                out += "gm"
                out += "gmc"
                out += "buick"
                out += "cadillac"
            }
            "toyota" -> {
                out += "lexus"
            }
            "honda" -> {
                out += "acura"
            }
            "nissan" -> {
                out += "infiniti"
            }
            "hyundai" -> {
                out += "kia"
            }
        }
        return out
    }
}
