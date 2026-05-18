package com.caseforge.scanner.engine.sequences

/**
 * Factory for common automotive diagnostic sequences.
 * These sequences are battle-tested patterns used in professional scan tools.
 */
object CommonSequences {

    /**
     * RELATIVE COMPRESSION TEST
     * Disable fuel injectors one cylinder at a time and measure RPM delta.
     * Healthy cylinders show 50-150 RPM drop; weak/failed cylinders show <50 RPM.
     */
    fun relativeCompression(): TestSequence {
        val cylinders = listOf(1, 2, 3, 4, 5, 6)
        val steps = mutableListOf<Step>()

        steps.add(
            Prompt(
                label = "Prep: Relative Compression",
                message = "Ensure engine is running at idle (800–1000 RPM). Do NOT open throttle.",
                imageUrl = null
            )
        )

        steps.add(
            CapturePid(
                label = "Baseline RPM",
                pid = "0x010C",  // Engine RPM
                storageKey = "baseline_rpm"
            )
        )

        for (cyl in cylinders) {
            steps.add(
                RunCapability(
                    label = "Disable Cyl $cyl Injector",
                    capabilityId = "injector_cutout",
                    params = mapOf("cylinder" to cyl.toString())
                )
            )

            steps.add(
                Wait(
                    label = "Settle (Cyl $cyl)",
                    seconds = 2.0
                )
            )

            steps.add(
                CapturePid(
                    label = "RPM with Cyl $cyl Disabled",
                    pid = "0x010C",
                    storageKey = "rpm_delta_$cyl"
                )
            )

            steps.add(
                RunCapability(
                    label = "Re-enable Cyl $cyl Injector",
                    capabilityId = "injector_restore",
                    params = mapOf("cylinder" to cyl.toString())
                )
            )

            steps.add(
                Wait(
                    label = "Recover (Cyl $cyl)",
                    seconds = 1.5
                )
            )
        }

        steps.add(
            Prompt(
                label = "Analysis: Review deltas in results",
                message = "Verify all cylinders show 50–150 RPM delta. Anomalies indicate compression loss."
            )
        )

        return TestSequence(
            id = "relative_compression",
            label = "Relative Compression Test",
            description = "Injector-cutout sweep to measure compression per cylinder. Detects weak/failed cylinders.",
            steps = steps,
            timeout = 120_000L
        )
    }

    /**
     * EVAP SMOKE TEST
     * Seal EVAP, command vent valve, measure pressure decay to detect leaks.
     */
    fun evapSmokeTest(): TestSequence {
        return TestSequence(
            id = "evap_smoke_test",
            label = "EVAP System Leak Detection",
            description = "Pressurize EVAP canister and measure decay rate.",
            steps = listOf(
                Prompt(
                    label = "Prep: EVAP Smoke Test",
                    message = "Disconnect negative battery terminal. Locate EVAP canister purge line. Ready smoke machine.",
                    imageUrl = null
                ),
                RunCapability(
                    label = "Close Vent Valve",
                    capabilityId = "evap_vent_close",
                    params = emptyMap()
                ),
                Wait(
                    label = "Stabilize",
                    seconds = 1.0
                ),
                CapturePid(
                    label = "Initial EVAP Pressure",
                    pid = "0x0159",  // EVAP vapor pressure (if available)
                    storageKey = "evap_initial"
                ),
                Prompt(
                    label = "Apply Smoke",
                    message = "Apply smoke to EVAP canister at 0.5–1.0 psi. Watch for visible leaks.",
                    imageUrl = null
                ),
                Wait(
                    label = "Smoke Dwell",
                    seconds = 30.0
                ),
                CapturePid(
                    label = "EVAP Pressure After Smoke",
                    pid = "0x0159",
                    storageKey = "evap_after_smoke"
                ),
                Branch(
                    label = "Check Pressure Decay",
                    condition = "evap_after_smoke < 0.2",
                    ifTrue = listOf(
                        Prompt(
                            label = "PASS: No Major Leaks",
                            message = "EVAP system holds pressure. No visible/audible leaks detected."
                        )
                    ),
                    ifFalse = listOf(
                        Prompt(
                            label = "FAIL: Pressure Loss",
                            message = "EVAP pressure decayed rapidly. Check for leaks at purge valve, canister seams, lines."
                        )
                    )
                )
            ),
            timeout = 90_000L
        )
    }

