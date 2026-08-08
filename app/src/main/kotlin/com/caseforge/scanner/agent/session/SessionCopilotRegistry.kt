package com.caseforge.scanner.agent.session

import com.caseforge.scanner.ui.session.ActiveCustomerSession

/**
 * Holds the active New Session chat context so copilot tools (e.g. [analyze_session_photos])
 * can reach wizard photos without MainActivity plumbing.
 */
object SessionCopilotRegistry {
    @Volatile
    var active: ActiveCustomerSession? = null
        private set

    fun bind(session: ActiveCustomerSession?) {
        active = session
    }
}
