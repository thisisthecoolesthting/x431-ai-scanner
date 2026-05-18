package com.caseforge.scanner

import android.app.Application
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.SettingsRepo

class App : Application() {
    lateinit var settings: SettingsRepo
        private set
    lateinit var actionLog: AgentActionLog
        private set
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepo(this)
        actionLog = AgentActionLog(this)
        db = AppDatabase.get(this)
    }
}