    /**
     * PARASITIC DRAW BISECTION
     * Guide user through fuse-pull bisection to isolate parasitic current draw.
     */
    fun parasiticDrawBisection(): TestSequence {
        val fuseGroups = listOf(
            "Fuse Box 1 (Cabin)",
            "Fuse Box 2 (Engine)",
            "PDM Relay (if equipped)"
        )

        val steps = mutableListOf<Step>()

        steps.add(
            Prompt(
                label = "Prep: Parasitic Draw Test",
                message = "Engine off, all accessories off, doors/windows closed. Connect ammeter between battery negative and terminal."
            )
        )

        steps.add(
            CapturePid(
                label = "Baseline Current Draw",
                pid = "0x0000",  // placeholder; actual implementation uses analog meter input
                storageKey = "baseline_draw"
            )
        )

        for ((idx, group) in fuseGroups.withIndex()) {
            steps.add(
                Prompt(
                    label = "Pull Fuse Group: $group",
                    message = "Remove all fuses from $group. Observe ammeter reading."
                )
            )

            steps.add(
                Wait(label = "Settle", seconds = 5.0)
            )

            steps.add(
                CapturePid(
                    label = "Current with $group Disabled",
                    pid = "0x0000",
                    storageKey = "draw_without_group_$idx"
                )
            )

            steps.add(
                Branch(
                    label = "Did draw drop significantly?",
                    condition = "draw_without_group_$idx < 0.05",  // <50 mA
                    ifTrue = listOf(
                        Prompt(
                            label = "Culprit Found",
                            message = "Parasitic load is in $group. Reinstall fuses one at a time to isolate exact circuit."
                        )
                    ),
                    ifFalse = listOf(
                        Prompt(
                            label = "No change; try next group",
                            message = "Reinstall $group fuses and proceed."
                        )
                    )
                )
            )

            steps.add(
                Prompt(
                    label = "Reinstall Fuses",
                    message = "Reinstall all fuses in $group."
                )
            )
        }

        return TestSequence(
            id = "parasitic_draw_bisect",
            label = "Parasitic Draw Bisection",
            description = "User-guided fuse-pull to isolate parasitic current drain.",
            steps = steps,
            timeout = 600_000L  // 10 min
        )
    }

    /**
     * INJECTOR KILL SWEEP
     * Sequentially disable each injector and log misfire count delta.
     */
    fun injectorKillSweep(): TestSequence {
        val cylinders = listOf(1, 2, 3, 4, 5, 6)
        val steps = mutableListOf<Step>()

        steps.add(
            Prompt(
                label = "Prep: Injector Kill Sweep",
                message = "Engine running at 2000 RPM under light load. Monitor misfire counter.",
                imageUrl = null
            )
        )

        steps.add(
            RunCapability(
                label = "Clear Misfire Counter",
                capabilityId = "clear_dtc",
                params = mapOf("dtc_type" to "misfire")
            )
        )

        for (cyl in cylinders) {
            steps.add(
                RunCapability(
                    label = "Kill Cyl $cyl Injector",
                    capabilityId = "injector_cutout",
                    params = mapOf("cylinder" to cyl.toString())
                )
            )

            steps.add(Wait(label = "Dwell", seconds = 5.0))

            steps.add(
                CapturePid(
                    label = "Misfire Count (Cyl $cyl Disabled)",
                    pid = "0x0147",  // Misfire cylinder
                    storageKey = "misfire_cyl_$cyl"
                )
            )

            steps.add(
                RunCapability(
                    label = "Restore Cyl $cyl Injector",
                    capabilityId = "injector_restore",
                    params = mapOf("cylinder" to cyl.toString())
                )
            )

            steps.add(Wait(label = "Recover", seconds = 2.0))
        }

        steps.add(
            Prompt(
                label = "Review Results",
                message = "Healthy injectors cause misfire jump >20 counts; weak injectors <5 counts."
            )
        )

        return TestSequence(
            id = "injector_kill_sweep",
            label = "Injector Kill Sweep",
            description = "Disable each injector sequentially and measure misfire count delta.",
            steps = steps,
            timeout = 180_000L
        )
    }

    /**
     * VVT SOLENOID SWEEP
     * Command VVT advance/retard and log cam position response.
     * REQUIRES F10: Direct VCI control for real-time solenoid PWM.
     */
    fun vvtSolenoidSweep(): TestSequence {
        return TestSequence(
            id = "vvt_solenoid_sweep",
            label = "VVT Solenoid Sweep",
            description = "Command VVT advance/retard and measure cam position response. [REQUIRES F10: Direct VCI]",
            steps = listOf(
                Prompt(
                    label = "FEATURE FLAG: F10 Required",
                    message = "VVT solenoid control requires F10 (Direct VCI) bidirectional capability. Not yet implemented."
                ),
                Prompt(
                    label = "Placeholder Step",
                    message = "When F10 is available, this sequence will: command OCV PWM 0–100%, measure cam position delay, detect sticking/dead bands."
                )
            ),
            timeout = 60_000L
        )
    }
}
