package com.caseforge.scanner.planb.golden

import kotlinx.serialization.json.Json

object GoldenLogParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(jsonl: String): List<GoldenLogLine> =
        jsonl.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line -> json.decodeFromString(GoldenLogLine.serializer(), line) }
            .toList()
}
