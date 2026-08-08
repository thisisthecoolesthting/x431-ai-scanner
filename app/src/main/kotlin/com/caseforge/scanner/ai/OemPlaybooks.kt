package com.caseforge.scanner.ai

/**
 * OEM-specific diagnostic prompt packs keyed by VIN WMI (World Manufacturer Identifier).
 * These augment the base diagnostic goal with make-specific knowledge about DTCs,
 * bidirectional tests, security, and service procedures.
 */
object OemPlaybooks {

    /**
     * Returns a make-specific prompt block based on VIN, or null if no match.
     * Examines the first 3 characters (WMI) of the VIN.
     */
    fun forVin(vin: String?): String? {
        if (vin.isNullOrBlank() || vin.length < 3) return null

        val wmi = vin.substring(0, 3).uppercase()

        return when {
            // Toyota (JT, 4T, 5T, JTD), Lexus (JTH, JTJ)
            wmi in setOf("JT", "4T", "5T") || wmi in setOf("JTD", "JTH", "JTJ") -> TOYOTA_LEXUS

            // GM/Chevy/GMC/Cadillac/Buick (1G, 2G, 3G, KL)
            wmi in setOf("1G", "2G", "3G", "KL") -> GENERAL_MOTORS

            // Stellantis: Dodge/Chrysler/Jeep/Ram (1C, 2C, 3C, 1B, 2B, 3B)
            wmi in setOf("1C", "2C", "3C", "1B", "2B", "3B") -> STELLANTIS

            // Ford/Lincoln (1F, 2F, 3F, 1L)
            wmi in setOf("1F", "2F", "3F", "1L") -> FORD_LINCOLN

            // Honda/Acura (1H, 2H, 3H, 19, JH)
            wmi in setOf("1H", "2H", "3H", "19", "JH") -> HONDA_ACURA

            // Nissan/Infiniti (1N, 2N, 3N, JN)
            wmi in setOf("1N", "2N", "3N", "JN") -> NISSAN_INFINITI

            // Hyundai/Kia/Genesis (5N, KM, KN, KMH)
            wmi in setOf("5N", "KM", "KN") || wmi.startsWith("KMH") -> HYUNDAI_KIA_GENESIS

            // VW/Audi/Porsche (1V, 3V, WV, WA, WP)
            wmi in setOf("1V", "3V", "WV", "WA", "WP") -> VW_AUDI_PORSCHE

            // BMW/Mini (WBA, WBS, WMW)
            wmi in setOf("WBA", "WBS", "WMW") -> BMW_MINI

            // Mercedes-Benz (WDB, WDC, WDD)
            wmi in setOf("WDB", "WDC", "WDD") -> MERCEDES_BENZ

            else -> null
        }
    }

    private val TOYOTA_LEXUS = """
        **Toyota/Lexus-Specific Guidance:**
        P0420 (Cat Efficiency) + Monitor Incomplete is typically a drive cycle reset needed, not actual catalyst failure—drive 50+ miles mixed city/highway before condemning the unit. P0171/P0174 (Fuel Trims): Check for vacuum leaks first; these engines are sensitive to small air leaks. VVT-related DTCs (P0014, P0016) are common on older models; confirm oil condition and change interval compliance. Bidirectional tests: Enable/disable VVT solenoids, test secondary O2 heaters, and perform fuel injector balance tests for flow diagnosis. Immobilizer: Most Toyota/Lexus vehicles require security access via OBD (no PIN code per se, but some hybrids lock after multiple invalid key attempts—wait 20+ minutes before retry). Oil reset: Navigate Menu > Maintenance > Oil Reset, or use TSB-specific paths for hybrid models. Throttle relearn on electronically controlled throttles requires engine off, ignition to ACC, wait 10+ seconds, then start.
    """.trimIndent()

    private val GENERAL_MOTORS = """
        **General Motors (Chevy/GMC/Cadillac/Buick) Guidance:**
        P0455 (EVAP Leak) is often a loose fuel cap—verify cap integrity before condemning the charcoal canister. P0496 (EVAP Purge): Check EVAP purge solenoid duty cycle; GM vehicles frequently see ghost faults from intermittent solenoid contacts. P0011/P0014 (Camshaft Timing): Verify VVT oil control solenoid connector integrity and perform relearn if recently replaced. Bidirectional tests: Perform fuel pump pulse-width modulation tests, EVAP purge solenoid cycling, and transmission converter clutch apply/release cycling. Security: OnStar or PassLock immobilizer may require GM-specific unlock protocol after battery disconnect; some 2000–2010 models auto-relearn, others lock indefinitely. Service resets: Oil life reset via Driver Information Center (DIC) using steering wheel buttons, or full reset via Techline if DIC unavailable. EGR valve relearn: Some models require stationary relearn after EGR replacement; others adapt during normal driving.
    """.trimIndent()

