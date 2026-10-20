package com.example.jetsoncontroller.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.example.jetsoncontroller.data.server.ServerEndpointProfile
import com.example.jetsoncontroller.data.server.StoredServerProfile
import com.example.jetsoncontroller.ui.model.Tone
import com.example.jetsoncontroller.ui.components.geo.GeoBadge
import com.example.jetsoncontroller.ui.components.geo.GeoButton
import com.example.jetsoncontroller.ui.components.geo.GeoFactRow
import com.example.jetsoncontroller.ui.components.geo.GeoOverlayAction
import com.example.jetsoncontroller.ui.components.geo.GeoSheet
import com.example.jetsoncontroller.ui.components.geo.GeoSheetFooter
import com.example.jetsoncontroller.ui.components.geo.GeoSheetKind
import com.example.jetsoncontroller.ui.components.geo.GeoStateBlock
import com.example.jetsoncontroller.ui.components.geo.GeoStateContent
import com.example.jetsoncontroller.ui.components.geo.GeoStateKind
import com.example.jetsoncontroller.ui.components.geo.GeoControlShape
import com.example.jetsoncontroller.ui.theme.LocalGeoColors
import com.example.jetsoncontroller.ui.components.geo.geoFocusRing
import com.example.jetsoncontroller.ui.components.geo.geoInputTextStyle
import com.example.jetsoncontroller.ui.components.geo.geoLabelTextStyle
import com.example.jetsoncontroller.ui.theme.LocalGeoType

@Composable
internal fun ServerConnectionScreen(
    profile: StoredServerProfile?,
    state: ServerConnectionUiState,
    onBack: () -> Unit,
    onCheck: (ServerEndpointProfile) -> Unit,
    onRelogin: (ServerEndpointProfile, String) -> Unit
) {
    var reloginOpen by rememberSaveable(profile?.profile?.profileId) { mutableStateOf(false) }
    var token by remember(profile?.profile?.profileId) { mutableStateOf("") }
    LaunchedEffect(profile?.profile?.profileId) {
        profile?.let { onCheck(it.profile) }
    }
    DisposableEffect(Unit) {
        onDispose { token = "" }
    }

    if (reloginOpen && profile != null) {
        GeoSheet(
            title = "다시 로그인",
            subtitle = profile.profile.displayName,
            kind = GeoSheetKind.FORM,
            onDismissRequest = {
                token = ""
                reloginOpen = false
            },
            footer = {
                GeoSheetFooter(
                    primaryAction = GeoOverlayAction(
                        label = "다시 로그인",
                        onClick = {
                            val submittedToken = token
                            token = ""
                            reloginOpen = false
                            onRelogin(profile.profile, submittedToken)
                        },
                        enabled = token.isNotBlank(),
                        busy = state.isBusy
                    )
                )
            }
        ) {
            MaskedEmployeeTokenField(token) { token = it }
        }
    }

    Column(Modifier.fillMaxSize().background(LocalGeoColors.current.surface)) {
        SettingsPageHeader("서버 연결", onBack)
        if (profile == null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp)) {
                GeoStateBlock(
                    GeoStateContent(
                        kind = GeoStateKind.MISSING,
                        title = "서버 연결 정보가 없습니다",
                        explanation = "관리자에게 서버 연결 정보를 받아 설정하세요."
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp)
            ) {
                item {
                    ServerConnectionStatusRow(state)
                    GeoFactRow("서버", profile.profile.displayName)
                    GeoFactRow("환경", serverEnvironmentLabel(profile.profile.environment))
                    GeoFactRow("직원", profile.profile.employeeId)
                    GeoFactRow("프로젝트", profile.profile.projectId)
                }
                if (state.errorMessage != null) {
                    item {
                        GeoStateBlock(
                            GeoStateContent(
                                kind = GeoStateKind.ERROR,
                                title = connectionErrorTitle(state.status),
                                explanation = state.errorMessage
                            ),
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                GeoButton(
                    label = if (state.status == ServerConnectionStatus.LOGIN_REQUIRED) "다시 로그인" else "연결 확인",
                    onClick = {
                        if (state.status == ServerConnectionStatus.LOGIN_REQUIRED) {
                            token = ""
                            reloginOpen = true
                        } else {
                            onCheck(profile.profile)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                    busy = state.isBusy,
                    busyLabel = "확인하는 중"
                )
            }
        }
    }
}

@Composable
private fun ServerConnectionStatusRow(state: ServerConnectionUiState) {
    val (word, tone) = when (state.status) {
        ServerConnectionStatus.UNKNOWN -> "확인 전" to Tone.UNKNOWN
        ServerConnectionStatus.CHECKING -> "확인 중" to Tone.PENDING
        ServerConnectionStatus.CONNECTED -> "연결됨" to Tone.SUCCESS
        ServerConnectionStatus.LOGIN_REQUIRED -> "다시 로그인 필요" to Tone.WARNING
        ServerConnectionStatus.FAILED -> "연결 실패" to Tone.ERROR
    }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("상태", Modifier.weight(1f), color = LocalGeoColors.current.textSecondary,
            style = LocalGeoType.current.body)
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GeoBadge(word, tone)
            state.lastCheckedAtEpochMillis?.let {
                Text(
                    "마지막 확인 " + formatServerCheckTime(it),
                    color = LocalGeoColors.current.textSecondary,
                    style = LocalGeoType.current.caption
                )
            }
        }
    }
}

private fun connectionErrorTitle(status: ServerConnectionStatus): String =
    if (status == ServerConnectionStatus.LOGIN_REQUIRED) "다시 로그인해야 합니다" else "연결 실패"

@Composable
private fun MaskedEmployeeTokenField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalGeoColors.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("직원 토큰", color = colors.text, style = geoLabelTextStyle())
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .padding(top = 8.dp)
                .onFocusChanged { focused = it.hasFocus }
                .geoFocusRing(GeoControlShape, focused)
                .clip(GeoControlShape)
                .background(colors.field)
                .border(1.dp, colors.borderStrong, GeoControlShape)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics {
                    contentDescription = "직원 토큰"
                    password()
                },
            textStyle = geoInputTextStyle().copy(color = colors.text),
            cursorBrush = SolidColor(colors.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )
        Text(
            "관리자에게 받은 토큰을 입력하세요.",
            Modifier.padding(top = 8.dp),
            color = colors.textSecondary,
            style = LocalGeoType.current.caption
        )
    }
}
