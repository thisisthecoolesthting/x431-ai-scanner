package com.caseforge.scanner.ui.tuning

import android.content.Context
import com.caseforge.scanner.planb.PlanbMarque
import com.caseforge.scanner.update.AssetOverlay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ReferenceProcedure(
    val id: String,
    val title: String,
    val marques: List<String> = emptyList(),
    val summary: String = "",
    val steps: List<String> = emptyList(),
    val requiresOemTool: Boolean = true,
)

@Serializable
data class ReferenceBundle(
    val schemaVersion: Int = 1,
    val disclaimer: String = "",
    val procedures: List<ReferenceProcedure> = emptyList(),
)

object TuningReferenceLoader {
    private const val SERVICE_RESET = "planb/service-reset-reference.json"
    private const val ADAPTATION = "planb/adaptation-reference.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun loadServiceReset(context: Context): ReferenceBundle? =
        load(context, SERVICE_RESET)

    fun loadAdaptation(context: Context): ReferenceBundle? =
        load(context, ADAPTATION)

    private fun load(context: Context, path: String): ReferenceBundle? = runCatching {
        val text = AssetOverlay.readText(context.applicationContext, path) ?: return@runCatching null
        json.decodeFromString<ReferenceBundle>(text)
    }.getOrNull()

    fun filterForMarque(bundle: ReferenceBundle?, marque: PlanbMarque): List<ReferenceProcedure> {
        val all = bundle?.procedures.orEmpty()
        if (all.isEmpty()) return emptyList()
        return all.filter { proc ->
            proc.marques.isEmpty() || proc.marques.any { it.equals(marque.id, ignoreCase = true) }
        }
    }
}
