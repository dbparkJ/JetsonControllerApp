package com.example.jetsoncontroller.ui.components.geo

import com.example.jetsoncontroller.ui.theme.LocalGeoType
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

data class GeoTechnicalValue(val label: String, val value: String)

@Composable
fun GeoTechInfo(
    values: List<GeoTechnicalValue>,
    developerMode: Boolean,
    showTechnicalInfo: Boolean,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onCopy: ((GeoTechnicalValue) -> Unit)? = null
) {
    if (!developerMode || !showTechnicalInfo) return
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val colors = LocalGeoColors.current
    val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxWidth().padding(top = 28.dp)) {
        Row(Modifier.fillMaxWidth().contentTarget { expanded = !expanded }.contentDivider()
            .heightIn(min = 56.dp).padding(vertical = 16.dp)
            .semantics(mergeDescendants = true) { stateDescription = if (expanded) "펼쳐짐" else "접힘" },
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("기술 정보", Modifier.weight(1f), color = colors.text, style = LocalGeoType.current.sectionTitle)
            androidx.compose.material3.Icon(if (expanded) GeoIcons.chevronUp else GeoIcons.chevronDown,
                if (expanded) "접기" else "펼치기", Modifier.size(20.dp), tint = colors.textSubtle)
        }
        if (expanded) {
            values.forEach { entry ->
                BoxWithConstraints(Modifier.fillMaxWidth().contentDivider().padding(vertical = 8.dp)) {
                    val stacked = LocalDensity.current.fontScale >= 1.5f || maxWidth < 320.dp
                    val copy: () -> Unit = {
                        if (onCopy != null) onCopy(entry) else clipboard.setText(AnnotatedString(entry.value))
                    }
                    if (stacked) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.label, color = colors.textSecondary, style = LocalGeoType.current.body)
                            SelectionContainer { Text(entry.value, color = colors.text, style = LocalGeoType.current.mono) }
                            TechCopy(entry.label, copy)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Text(entry.label, Modifier.weight(.32f), color = colors.textSecondary, style = LocalGeoType.current.body)
                            SelectionContainer(Modifier.weight(.68f)) {
                                Text(entry.value, color = colors.text, style = LocalGeoType.current.mono)
                            }
                            TechCopy(entry.label, copy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechCopy(label: String, onClick: () -> Unit) {
    GeoIconButton(GeoIcons.copy, "$label 복사", onClick)
}
