package com.caseforge.scanner.agent

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Append-only audit log for every action the agent takes. With fully autonomous mode enabled,
 * this is the user's record of WHAT the agent did and WHEN — important for the "actuate without
 * asking" mode.
 */
class AgentActionLog(context: Context) {
    private val file: File = File(context.filesDir, "agent_actions.log").also {
        if (!it.exists()) it.createNewFile()
    }
    private val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun event(kind: String, detail: String) {
        file.appendText("${ts.format(Date())}\t$kind\t${detail.replace('\n', ' ')}\n")
    }

    fun tail(maxLines: Int = 200): List<String> {
        val lines = file.readLines()
        return if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
    }

    fun clear() { file.writeText("") }
}
