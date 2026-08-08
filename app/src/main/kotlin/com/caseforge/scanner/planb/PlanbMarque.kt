package com.caseforge.scanner.planb

import com.caseforge.scanner.vin.ChryslerVinDetector
import com.caseforge.scanner.vin.DodgeVinDetector
import com.caseforge.scanner.vin.FordVinDetector
import com.caseforge.scanner.vin.GmVinDetector
import com.caseforge.scanner.vin.HondaVinDetector
import com.caseforge.scanner.vin.HyundaiVinDetector
import com.caseforge.scanner.vin.JeepVinDetector
import com.caseforge.scanner.vin.NissanVinDetector
import com.caseforge.scanner.vin.RamVinDetector
import com.caseforge.scanner.vin.ToyotaVinDetector

/**
 * Stellantis-family Plan B marques with bundled planb JSON assets.
 */
enum class PlanbMarque(val id: String) {
    JEEP("jeep"),
    FORD("ford"),
    DODGE("dodge"),
    RAM("ram"),
    CHRYSLER("chrysler"),
    CHEVROLET("chevrolet"),
    TOYOTA("toyota"),
    HONDA("honda"),
    NISSAN("nissan"),
    HYUNDAI("hyundai"),
    ;

    /** Ford + Stellantis marques included in the Tier 4 trial (reference checklists only). */
    val isTier4TrialMarque: Boolean
        get() = this in TRIAL_MARQUES

    companion object {
        val TRIAL_MARQUES: Set<PlanbMarque> = setOf(FORD, JEEP, DODGE, RAM, CHRYSLER)

        fun fromId(raw: String?): PlanbMarque? {
            val key = raw?.trim()?.lowercase().orEmpty()
            if (key.isEmpty()) return null
            return entries.firstOrNull { it.id == key }
        }

        /** WMI order matches [com.caseforge.scanner.vin.VinNormalizer.marqueHint]. */
        fun fromVin(vin: String?): PlanbMarque? {
            val v = vin?.trim().orEmpty()
            if (v.length < 3) return null
            return when {
                JeepVinDetector.isLikelyJeepVin(v) -> JEEP
                FordVinDetector.isLikelyFordVin(v) -> FORD
                RamVinDetector.isLikelyRamVin(v) -> RAM
                ChryslerVinDetector.isLikelyChryslerVin(v) -> CHRYSLER
                DodgeVinDetector.isLikelyDodgeVin(v) -> DODGE
                GmVinDetector.isLikelyGmVin(v) -> CHEVROLET
                ToyotaVinDetector.isLikelyToyotaVin(v) -> TOYOTA
                HondaVinDetector.isLikelyHondaVin(v) -> HONDA
                NissanVinDetector.isLikelyNissanVin(v) -> NISSAN
                HyundaiVinDetector.isLikelyHyundaiVin(v) -> HYUNDAI
                else -> null
            }
        }
    }
}

/** Short UI label (settings / Plan B screens). */
fun PlanbMarque.displayName(): String = when (this) {
    PlanbMarque.JEEP -> "Jeep"
    PlanbMarque.FORD -> "Ford"
    PlanbMarque.DODGE -> "Dodge"
    PlanbMarque.RAM -> "Ram"
    PlanbMarque.CHRYSLER -> "Chrysler"
    PlanbMarque.CHEVROLET -> "Chevrolet / GM"
    PlanbMarque.TOYOTA -> "Toyota / Lexus"
    PlanbMarque.HONDA -> "Honda / Acura"
    PlanbMarque.NISSAN -> "Nissan / Infiniti"
    PlanbMarque.HYUNDAI -> "Hyundai / Kia"
}
