package com.example.jetsoncontroller.ui.pipelines

internal enum class PipelineDetailContent { OFFLINE, ERROR, LOADING, EMPTY, CONTENT }

internal fun pipelineLogIsLive(available: Boolean, polling: Boolean, active: Boolean): Boolean = available && polling && active

internal fun pipelineDetailContent(available: Boolean, loading: Boolean, error: String?, hasContent: Boolean): PipelineDetailContent = when {
    hasContent -> PipelineDetailContent.CONTENT
    !available -> PipelineDetailContent.OFFLINE
    error != null -> PipelineDetailContent.ERROR
    loading -> PipelineDetailContent.LOADING
    else -> PipelineDetailContent.EMPTY
}

internal data class ConfigFieldCaption(val label: String, val key: String?)

internal fun configFieldCaption(path: String, label: String): ConfigFieldCaption {
    if (label.any { it in '가'..'힣' }) return ConfigFieldCaption(label, path)
    // Unknown keys stay visible as secondary technical text; do not guess their meaning.
    return ConfigFieldCaption("설정 값", path.ifBlank { label })
}
