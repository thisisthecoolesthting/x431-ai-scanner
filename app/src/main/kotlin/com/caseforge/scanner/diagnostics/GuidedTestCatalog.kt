package com.caseforge.scanner.diagnostics

/**
 * Bundled guided-test library for common driveability and electrical complaints.
 */
object GuidedTestCatalog {

    val ALL: List<GuidedTest> by lazy { listOf(
        misfire,
        noStart,
        chargingLowVoltage,
        evapLeak,
        o2Sensor,
        overheating,
    ) }

    fun byId(id: String): GuidedTest? = ALL.firstOrNull { it.id == id }

    val misfire = GuidedTest(
        id = "guided_misfire",
        title = "Misfire / rough idle",
        symptomAliases = listOf(
            "misfire",
            "rough idle",
            "shaking",
            "stumble",
            "miss",
            "P0300",
            "cylinder misfire",
            "hesitation",
        ),
        preconditions = GuidedTestPreconditions(
            ignition = IgnitionRequirement.ENGINE_RUNNING,
            notes = listOf(
                "Allow engine to reach normal operating temperature.",
                "Turn off A/C and unnecessary electrical loads.",
            ),
        ),
        requiredTransport = GuidedTransportRequirement.ANY,
        steps = listOf(
            GuidedTestStep(
                title = "Confirm stored misfire codes",
                instruction = "Read powertrain DTCs and note which cylinders are flagged (P0301–P0312 family).",
                expectedObservation = "Active or pending cylinder-specific misfire codes, or P0300 random/multiple.",
            ),
            GuidedTestStep(
                title = "Review freeze frame",
                instruction = "Open freeze frame for the newest misfire code and record RPM, load, and fuel status.",
                expectedObservation = "Misfire occurs at idle, light load, or under acceleration — pattern narrows fuel vs. ignition vs. mechanical.",
            ),
            GuidedTestStep(
                title = "Idle RPM stability",
                instruction = "Display engine RPM and watch for regular dips or hunting at stable idle.",
                expectedObservation = "Steady RPM within spec, or repeatable drops matching misfire counters.",
            ),
            GuidedTestStep(
                title = "Fuel trim snapshot",
                instruction = "Log short-term and long-term fuel trim at idle (and off-idle if available).",
                expectedObservation = "Trims near zero suggest ignition/mechanical; sustained positive trim suggests vacuum leak or lean fuel delivery.",
            ),
            GuidedTestStep(
                title = "Cylinder contribution check",
                instruction = "If available, run injector cutout or relative compression; otherwise note which cylinder shows weakest RPM drop.",
                expectedObservation = "One cylinder with low contribution points to plug, coil, injector, or compression on that hole.",
            ),
        ),
        relatedPids = listOf("010C", "RPM", "STFT B1", "LTFT B1", "Misfire Cylinder"),
        relatedDtcPrefixes = listOf("P030"),
        reportWording = "Guided misfire workflow completed: codes and freeze frame captured, idle RPM and fuel trims reviewed, cylinder contribution noted for follow-up (coil/plug/injector/compression as indicated).",
    )

