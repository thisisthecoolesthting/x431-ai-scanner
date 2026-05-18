package com.caseforge.scanner.engine

object SequenceDefinitions {
    val ALL: List<DiagnosticSequence> = listOf(
        DiagnosticSequence(
            sequenceId = "seq_relative_compression",
            name = "Relative Compression Test",
            description = "Cranking-current and RPM comparison to flag weak cylinders before teardown.",
            totalDurationMinutes = 8,
            steps = listOf(
                SequenceStep("Set up cranking capture", "Disable fuel or ignition as appropriate, connect current clamp, and confirm battery is charged.", SequenceAction.Prompt("setup_complete"), 90),
                SequenceStep("Open live data", "Navigate to live data for engine RPM and battery voltage.", SequenceAction.RunCapability("live_data"), 30),
                SequenceStep("Capture crank event", "Crank for five seconds and watch RPM/current waveform consistency.", SequenceAction.ReadLiveData(listOf("RPM", "Battery Voltage")), 15),
                SequenceStep("Record weak cylinder pattern", "Mark any repeating low-current or high-speed compression event.", SequenceAction.Prompt("relative_compression_notes"), 60),
            ),
        ),
        DiagnosticSequence(
            sequenceId = "seq_evap_smoke",
            name = "EVAP Smoke Test",
            description = "Commands the EVAP vent closed and guides a smoke inspection for leak isolation.",
            totalDurationMinutes = 12,
            steps = listOf(
                SequenceStep("Prepare smoke machine", "Connect smoke at the service port or purge line and set low pressure.", SequenceAction.Prompt("smoke_machine_ready"), 120),
                SequenceStep("Enter actuation test", "Open bidirectional controls for EVAP components.", SequenceAction.RunCapability("actuation"), 45),
                SequenceStep("Close vent valve", "Command the EVAP vent closed for sealed-system smoke testing.", SequenceAction.Actuate("evap_vent_close"), 30),
                SequenceStep("Inspect for smoke", "Inspect cap, canister, purge plumbing, tank seam, and vent filter for visible smoke.", SequenceAction.Wait(180), 180),
                SequenceStep("Record leak location", "Save observed leak location or no-leak result.", SequenceAction.Prompt("evap_leak_location"), 60),
            ),
        ),
        DiagnosticSequence(
            sequenceId = "seq_injector_kill",
            name = "Injector Kill Test",
            description = "Cycles injector shutoff commands and records RPM contribution changes.",
            totalDurationMinutes = 10,
            steps = listOf(
                SequenceStep("Warm engine at idle", "Run engine at operating temperature with all accessories off.", SequenceAction.Prompt("engine_warm_idle"), 60),
                SequenceStep("Open live RPM data", "Display RPM, misfire counters, and fuel trim where available.", SequenceAction.RunCapability("live_data"), 30),
                SequenceStep("Open injector actuation", "Navigate to injector cutout controls.", SequenceAction.RunCapability("actuation"), 45),
                SequenceStep("Cycle injector cutouts", "Disable each injector briefly and record RPM drop consistency.", SequenceAction.Actuate("injector_kill"), 180),
                SequenceStep("Record cylinder balance", "Flag cylinders with low or no RPM contribution.", SequenceAction.Prompt("injector_balance_results"), 90),
            ),
        ),
        DiagnosticSequence(
            sequenceId = "seq_vvt_sweep",
            name = "VVT Solenoid Sweep",
            description = "Sweeps cam actuator command while comparing desired and actual cam angle response.",
            totalDurationMinutes = 9,
            steps = listOf(
                SequenceStep("Open cam live data", "Display desired cam angle, actual cam angle, engine RPM, and oil temperature if available.", SequenceAction.RunCapability("live_data"), 45),
                SequenceStep("Stabilize idle", "Hold a stable idle or commanded test RPM before sweeping the solenoid.", SequenceAction.Wait(30), 30),
                SequenceStep("Sweep VVT command", "Command the VVT solenoid through low, medium, and high duty cycle.", SequenceAction.Actuate("vvt_solenoid_sweep"), 120),
                SequenceStep("Capture cam response", "Record lag, overshoot, no-response, or stuck-position behavior.", SequenceAction.ReadLiveData(listOf("Desired Cam Angle", "Actual Cam Angle", "Engine RPM")), 30),
                SequenceStep("Record VVT result", "Save final pass/fail notes and any oil pressure or sludge concerns.", SequenceAction.Prompt("vvt_sweep_result"), 60),
            ),
        ),
        DiagnosticSequence(
            sequenceId = "seq_parasitic_draw_bisection",
            name = "Parasitic Draw Bisection",
            description = "Guides a fuse-panel bisection workflow to isolate excessive key-off draw.",
            totalDurationMinutes = 25,
            steps = listOf(
                SequenceStep("Prepare sleep test", "Install ammeter or current clamp, latch doors, and let modules time out.", SequenceAction.Wait(600), 600),
                SequenceStep("Record baseline draw", "Record stabilized key-off current before pulling any fuses.", SequenceAction.Prompt("baseline_draw_ma"), 60),
                SequenceStep("Bisect first fuse group", "Pull half the suspect fuse group and watch for a draw drop.", SequenceAction.Prompt("first_bisection_result"), 180),
                SequenceStep("Bisect remaining group", "Continue halving the active group until one circuit remains.", SequenceAction.Prompt("remaining_bisection_result"), 240),
                SequenceStep("Record suspect circuit", "Save fuse number, circuit name, current drop, and next inspection target.", SequenceAction.Prompt("parasitic_draw_suspect"), 120),
            ),
        ),
    )
}
