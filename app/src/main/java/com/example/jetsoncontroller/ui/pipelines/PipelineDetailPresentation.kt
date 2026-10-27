package com.example.jetsoncontroller.ui.pipelines

internal enum class PipelineDetailContent { OFFLINE, ERROR, LOADING, EMPTY, CONTENT }

/** Generic load failures explain recovery; specific causes remain visible. */
internal fun pipelineFailureDetail(message: String?, title: String): String {
    val genericTitles = listOf(title, "수집 작업을 불러오지 못했습니다", "작업 목록을 불러오지 못했습니다",
        "작업 정보를 불러오지 못했습니다", "폴더 내용을 불러오지 못했습니다", "폴더를 불러오지 못했습니다")
    val original = message.orEmpty().trim()
    val prefix = genericTitles.firstOrNull { original.startsWith(it) }
    val detail = (if (prefix != null) original.removePrefix(prefix) else original)
        .trimStart(' ', '.', '!', '·', ':', '\n', '\r').trimEnd()
    val normalized = detail.trimEnd('.', '!')
    return if (normalized.isBlank() || normalized in setOf("다시 시도하세요", "다시 조회하세요", "다시 확인하세요"))
        "장치 연결을 확인하고 다시 조회하세요." else detail
}

internal fun pipelineListContent(loading: Boolean, error: String?, hasItems: Boolean): PipelineDetailContent = when {
    hasItems -> PipelineDetailContent.CONTENT
    loading -> PipelineDetailContent.LOADING
    error != null -> PipelineDetailContent.ERROR
    else -> PipelineDetailContent.EMPTY
}

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