    val noStart = GuidedTest(
        id = "guided_no_start",
        title = "No-start / crank-no-start",
        symptomAliases = listOf(
            "no start",
            "won't start",
            "crank no start",
            "cranks but won't start",
            "turns over",
            "no fire",
            "immobilizer",
        ),
        preconditions = GuidedTestPreconditions(
            ignition = IgnitionRequirement.KEY_ON_THEN_ENGINE_RUNNING,
            notes = listOf(
                "Verify park/neutral or clutch safety interlock.",
                "Confirm fuel level and recent repairs (timing belt, fuel pump).",
            ),
        ),
        requiredTransport = GuidedTransportRequirement.ANY,
        steps = listOf(
            GuidedTestStep(
                title = "Battery and crank voltage",
                instruction = "Key on engine off: note battery voltage. During crank, watch voltage sag.",
                expectedObservation = "Resting ≥12.4 V typical; crank sag below ~9.5 V suggests weak battery or cable drop.",
            ),
            GuidedTestStep(
                title = "Crank RPM if available",
                instruction = "During crank, display engine RPM PID if the ECM reports it while cranking.",
                expectedObservation = "RPM rises during crank (ECM awake); flatline may indicate no ECM communication or security lockout.",
            ),
            GuidedTestStep(
                title = "Security / immobilizer",
                instruction = "Check for PATS/immobilizer or security DTCs; confirm correct key/fob and no aftermarket bypass faults.",
                expectedObservation = "No active security codes; security light behavior matches a valid key.",
            ),
            GuidedTestStep(
                title = "Spark vs. fuel divide",
                instruction = "Use safe test light or scan-tool fuel command if supported; otherwise smell for fuel and check injector pulse.",
                expectedObservation = "Missing spark → ignition path; fuel smell without start → spark OK, investigate fuel pressure/volume.",
            ),
            GuidedTestStep(
                title = "Capture first-failure DTCs",
                instruction = "Clear codes only after recording; re-read after one crank attempt if needed.",
                expectedObservation = "Crank sensor, CMP, fuel pump driver, or anti-theft codes narrow the next physical test.",
            ),
        ),
        relatedPids = listOf("Battery Voltage", "Engine RPM", "Fuel System Status"),
        relatedDtcPrefixes = listOf("P033", "P034", "P035", "P060", "P061", "P157", "P168"),
        reportWording = "Guided no-start workflow: battery/crank voltage documented, crank RPM and security status checked, spark/fuel divide recorded with supporting DTCs.",
    )

    val chargingLowVoltage = GuidedTest(
        id = "guided_charging_low_voltage",
        title = "Charging system / low voltage",
        symptomAliases = listOf(
            "battery light",
            "charging",
            "low voltage",
            "dim lights",
            "slow crank",
            "alternator",
            "P0562",
            "P0622",
        ),
        preconditions = GuidedTestPreconditions(
            ignition = IgnitionRequirement.KEY_ON_THEN_ENGINE_RUNNING,
            notes = listOf(
                "Turn off high-draw accessories for baseline readings.",
                "Verify belt condition and tension before condemning alternator.",
            ),
        ),
        requiredTransport = GuidedTransportRequirement.ELM327,
        steps = listOf(
            GuidedTestStep(
                title = "Key-off battery voltage",
                instruction = "Key off, engine off: read battery voltage via OBD or multimeter at posts.",
                expectedObservation = "Typically 12.4–12.7 V on a charged battery; lower suggests discharge or bad cell.",
            ),
            GuidedTestStep(
                title = "Key-on engine-off load",
                instruction = "Key on, engine off: note voltage with headlights or blower on briefly.",
                expectedObservation = "Moderate drop acceptable; severe sag points to weak battery or high resistance in cables.",
            ),
            GuidedTestStep(
                title = "Idle charging voltage",
                instruction = "Engine running at idle: read charging voltage after 30 seconds.",
                expectedObservation = "Roughly 13.5–14.8 V at idle indicates alternator output; stuck near battery voltage suggests undercharge.",
            ),
            GuidedTestStep(
                title = "Raised RPM check",
                instruction = "Hold ~2000 RPM for 10 seconds and re-read voltage.",
                expectedObservation = "Voltage should rise or hold in charging range; no rise with good belt → alternator/regulator fault.",
            ),
            GuidedTestStep(
                title = "Charging DTC review",
                instruction = "Read powertrain and body charging-related codes (P056x, P062x, Bxxxx generator codes).",
                expectedObservation = "Circuit or communication codes support wiring vs. internal alternator diagnosis.",
            ),
        ),
        relatedPids = listOf("Battery Voltage", "0142"),
        relatedDtcPrefixes = listOf("P056", "P062", "P163"),
        reportWording = "Guided charging/low-voltage workflow: resting, key-on, and running voltages logged at idle and raised RPM; charging-related DTCs reviewed.",
    )

