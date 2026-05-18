package com.caseforge.scanner

import android.app.Application
import com.caseforge.scanner.agent.AgentActionLog
import com.caseforge.scanner.agent.AgentStatus
import com.caseforge.scanner.agent.AgentTts
import com.caseforge.scanner.data.AppDatabase
import com.caseforge.scanner.data.SettingsRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var settings: SettingsRepo
        private set
    lateinit var actionLog: AgentActionLog
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var tts: AgentTts
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepo(this)
        actionLog = AgentActionLog(this)
        db = AppDatabase.get(this)
        tts = AgentTts(this)
        com.caseforge.scanner.agent.AcousticTool.attach(this)
        com.caseforge.scanner.agent.CostTracker.loadLifetime(this)

        // Speak each new activity line when TTS is enabled.
        scope.launch {
            AgentStatus.activity.collect { msg ->
                if (settings.speakEnabled && msg.isNotBlank()) tts.speak(msg)
            }
        }
    }
}
