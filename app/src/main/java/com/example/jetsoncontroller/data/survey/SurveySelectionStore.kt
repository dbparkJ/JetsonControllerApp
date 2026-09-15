package com.example.jetsoncontroller.data.survey

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.security.MessageDigest

private val Context.surveyRunDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "survey_run_context")

data class SurveySelection(
    val surveyProjectId: String,
    val surveyProjectRevision: Int,
    val surveySectionId: String,
    val surveySectionRevision: Int
)

data class PendingContextualStart(
    val pipelineId: String,
    val surveyProjectId: String,
    val surveyProjectRevision: Int,
    val surveySectionId: String,
    val surveySectionRevision: Int,
    val policyRevision: String,
    val preflightId: String,
    val clientRequestId: String
)

data class SurveyRunLocalState(
    val selection: SurveySelection? = null,
    val pendingStart: PendingContextualStart? = null,
    val lastPipelineId: String? = null,
    val lastRunId: String? = null
)

interface SurveyRunPersistence {
    fun state(deviceId: String): Flow<SurveyRunLocalState>
    suspend fun saveSelection(deviceId: String, selection: SurveySelection?)
    suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?)
    suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String)
}

class SurveySelectionStore(context: Context) : SurveyRunPersistence {
    private val dataStore = context.applicationContext.surveyRunDataStore

    override fun state(deviceId: String): Flow<SurveyRunLocalState> = dataStore.data.map { preferences ->
        decode(preferences[key(deviceId)])
    }

    override suspend fun saveSelection(deviceId: String, selection: SurveySelection?) {
        update(deviceId) { it.copy(selection = selection) }
    }

    override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) {
        update(deviceId) { it.copy(pendingStart = pending) }
    }

    override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) {
        update(deviceId) {
            it.copy(pendingStart = null, lastPipelineId = pipelineId, lastRunId = runId)
        }
    }

    private suspend fun update(deviceId: String, transform: (SurveyRunLocalState) -> SurveyRunLocalState) {
        val preferenceKey = key(deviceId)
        dataStore.edit { preferences ->
            preferences[preferenceKey] = encode(transform(decode(preferences[preferenceKey])))
        }
    }

    private fun key(deviceId: String) = stringPreferencesKey("device_${deviceScope(deviceId)}")
}

internal fun deviceScope(deviceId: String): String = MessageDigest.getInstance("SHA-256")
    .digest(deviceId.trim().lowercase().toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

private fun encode(state: SurveyRunLocalState): String = JSONObject().apply {
    put("version", 1)
    state.selection?.let { selection ->
        put("selection", JSONObject()
            .put("surveyProjectId", selection.surveyProjectId)
            .put("surveyProjectRevision", selection.surveyProjectRevision)
            .put("surveySectionId", selection.surveySectionId)
            .put("surveySectionRevision", selection.surveySectionRevision))
    }
    state.pendingStart?.let { pending ->
        put("pendingStart", JSONObject()
            .put("pipelineId", pending.pipelineId)
            .put("surveyProjectId", pending.surveyProjectId)
            .put("surveyProjectRevision", pending.surveyProjectRevision)
            .put("surveySectionId", pending.surveySectionId)
            .put("surveySectionRevision", pending.surveySectionRevision)
            .put("policyRevision", pending.policyRevision)
            .put("preflightId", pending.preflightId)
            .put("clientRequestId", pending.clientRequestId))
    }
    state.lastPipelineId?.let { put("lastPipelineId", it) }
    state.lastRunId?.let { put("lastRunId", it) }
}.toString()

private fun decode(encoded: String?): SurveyRunLocalState = if (encoded == null) {
    SurveyRunLocalState()
} else runCatching {
    val json = JSONObject(encoded)
    require(json.optInt("version") == 1)
    val selection = json.optJSONObject("selection")?.let {
        SurveySelection(
            it.getString("surveyProjectId"),
            it.getInt("surveyProjectRevision"),
            it.getString("surveySectionId"),
            it.getInt("surveySectionRevision")
        )
    }
    val pending = json.optJSONObject("pendingStart")?.let {
        PendingContextualStart(
            it.getString("pipelineId"),
            it.getString("surveyProjectId"),
            it.getInt("surveyProjectRevision"),
            it.getString("surveySectionId"),
            it.getInt("surveySectionRevision"),
            it.getString("policyRevision"),
            it.getString("preflightId"),
            it.getString("clientRequestId")
        )
    }
    SurveyRunLocalState(
        selection = selection,
        pendingStart = pending,
        lastPipelineId = json.optString("lastPipelineId").takeIf(String::isNotBlank),
        lastRunId = json.optString("lastRunId").takeIf(String::isNotBlank)
    )
}.getOrDefault(SurveyRunLocalState())