    val evapLeak = GuidedTest(
        id = "guided_evap_leak",
        title = "EVAP leak / fuel vapor",
        symptomAliases = listOf(
            "evap",
            "gas smell",
            "fuel smell",
            "P0442",
            "P0455",
            "P0456",
            "small leak",
            "large leak",
            "purge",
        ),
        preconditions = GuidedTestPreconditions(
            ignition = IgnitionRequirement.KEY_ON_ENGINE_OFF,
            notes = listOf(
                "Fuel cap tight and OEM-spec; inspect cap seal before smoke.",
                "Perform in ventilated area away from ignition sources.",
            ),
        ),
        requiredTransport = GuidedTransportRequirement.OEM,
        steps = listOf(
            GuidedTestStep(
                title = "Read EVAP DTCs",
                instruction = "Capture pending/active EVAP codes (P044x, P045x, P049x) and readiness monitor status.",
                expectedObservation = "Small vs. large leak codes and monitor incomplete flags guide smoke vs. cap-first strategy.",
            ),
            GuidedTestStep(
                title = "Cap and filler neck",
                instruction = "Inspect gas cap gasket, filler neck rust, and mis-seated cap before invasive tests.",
                expectedObservation = "Cracked cap or rusty neck is a common fix for small-leak codes.",
            ),
            GuidedTestStep(
                title = "Purge/vent command check",
                instruction = "Using OEM bidirectional controls, command purge open/closed and vent closed where supported.",
                expectedObservation = "Valve clicks or PID/state change confirms actuator and driver circuit.",
            ),
            GuidedTestStep(
                title = "Smoke or pressure test",
                instruction = "With vent sealed per shop procedure, apply low-pressure smoke at service port or purge line.",
                expectedObservation = "Visible smoke at hose, canister, tank seam, or vent filter pinpoints leak location.",
            ),
            GuidedTestStep(
                title = "Document leak path",
                instruction = "Record component, fitting, or cap failure for estimate and monitor re-run after repair.",
                expectedObservation = "Leak isolated to a single path; no leak found suggests intermittent or test condition issue.",
            ),
        ),
        relatedPids = listOf("EVAP Purge", "EVAP Vent", "Fuel Tank Pressure"),
        relatedDtcPrefixes = listOf("P044", "P045", "P049"),
        reportWording = "Guided EVAP leak workflow: codes and cap inspection documented, purge/vent actuation verified where available, smoke/pressure result and leak location recorded.",
    )

