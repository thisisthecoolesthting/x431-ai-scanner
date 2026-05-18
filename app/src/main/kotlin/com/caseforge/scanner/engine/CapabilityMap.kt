package com.caseforge.scanner.engine

import com.caseforge.scanner.data.SequenceDefinitions

/**
 * Declarative descriptors for every X431 diagnostic capability Launch AI knows how to drive.
 *
 * Each entry says: "to do X, navigate this menu path and wait for this done-marker."
 * This file is the single source of truth for the "skill inheritance" from the original
 * X431 app — Haiku agents add new entries here as they explore each vehicle make.
 *
 * NOTE: paths are lower-cased substring matches against accessibility node text, so they
 * tolerate small UI changes in X431 ("Auto Scan" vs "Auto-Scan" vs "Auto  Scan").
 *
 * When X431 ships a UI update, the JSON shipped as `assets/capabilities.json` overrides
 * this baked-in baseline. That JSON is hot-patchable from a GitHub raw URL.
 */
object CapabilityMap {

    open class Capability(
        val id: String,
        val label: String,
        val category: Category,
        /** Menu items to tap in order, lower-cased substring match. */
        val path: List<String>,
        /** A piece of text we expect on screen when the operation is complete. */
        val doneWhen: String? = null,
        /** Hard timeout in seconds before we abort and report failure. */
        val timeoutSec: Int = 60,
        /** OEM scope: empty = universal, otherwise list of makes ("ford","gm","stellantis"). */
        val oemScope: Set<String> = emptySet(),
        /** Human-readable note for techs / for the AI agent's reasoning. */
        val note: String? = null,
    )

    enum class Category { Scan, Codes, LiveData, Service, Bidirectional, Programming, Coding, Module, Sequences }

    // -------- Baseline (universal-ish across X431 PRO / PROS / V+) --------

    val ALL: List<Capability> = listOf(
        Capability(
            id = "full_scan",
            label = "Full system scan",
            category = Category.Scan,
            path = listOf("diagnose", "auto scan"),
            doneWhen = "scan complete",
            timeoutSec = 180,
            note = "Scans every module on the bus, reports DTCs per module.",
        ),
        Capability(
            id = "read_dtcs",
            label = "Read DTCs",
            category = Category.Codes,
            path = listOf("diagnose", "read fault code"),
            doneWhen = "fault code",
            timeoutSec = 45,
        ),
        Capability(
            id = "clear_dtcs",
            label = "Clear DTCs",
            category = Category.Codes,
            path = listOf("diagnose", "clear fault"),
            doneWhen = "clear successfully",
            timeoutSec = 30,
            note = "Engine off, KOEO. Cycle key after.",
        ),
        Capability(
            id = "live_data",
            label = "Live data",
            category = Category.LiveData,
            path = listOf("diagnose", "read data stream"),
            timeoutSec = 600,
        ),
        Capability(
            id = "freeze_frame",
            label = "Freeze frame",
            category = Category.Codes,
            path = listOf("diagnose", "freeze frame"),
            timeoutSec = 30,
        ),
        Capability(
            id = "actuation",
            label = "Actuation test",
            category = Category.Bidirectional,
            path = listOf("diagnose", "actuation test"),
            timeoutSec = 120,
            note = "Bidirectional. Always confirm engine state first.",
        ),
        Capability(
            id = "oil_reset",
            label = "Oil service reset",
            category = Category.Service,
            path = listOf("service", "oil reset"),
            doneWhen = "successful",
            timeoutSec = 60,
        ),
        Capability(
            id = "epb",
            label = "EPB service",
            category = Category.Service,
            path = listOf("service", "epb"),
            timeoutSec = 120,
            note = "Retract caliper for brake pad job.",
        ),
        Capability(
            id = "sas",
            label = "Steering angle reset",
            category = Category.Service,
            path = listOf("service", "steering angle"),
            timeoutSec = 90,
        ),
        Capability(
            id = "tpms",
            label = "TPMS relearn",
            category = Category.Service,
            path = listOf("service", "tpms"),
            timeoutSec = 120,
        ),
        Capability(
            id = "battery_register",
            label = "Battery registration",
            category = Category.Service,
            path = listOf("service", "battery"),
            timeoutSec = 60,
        ),
        Capability(
            id = "throttle_relearn",
            label = "Throttle relearn",
            category = Category.Service,
            path = listOf("service", "throttle"),
            timeoutSec = 60,
        ),
        Capability(
            id = "dpf_regen",
            label = "DPF regeneration",
            category = Category.Service,
            path = listOf("service", "dpf"),
            timeoutSec = 1800,
            note = "Diesel only. Vehicle must idle warm.",
        ),
        Capability(
            id = "injector_coding",
            label = "Injector coding",
            category = Category.Coding,
            path = listOf("service", "injector"),
            timeoutSec = 120,
        ),
        Capability(
            id = "key_program",
            label = "Key programming",
            category = Category.Programming,
            path = listOf("service", "immobilizer"),
            timeoutSec = 600,
            note = "Some makes need PIN code. May need Launch subscription for newer years.",
        ),
        Capability(
            id = "abs_bleed",
            label = "ABS / brake bleed",
            category = Category.Service,
            path = listOf("service", "abs"),
            timeoutSec = 300,
        ),
        Capability(
            id = "gear_learn",
            label = "Gearbox / TCM learn",
            category = Category.Coding,
            path = listOf("service", "gear", "learn"),
            timeoutSec = 180,
        ),
        Capability(
            id = "suspension",
            label = "Suspension calibration",
            category = Category.Service,
            path = listOf("service", "suspension"),
            timeoutSec = 120,
        ),
        Capability(
            id = "ecu_coding",
            label = "ECU coding",
            category = Category.Coding,
            path = listOf("coding", "ecu coding"),
            timeoutSec = 180,
        ),
        Capability(
            id = "module_program",
            label = "Module programming (J2534)",
            category = Category.Programming,
            path = listOf("program"),
            timeoutSec = 1800,
            note = "Online programming requires Launch subscription + reliable wifi.",
        ),
    ) + SequenceDefinitions.asCapabilities()

    fun byId(id: String): Capability? = (ALL + SequenceDefinitions.ALL).firstOrNull { it.id == id }
    fun byCategory(c: Category): List<Capability> = (ALL + SequenceDefinitions.ALL).filter { it.category == c }
}
