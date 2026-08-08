package com.caseforge.scanner.planb.golden

data class GoldenFrameSummary(
    val totalFrames: Int,
    val txFrames: Int,
    val rxFrames: Int,
    val uniqueCanIds: Int,
    /** CAN IDs → frame count, sorted by id for stable dumps/tests. */
    val framesByCanId: Map<String, Int>,
) {
    companion object {
        fun summarize(lines: List<GoldenLogLine>): GoldenFrameSummary {
            val tx = lines.count { it.dir == "TX" }
            val rx = lines.count { it.dir == "RX" }
            val byId = lines.groupingBy { it.canId }.eachCount()
            return GoldenFrameSummary(
                totalFrames = lines.size,
                txFrames = tx,
                rxFrames = rx,
                uniqueCanIds = byId.size,
                framesByCanId = byId.toSortedMap(),
            )
        }
    }
}