    val o2Sensor = GuidedTest(
        id = "guided_o2_sensor",
        title = "O2 sensor / fuel trim",
        symptomAliases = listOf(
            "o2 sensor",
            "oxygen sensor",
            "lazy o2",
            "heater circuit",
            "P013",
            "P014",
            "P015",
            "P0171",
            "P0172",
            "lean",
            "rich",
        ),
        preconditions = GuidedTestPreconditions(
            ignition = IgnitionRequirement.ENGINE_RUNNING,
            notes = listOf(
                "Fix exhaust leaks upstream of affected sensors before replacement.",
                "Allow closed-loop operation at operating temperature.",
            ),
        ),
        requiredTransport = GuidedTransportRequirement.ELM327,
        steps = listOf(
            GuidedTestStep(
                title = "Identify sensor bank/position",
                instruction = "Map DTCs to bank 1/2 and upstream vs. downstream sensors (P013x–P016x, P005x heaters).",
                expectedObservation = "Specific sensor circuit codes vs. system lean/rich codes (P017x).",
            ),
            GuidedTestStep(
                title = "Heater circuit codes",
                instruction = "If heater codes present, note whether sensor reaches closed-loop after warm-up.",
                expectedObservation = "Heater codes with slow closed-loop → heater or wiring; sensor may still switch once hot.",
            ),
            GuidedTestStep(
                title = "Upstream switching at idle",
                instruction = "Graph upstream O2 voltage or equivalence ratio at stable idle for 30–60 seconds.",
                expectedObservation = "Regular cross-counts indicate healthy upstream sensor; flat high/low suggests lazy or contaminated sensor.",
            ),
            GuidedTestStep(
                title = "Downstream vs. upstream",
                instruction = "Compare downstream to upstream at idle and light acceleration if both available.",
                expectedObservation = "Downstream mirrors upstream → possible cat efficiency issue; downstream flat with active upstream → cat may be filtering normally.",
            ),
            GuidedTestStep(
                title = "Fuel trim correlation",
                instruction = "Log STFT/LTFT during the same capture window.",
                expectedObservation = "Trims correcting in direction of reported lean/rich code support vacuum leak, fuel delivery, or sensor bias diagnosis.",
            ),
        ),
        relatedPids = listOf("O2 B1S1", "O2 B1S2", "STFT B1", "LTFT B1", "0143", "0144"),
        relatedDtcPrefixes = listOf("P013", "P014", "P015", "P016", "P005", "P017"),
        reportWording = "Guided O2/fuel-trim workflow: sensor position and heater status confirmed, upstream switching and downstream comparison captured with fuel trims.",
    )

    val overheating = GuidedTest(
        id = "guided_overheating",
        title = "Overheating / coolant temperature",
        symptomAliases = listOf(
            "overheating",
            "over heat",
            "hot",
            "temp gauge high",
            "coolant",
            "P0128",
            "P0217",
            "fan",
        ),
        preconditions = GuidedTestPreconditions(
            ignition = IgnitionRequirement.KEY_ON_THEN_ENGINE_RUNNING,
            notes = listOf(
                "Do not open a hot pressurized cooling system.",
                "Verify coolant level only on a cool engine unless using proper depressurization.",
            ),
        ),
        requiredTransport = GuidedTransportRequirement.ELM327,
        steps = listOf(
            GuidedTestStep(
                title = "ECT PID at idle",
                instruction = "Display engine coolant temperature (ECT) and watch warm-up from cold start if safe.",
                expectedObservation = "Gradual rise to thermostat-opening range (~180–220 °F / 82–104 °C per spec); flat line may be sensor or wiring.",
            ),
            GuidedTestStep(
                title = "Fan command behavior",
                instruction = "Observe cooling fan on/off relative to ECT; note manual fan command if supported.",
                expectedObservation = "Fan engages near high-ECT threshold; no engagement with high ECT → fan, relay, or command fault.",
            ),
            GuidedTestStep(
                title = "Thermostat rationality",
                instruction = "After warm-up, note whether ECT stabilizes in thermostat-regulated band or climbs continuously.",
                expectedObservation = "Continuous climb at idle/load suggests low coolant, air pocket, stuck-closed thermostat, or restricted flow.",
            ),
            GuidedTestStep(
                title = "Visual underhood check",
                instruction = "Engine off and cool: inspect level, hose condition, radiator airflow, and obvious leaks.",
                expectedObservation = "Low level, collapsed hose, or external leak explains overheating without condemning water pump.",
            ),
            GuidedTestStep(
                title = "Overheat-related DTCs",
                instruction = "Read codes for ECT circuit, thermostat rationality, and engine over-temperature (P0128, P0217, P218x).",
                expectedObservation = "Sensor/rationality codes vs. no codes with physical overheating points to mechanical cooling failure.",
            ),
        ),
        relatedPids = listOf("0105", "ECT", "Coolant Temp", "Fan Status"),
        relatedDtcPrefixes = listOf("P012", "P021", "P218"),
        reportWording = "Guided overheating workflow: ECT warm-up and fan behavior documented, thermostat rationality noted, underhood visual and temperature-related DTCs recorded.",
    )
}