    private val STELLANTIS = """
        **Stellantis (Dodge/Chrysler/Jeep/Ram) Guidance:**
        P0700 (TCM Fault): Check transmission fluid condition and level—low fluid causes intermittent TCM faults. P0420 (Cat Efficiency) on Rams/Jeeps may indicate clogged CAT from extended idling; verify exhaust backpressure. P0128 (Coolant Thermostat): Chrysler/Jeep thermal management is sensitive; confirm coolant type (OAT vs. IAT) and proper fill level—air pockets cause this fault. Bidirectional tests: Enable/disable Evaporative Vent solenoid, test transmission line pressure solenoids, and perform fuel pump volume tests. Security: CAN-based immobilizer on newer models; older DaimlerChrysler vehicles use SKIM (Sentry Key Immobilizer Module)—requires special programming after key loss. Service resets: Oil reset via Uconnect menu (if equipped) or instrument cluster button sequence; throttle relearn requires 2–3 min at idle after replacement, no external relearn needed on electronically controlled models.
    """.trimIndent()

    private val FORD_LINCOLN = """
        **Ford/Lincoln Guidance:**
        P0171/P0174 (Fuel Trims): Ford's MAF sensor is notoriously sensitive to carbon buildup—clean MAF and re-run KOEO/KOER before condemning fuel system. P0420 (Cat): Ford trucks/SUVs often see incomplete monitors after battery drain; drive 50+ highway miles to complete. P0014 (Exhaust Camshaft): Variable Cam Timing solenoid issues are common; perform solenoid balance test and confirm oil change interval compliance. Bidirectional tests: Ford gems include fuel pump output cycling, VCT solenoid duty cycle sweep, EGR valve pintle position test, and transmission torque converter clutch engagement. Security: SecuriLock immobilizer requires module-level reprogramming after key fob battery replacement (not just fob programming). Oil reset: Sync menu (or Cluster buttons on older models) > Service > Oil Change Reset, then confirm reset with vehicle start. Throttle relearn: ProteGe control module monitors; most require 5–10 minutes idle, some require off-road diagnostics via PerTronix.
    """.trimIndent()

    private val HONDA_ACURA = """
        **Honda/Acura Guidance:**
        P0505 (Idle Control): Honda's IACV and idle logic are complex; check for intake carbon and perform fuel injector cleaning before condemning ECM. P1456 (EVAP Vent): Common on older Hondas—usually a stuck vent solenoid or canister valve; test solenoid click and vacuum action. P0420 (Cat): Verify O2 sensor heater voltage (typically 12V for 1 sec at startup); low heater voltage prevents proper O2 function. Bidirectional tests: Variable Timing Actuator solenoid pulse-width modulation, EVAP purge solenoid duty cycle, fuel injector balance (cylinder-to-cylinder flow variance), and transmisison lock-up clutch apply/release. Security: Honda Immobilizer Code (HIC) is vehicle-specific; immobilizer may lock after 10 failed key attempts—requires 10-minute cool-down before retry. Service resets: Maintenance light reset via Dashboard menu or Info button sequence (varies by model year and cluster design). Throttle adaptation: Electronic throttle bodies auto-adapt during 5+ minutes idle, no external relearn required on most models.
    """.trimIndent()

    private val NISSAN_INFINITI = """
        **Nissan/Infiniti Guidance:**
        P0011/P0014 (VVT Timing): Check for sludge in oil around VVT actuator solenoid; change oil if overdue. VVT solenoid continuity is also frequent—test solenoid resistance (~3–5 ohms typical) before replacement. P1220 (Fuel Pump Relay): Nissan fuel pump circuits are prone to relay chatter on older models; confirm relay amperage draw and contact cleanliness. P0420 (Cat) with multiple O2 sensor codes suggests oxygen sensor fouling; confirm correct sensor type and heater voltage. Bidirectional tests: VVT actuator on/off cycling, fuel pump pulse-width control, transmission torque-converter lock/unlock, and exhaust gas recirculation solenoid duty cycle. Security: Nissan BCM immobilizer uses RF key ID matching; full BCM reprogram (not just key programming) required after BCM replacement or multiple key loss. Service resets: Maintenance reminder via Cluster menu or scroll buttons; some CVT models auto-adapt after drive cycle (100 miles mixed). Throttle relearn: Electronic units adapt automatically over 5–10 minutes of normal driving post-replacement.
    """.trimIndent()

