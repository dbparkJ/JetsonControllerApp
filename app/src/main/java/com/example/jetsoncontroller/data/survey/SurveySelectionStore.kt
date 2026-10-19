package com.example.jetsoncontroller.data.survey

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.jetsoncontroller.model.PreparationDraft
import com.example.jetsoncontroller.model.PreparationSelection
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

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
    val clientRequestId: String,
    val deviceId: String? = null,
    val serverProfileId: String? = null,
    val ticketRowId: String? = null,
    val ticketRevision: String? = null,
    val geometryRevision: String? = null,
    val geometryHash: String? = null,
    val projectRequestId: String? = null,
    val sectionRequestId: String? = null
)

data class SurveyRunLocalState(
    val selection: SurveySelection? = null,
    val pendingStart: PendingContextualStart? = null,
    val lastPipelineId: String? = null,
    val lastRunId: String? = null,
    val acknowledgedRunId: String? = null,
    val preparationDraft: PreparationDraft? = null,
    val unconfirmedRunId: String? = null,
    /** Keeps ticket and geometry bound to an accepted run without extending the device's wire schema. */
    val acceptedStarts: Map<String, PendingContextualStart> = emptyMap()
)

interface SurveyRunPersistence {
    fun state(deviceId: String): Flow<SurveyRunLocalState>
    suspend fun saveSelection(deviceId: String, selection: SurveySelection?)
    suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?)
    suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String)
    suspend fun saveAcknowledgedRun(deviceId: String, runId: String)
    suspend fun savePreparationDraft(deviceId: String, draft: PreparationDraft?) = Unit
    suspend fun saveUnconfirmedRun(deviceId: String, runId: String?) = Unit
}

class SurveySelectionStore internal constructor(private val dataStore: DataStore<Preferences>) : SurveyRunPersistence {
    constructor(context: Context) : this(context.applicationContext.surveyRunDataStore)

    override fun state(deviceId: String): Flow<SurveyRunLocalState> = dataStore.data.map { preferences ->
        decode(preferences[key(deviceId)])
    }

    override suspend fun saveSelection(deviceId: String, selection: SurveySelection?) {
        update(deviceId) { it.copy(selection = selection) }
    }

    override suspend fun savePendingStart(deviceId: String, pending: PendingContextualStart?) {
        pending?.deviceId?.let { require(deviceScope(it) == deviceScope(deviceId)) }
        update(deviceId) { it.copy(pendingStart = pending) }
    }

    override suspend fun saveAcceptedRun(deviceId: String, pipelineId: String, runId: String) {
        update(deviceId) {
            it.copy(pendingStart = null, preparationDraft = null, unconfirmedRunId = null,
                lastPipelineId = pipelineId, lastRunId = runId,
                acceptedStarts = it.pendingStart?.let { pending -> it.acceptedStarts + (runId to pending) } ?: it.acceptedStarts)
        }
    }

    override suspend fun saveAcknowledgedRun(deviceId: String, runId: String) {
        update(deviceId) { it.copy(acknowledgedRunId = runId) }
    }

    override suspend fun savePreparationDraft(deviceId: String, draft: PreparationDraft?) {
        require(draft == null || deviceScope(draft.deviceId) == deviceScope(deviceId))
        update(deviceId) { it.copy(preparationDraft = draft) }
    }

    override suspend fun saveUnconfirmedRun(deviceId: String, runId: String?) {
        update(deviceId) {
            it.copy(unconfirmedRunId = runId, lastRunId = runId ?: it.lastRunId,
                lastPipelineId = if (runId == null) it.lastPipelineId else
                    it.pendingStart?.pipelineId ?: it.preparationDraft?.pipelineId ?: it.lastPipelineId)
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
    .digest(deviceId.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

/** A changed decision gets a new id; retries and task choices retain the original id and label. */
fun PreparationDraft.withCreationRequestIds(newId: () -> String = { UUID.randomUUID().toString() }): PreparationDraft = copy(
    projectRequestId = projectRequestId ?: if (project.isCreation()) newId() else null,
    sectionRequestId = sectionRequestId ?: if (section.isCreation()) newId() else null
)

fun PreparationDraft.chooseProject(choice: PreparationSelection?, now: Long): PreparationDraft =
    if (project == choice) this else copy(project = choice, section = null,
        projectRequestId = null, sectionRequestId = null, updatedAt = now).withCreationRequestIds()

fun PreparationDraft.chooseSection(choice: PreparationSelection?, now: Long): PreparationDraft =
    if (section == choice) this else copy(section = choice, sectionRequestId = null,
        updatedAt = now).withCreationRequestIds()

private fun PreparationSelection?.isCreation(): Boolean =
    this is PreparationSelection.CreateFromTicket || this is PreparationSelection.CreateNew

internal fun encode(state: SurveyRunLocalState): String {
    val gson = Gson()
    return gson.toJsonTree(state).asJsonObject.apply {
        addProperty("version", 2)
        state.preparationDraft?.let { draft ->
            getAsJsonObject("preparationDraft").apply {
                add("project", encodeChoice(draft.project))
                add("section", encodeChoice(draft.section))
            }
        }
    }.toString()
}

private fun encodeChoice(choice: PreparationSelection?): JsonObject? = choice?.let {
    JsonObject().apply {
        when (it) {
            is PreparationSelection.Existing -> {
                addProperty("kind", "existing"); addProperty("id", it.id); addProperty("revision", it.revision)
            }
            is PreparationSelection.CreateFromTicket -> { addProperty("kind", "ticket"); addProperty("label", it.label) }
            is PreparationSelection.CreateNew -> { addProperty("kind", "new"); addProperty("label", it.label) }
        }
    }
}

private fun decodeChoice(json: JsonObject?): PreparationSelection? = json?.let {
    when (it.get("kind").asString) {
        "existing" -> PreparationSelection.Existing(it.get("id").asString, it.get("revision").asInt)
        "ticket" -> PreparationSelection.CreateFromTicket(it.get("label").asString)
        "new" -> PreparationSelection.CreateNew(it.get("label").asString)
        else -> error("Unknown preparation choice")
    }
}

internal fun decode(encoded: String?): SurveyRunLocalState = if (encoded == null) {
    SurveyRunLocalState()
} else runCatching {
    val gson = Gson()
    val json = JsonParser.parseString(encoded).asJsonObject
    require(json.get("version").asInt in 1..2)
    val draftJson = json.remove("preparationDraft")?.takeUnless { it.isJsonNull }?.asJsonObject
    val draft = runCatching { draftJson?.let {
        val project = decodeChoice(it.remove("project")?.takeUnless { value -> value.isJsonNull }?.asJsonObject)
        val section = decodeChoice(it.remove("section")?.takeUnless { value -> value.isJsonNull }?.asJsonObject)
        gson.fromJson(it, PreparationDraft::class.java).copy(project = project, section = section)
    } }.getOrNull()
    gson.fromJson(json, SurveyRunLocalState::class.java).copy(preparationDraft = draft)
}.getOrDefault(SurveyRunLocalState())