    private val HYUNDAI_KIA_GENESIS = """
        **Hyundai/Kia/Genesis Guidance:**
        P0128 (Coolant Thermostat): Hyundai engines run cold-start issues on short trips; verify coolant fill level and bleed air pockets completely. P0171/P0174 (Fuel Trims): Common on direct-injection engines—check fuel injector spray pattern and carbon buildup, then perform MAF relearn. P1104 (IAC Position): Idle control is tune-sensitive; check for vacuum leaks and perform IAC position relearn if replaced. Bidirectional tests: VVT solenoid pulse-width modulation, fuel injector balance by cylinder, transmission transmission clutch engagement cycling, and EGR valve pintle position sweep. Security: Immobilizer requires PIN entry (typically 00000 factory default or customer PIN from documentation) before programming new keys; Genesis models use more robust rolling-code systems. Service resets: Maintenance light reset via Cluster menu (MID) navigation buttons; some models require engine-off reset sequence. Throttle relearn: Electronic throttle bodies auto-adapt over 5 minutes idle after replacement; some ATF models require separate transmission relearn drive cycle (50+ miles mixed).
    """.trimIndent()

    private val VW_AUDI_PORSCHE = """
        **VW/Audi/Porsche Guidance:**
        P0011/P0014 (Camshaft Timing): VW/Audi variable valve timing solenoids are prone to varnish buildup—soak and clean solenoid plunger before replacement; oil change intervals are critical (5K-10K max). P0128 (Thermostat): Audi's thermal strategy is aggressive for emissions; confirm stat opening temp and thermostat housing gasket integrity. P0420 (Cat Efficiency): Rear O2 sensor heater failure is common on 2006–2012 models; test heater circuit voltage at startup. Bidirectional tests: Variable Timing Actuator solenoid duty cycle sweep, EVAP canister purge control, transmission mechatronic shift solenoid testing, and EGR cooler backpressure validation. Security: Immobilizer is CAN-based and tied to steering lock; requires VAG-Diag (or clone) for security access—DO NOT attempt key programming without proper security key module sync. Service resets: Long-life service interval reset via Cluster menu (hold down scroll/reset button), or use diagnostic tool for forced resets. Throttle adaptation: DBW modules require throttle position 0% and 100% calibration after replacement (use Cluster menu or VAG diagnostics).
    """.trimIndent()

    private val BMW_MINI = """
        **BMW/Mini Guidance:**
        P0011/P0014 (VVT Solenoid): BMW VANOS solenoids are electro-hydraulic and varnish-prone—clean solenoid plunger and verify oil condition before condemning unit. P0128 (Thermostat): BMW's stratified cooling strategy (dual-stage stat) is common; confirm correct stat part number and housing fit. P0420 (Cat Efficiency) with rear O2 sensor DTCs: Test heater circuit (typically 12V at startup); BMW rear sensors fail frequently on aging vehicles. Bidirectional tests: VANOS solenoid duty cycle optimization, fuel injector balance across cylinders, transmission mechatronic control module solenoid tests, and EGR valve position feedback. Security: BMW EWS (Electronic Watchdog System) immobilizer is RF-based and extremely strict—requires BMW-level security access and MotorScan or INPA tool for security unlock after key loss or module replacement. Service resets: Service indicator reset via Cluster menu (select > Confirm > left scroll), or forced reset via diagnostic tool; some iDrive systems require menu navigation. Throttle relearn: DDE engine control modules auto-adapt electronic throttle over 5–10 minutes; no external relearn procedure required on modern models.
    """.trimIndent()

    private val MERCEDES_BENZ = """
        **Mercedes-Benz Guidance:**
        P0011/P0014 (Camshaft Timing): Mercedes VarioCam solenoids are electronically sophisticated—verify continuity, resistance (~3–5 ohms), and oil quality; solenoid adaptation data may require reset via Xentry. P0128 (Thermostat): Mercedes multi-stage stat logic for emissions compliance is aggressive; confirm stat opening temp and housing gasket. P0420 (Cat Efficiency) + rear O2 fault: Rear oxygen sensor heater circuit failures are endemic on 2005–2015 models; test 12V heater output at cold start. Bidirectional tests: VarioCam solenoid duty cycle sweep, fuel injector balance by cylinder, transmission torque-converter lock-up cycling, and EGR cooler bypass solenoid control. Security: Mercedes IMMO (Immobilizer) is CAN-integrated and highly restricted—requires Xentry diagnostic tool with vehicle-specific security key; dealership programming required for lost keys or module replacement. Service resets: Service interval reset via Cluster menu (odometer > Confirm > Service) or ASSYST menu (newer models); forced resets require Xentry authentication. Throttle adaptation: Electronic throttle auto-adapts over 5–10 minutes of idle after replacement; transmission mechatronic modules may require separate adaptation drive (50+ miles highway + 5 minutes idle).
    """.trimIndent()
}
