package io.hrns_now.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.HrnsUiState
import io.hrns_now.app.presentation.model.NotificationItem
import io.hrns_now.app.presentation.model.NotificationTone
import io.hrns_now.core.AppRoute
import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.domain.model.ArtifactProbeResult
import io.hrns_now.core.domain.model.ArtifactProbeState
import io.hrns_now.core.domain.model.ArtifactRequirement
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.app.presentation.model.ShellProjection
import io.hrns_now.infra.InfraMarker
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// 최상위 Shell
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compose는 [uiState]를 그리기만 한다 — 파일 탐색·Reader 호출은
 * [io.hrns_now.app.presentation.viewmodel.AppViewModel]이 이미 끝낸 뒤 이 값으로 전달한다.
 */
@Composable
fun HrnsShell(
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
    uiState: HrnsUiState,
    themeMode: HrnsThemeMode,
    onThemeToggle: () -> Unit,
    onCockpitAction: (UiAction) -> Unit,
    onUiEvent: (HrnsUiEvent) -> Unit,
    notifications: List<NotificationItem> = emptyList(),
    onDismissNotification: (String) -> Unit = {},
    onMarkNotificationRead: (String) -> Unit = {},
    onMarkAllNotificationsRead: () -> Unit = {},
    locale: AppLocale = AppLocale.Korean,
    onLocaleChange: (AppLocale) -> Unit = {},
) {
    val colors = LocalHrnsColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.appBackground,
    ) {
        when (uiState) {
            HrnsUiState.Loading -> LoadingShell()
            is HrnsUiState.Ready -> ReadyShell(
                selectedRoute = selectedRoute,
                onRouteSelected = onRouteSelected,
                state = uiState,
                themeMode = themeMode,
                onThemeToggle = onThemeToggle,
                onCockpitAction = onCockpitAction,
                onUiEvent = onUiEvent,
                notifications = notifications,
                onDismissNotification = onDismissNotification,
                onMarkNotificationRead = onMarkNotificationRead,
                onMarkAllNotificationsRead = onMarkAllNotificationsRead,
                locale = locale,
                onLocaleChange = onLocaleChange,
            )
        }
    }
}

@Composable
private fun LoadingShell() {
    val colors = LocalHrnsColors.current
    val strings = chromeStrings(LocalAppLocale.current)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = strings.loadingNotice,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.secondaryText,
        )
    }
}

@Composable
private fun ReadyShell(
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
    state: HrnsUiState.Ready,
    themeMode: HrnsThemeMode,
    onThemeToggle: () -> Unit,
    onCockpitAction: (UiAction) -> Unit,
    onUiEvent: (HrnsUiEvent) -> Unit,
    notifications: List<NotificationItem>,
    onDismissNotification: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    locale: AppLocale,
    onLocaleChange: (AppLocale) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopRibbon(
            projection = state.shell,
            activeProjectName = state.cockpit.projectName,
            activeProjectStale = state.cockpit.isStale,
            readiness = state.workspaceReadiness,
            themeMode = themeMode,
            onThemeToggle = onThemeToggle,
            notifications = notifications,
            onDismissNotification = onDismissNotification,
            onMarkAllNotificationsRead = onMarkAllNotificationsRead,
            locale = locale,
            onLocaleChange = onLocaleChange,
        )
        Row(modifier = Modifier.fillMaxSize()) {
            LeftRail(
                selectedRoute = selectedRoute,
                onRouteSelected = onRouteSelected,
                modifier = Modifier
                    .width(248.dp)
                    .fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 56.dp),
            ) {
                ScreenRoute(
                    route = selectedRoute,
                    setupProjection = state.setup,
                    workspaceConfig = state.workspaceConfig,
                    workspaceProbeSummary = state.workspaceProbeSummary,
                    cockpitProjection = state.cockpit,
                    todayWorkProjection = state.todayWork,
                    runStatusProjection = state.runStatus,
                    recoveryProjection = state.recovery,
                    readiness = state.workspaceReadiness,
                    onCockpitAction = onCockpitAction,
                    registryProjects = state.registryProjects,
                    workspaceDays = state.workspaceDays,
                    todayDate = state.todayDate,
                    activeProjectSourceLabel = state.activeProjectSourceLabel,
                    registryMessage = state.registryMessage,
                    registrationFeedback = state.registrationFeedback,
                    onUiEvent = onUiEvent,
                )
            }
            InspectorPanel(
                projection = state.shell,
                artifactSummary = state.workspaceArtifactSummary,
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxSize(),
            )
        }
    }
        NotificationToastHost(
            notifications = notifications,
            onMarkRead = onMarkNotificationRead,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 84.dp, end = 24.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 상단 리본  ──  brand · 페이지 라벨 · 준비 상태 요약 · 테마 토글
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopRibbon(
    projection: ShellProjection,
    activeProjectName: String?,
    activeProjectStale: Boolean,
    readiness: WorkspaceReadiness,
    themeMode: HrnsThemeMode,
    onThemeToggle: () -> Unit,
    notifications: List<NotificationItem>,
    onDismissNotification: (String) -> Unit,
    onMarkAllNotificationsRead: () -> Unit,
    locale: AppLocale,
    onLocaleChange: (AppLocale) -> Unit,
) {
    val colors = LocalHrnsColors.current
    val strings = chromeStrings(locale)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appBackground)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 브랜드 + 활성 프로젝트 — 사용자가 화면을 열자마자 어느 프로젝트를 보고 있는지
        // 즉시 식별할 수 있어야 한다(새 Phase 6 제품 목표 1).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(min = 220.dp),
        ) {
            BrandMark()
            Column {
                Text(
                    text = projection.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = strings.projectLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.tertiaryText,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = activeProjectName ?: strings.notSelected,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            activeProjectName == null -> colors.tertiaryText
                            activeProjectStale -> colors.warning
                            else -> colors.primaryText
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 준비 상태 요약 (1행, 가운데)
        ReadinessRibbon(readiness, strings)

        Spacer(modifier = Modifier.weight(1f))

        // 알림함 — 우측 상단, 배지·transient toast·이력 조회(새 Phase 8 §4.2)
        NotificationBell(
            notifications = notifications,
            onDismiss = onDismissNotification,
            onMarkAllRead = onMarkAllNotificationsRead,
            strings = strings,
        )

        // 한국어/English 전환 — 선택 즉시 적용되고 UiPreferencesPort에만 저장된다(새 Phase 8 §7).
        LocaleToggle(locale = locale, strings = strings, onLocaleChange = onLocaleChange)

        // 테마 토글
        ThemeToggle(themeMode = themeMode, onClick = onThemeToggle)
    }
}

@Composable
private fun LocaleToggle(locale: AppLocale, strings: ChromeStrings, onLocaleChange: (AppLocale) -> Unit) {
    val colors = LocalHrnsColors.current
    TextButton(
        onClick = {
            onLocaleChange(if (locale == AppLocale.Korean) AppLocale.English else AppLocale.Korean)
        },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = colors.cardBackground,
            contentColor = colors.primaryText,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (locale == AppLocale.Korean) strings.localeSelectorKorean else strings.localeSelectorEnglish,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 전역 알림함 — 배지·이력 dropdown·transient toast(새 Phase 8 §4.2)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NotificationBell(
    notifications: List<NotificationItem>,
    onDismiss: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    strings: ChromeStrings,
) {
    val colors = LocalHrnsColors.current
    var trayOpen by remember { mutableStateOf(false) }
    val unreadCount = notifications.count { !it.read }

    TextButton(
        onClick = {
            trayOpen = !trayOpen
            if (trayOpen) onMarkAllRead()
        },
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = colors.cardBackground,
            contentColor = colors.primaryText,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = strings.notificationBell,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
        )
        if (unreadCount > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.background(colors.danger, RoundedCornerShape(999.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.onAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (trayOpen) {
        Popup(alignment = Alignment.TopEnd, onDismissRequest = { trayOpen = false }) {
            NotificationTrayContent(notifications = notifications, onDismiss = onDismiss, strings = strings)
        }
    }
}

@Composable
private fun NotificationTrayContent(
    notifications: List<NotificationItem>,
    onDismiss: (String) -> Unit,
    strings: ChromeStrings,
) {
    val colors = LocalHrnsColors.current
    Column(
        modifier = Modifier
            .width(340.dp)
            .heightIn(max = 420.dp)
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(16.dp))
            .background(colors.cardBackground, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, colors.chelseaBlueSoft), RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = strings.notificationBell,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
        )
        if (notifications.isEmpty()) {
            Text(
                text = strings.notificationEmpty,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.tertiaryText,
            )
        } else {
            notifications.forEach { item -> NotificationTrayRow(item, onDismiss, strings) }
        }
    }
}

@Composable
private fun NotificationTrayRow(item: NotificationItem, onDismiss: (String) -> Unit, strings: ChromeStrings) {
    val colors = LocalHrnsColors.current
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .background(item.tone.dotColor(), CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = item.message,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
            color = colors.primaryText,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        TextButton(
            onClick = { onDismiss(item.id) },
            modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(strings.dismiss, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
        }
    }
}

@Composable
private fun NotificationTone.dotColor(): Color {
    val c = LocalHrnsColors.current
    return when (this) {
        NotificationTone.Success -> c.success
        NotificationTone.Failure -> c.danger
        NotificationTone.Info -> c.warning
    }
}

/**
 * 최근 미확인 알림 하나를 짧게 보여주고 자동으로 사라진다(새 Phase 8 §4.2). 사용자가 명시적으로
 * 닫을 수도 있다 — 둘 다 해당 항목을 읽음 처리만 할 뿐 알림함 이력에서 지우지는 않는다.
 */
@Composable
private fun NotificationToastHost(
    notifications: List<NotificationItem>,
    onMarkRead: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = notifications.firstOrNull { !it.read } ?: return
    val colors = LocalHrnsColors.current
    val strings = chromeStrings(LocalAppLocale.current)

    LaunchedEffect(latest.id) {
        delay(4000)
        onMarkRead(latest.id)
    }

    Box(
        modifier = modifier
            .width(320.dp)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(14.dp))
            .background(colors.cardBackground, RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, colors.chelseaBlueSoft), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .background(latest.tone.dotColor(), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = latest.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp),
                color = colors.primaryText,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = { onMarkRead(latest.id) },
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(strings.dismiss, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun BrandMark() {
    val colors = LocalHrnsColors.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = colors.chelseaGlow,
                spotColor = colors.chelseaGlow,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource("icon.png"),
            contentDescription = "HRNS-NOW",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ReadinessRibbon(readiness: WorkspaceReadiness, strings: ChromeStrings) {
    val colors = LocalHrnsColors.current
    val locale = LocalAppLocale.current
    val items = listOf(
        strings.readinessWorkspace to readiness.workspaceLabel,
        strings.readinessEngine to readiness.engineLabel,
        strings.readinessBridge to readiness.bridgeLabel,
        strings.readinessProfile to readiness.profileLabel,
        strings.readinessDoctor to readiness.doctorLabel,
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(999.dp),
                ambientColor = colors.chelseaGlow,
                spotColor = colors.chelseaGlow,
            )
            .background(colors.cardBackground, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        items.forEachIndexed { idx, (label, value) ->
            if (idx > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(width = 1.dp, height = 12.dp)
                        .background(colors.borderSubtle),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(readinessDotColor(value), CircleShape),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        letterSpacing = 0.1.sp,
                    ),
                    color = colors.tertiaryText,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = localizeInfraLabel(value, locale),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        letterSpacing = 0.1.sp,
                    ),
                    color = colors.primaryText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun readinessDotColor(value: String): Color {
    val c = LocalHrnsColors.current
    val v = value.trim()
    return when {
        v.contains("확인됨") || v.contains("준비") || v.contains("Confirmed") || v.contains("Ready") || v == "OK" -> c.success
        v.contains("없음") || v.contains("미설정") || v.contains("미선택") || v.contains("Missing") || v.contains("Not configured") || v.contains("Not selected") -> c.warning
        v.contains("불가") || v.contains("실패") || v.contains("불일치") || v.contains("Not readable") || v.contains("Failed") || v.contains("Wrong type") -> c.danger
        else -> c.tertiaryText
    }
}

@Composable
private fun ThemeToggle(themeMode: HrnsThemeMode, onClick: () -> Unit) {
    val colors = LocalHrnsColors.current
    val glyph = when (themeMode) {
        HrnsThemeMode.Dark -> "☾"
        HrnsThemeMode.Light -> "☼"
    }
    val label = when (themeMode) {
        HrnsThemeMode.Dark -> "Dark"
        HrnsThemeMode.Light -> "Light"
    }

    TextButton(
        onClick = onClick,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.textButtonColors(
            containerColor = colors.cardBackground,
            contentColor = colors.primaryText,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = colors.accent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 좌측 레일
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LeftRail(
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colors = LocalHrnsColors.current
    val strings = chromeStrings(LocalAppLocale.current)

    Column(
        modifier = modifier
            .background(colors.appBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SidebarSectionLabel(strings.navSectionWorkflow)

        NavigationButton(
            text = strings.navSetup,
            selected = selectedRoute == AppRoute.Setup,
            onClick = { onRouteSelected(AppRoute.Setup) },
            leadingGlyph = "01",
        )
        NavigationButton(
            text = strings.navCockpit,
            selected = selectedRoute == AppRoute.Cockpit,
            onClick = { onRouteSelected(AppRoute.Cockpit) },
            leadingGlyph = "02",
        )
        NavigationButton(
            text = strings.navStrategy,
            selected = selectedRoute == AppRoute.Strategy,
            onClick = { onRouteSelected(AppRoute.Strategy) },
            leadingGlyph = "03",
        )
        NavigationButton(
            text = strings.navRun,
            selected = selectedRoute == AppRoute.Run,
            onClick = { onRouteSelected(AppRoute.Run) },
            leadingGlyph = "04",
        )
        NavigationButton(
            text = strings.navRecovery,
            selected = selectedRoute == AppRoute.Recovery,
            onClick = { onRouteSelected(AppRoute.Recovery) },
            leadingGlyph = "05",
        )

        Spacer(modifier = Modifier.height(28.dp))
        SidebarSectionLabel(strings.navSectionGuide)

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = strings.readOnlyShellNote,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                color = colors.tertiaryText,
            )
        }
    }
}

@Composable
private fun SidebarSectionLabel(text: String) {
    val colors = LocalHrnsColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.4.sp,
            fontSize = 10.5.sp,
        ),
        color = colors.tertiaryText,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 10.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 우측 인스펙터
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InspectorPanel(
    projection: ShellProjection,
    artifactSummary: WorkspaceArtifactSummary,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colors = LocalHrnsColors.current
    val strings = chromeStrings(LocalAppLocale.current)

    Column(
        modifier = modifier
            .background(colors.appBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "INSPECTOR",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.6.sp,
                    fontSize = 10.5.sp,
                ),
                color = colors.tertiaryText,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = strings.inspectorHeading,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            )
            Text(
                text = strings.inspectorSubtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.tertiaryText,
            )
        }

        // 새 Phase 8 §5.2: compact technical panel은 하나의 영문 표기 규칙(STATUS/ENVIRONMENT/
        // ARTIFACTS)으로 통일한다 — 이전에는 "아티팩트"(한글)/"Meta"(영문 약어)/"Read-only"가
        // 서로 다른 언어·모호한 제목으로 섞여 있었다 — 이 세 패널 제목은 Phase 8 보완에서도 그대로
        // 영문으로 고정한다(의도적 예외, phase8-completion-report.md §5 참고).
        SectionCard(title = "ARTIFACTS", eyebrow = strings.artifactsEyebrow) {
            // legacy fallback 파일(WORKDAY_STATE.json 등)은 기본 화면에서 숨긴다 (계약 2.2).
            val visibleItems = artifactSummary.items.filter { it.requirement != ArtifactRequirement.Legacy }
            Column {
                visibleItems.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.borderSubtle),
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                    ArtifactRow(item)
                    if (index < visibleItems.lastIndex) {
                        Spacer(Modifier.height(14.dp))
                    }
                }
            }
        }

        SectionCard(title = "ENVIRONMENT", eyebrow = strings.environmentEyebrow) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = projection.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = colors.secondaryText,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "infra",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            letterSpacing = 0.6.sp,
                        ),
                        color = colors.tertiaryText,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = InfraMarker.name,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        color = colors.primaryText,
                    )
                }
                // 이전에는 별도의 상시 노출 "앱이 소유하지 않음" 경고 카드였다 — 행동을 안내하지
                // 않는 반복 문구라 이 화면 기본 구성에서 제거하고, 필요한 사실만 여기 짧게 남긴다.
                if (projection.notAppOwnedMessages.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        projection.notAppOwnedMessages.forEach { message ->
                            Text(
                                text = "· $message",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                                color = colors.tertiaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactRow(item: ArtifactProbeResult) {
    val colors = LocalHrnsColors.current
    val locale = LocalAppLocale.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = localizeInfraLabel(item.label, locale),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = (-0.1).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                text = item.state.localizedLabel(locale),
                tone = item.state.tone(),
            )
        }
        Text(
            text = item.path,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            ),
            color = colors.tertiaryText,
        )
    }
}

private fun ArtifactProbeState.localizedLabel(locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (this) {
            ArtifactProbeState.WorkspaceNotConfigured -> "미선택"
            ArtifactProbeState.Exists -> "확인됨"
            ArtifactProbeState.Missing -> "없음"
            ArtifactProbeState.NotReadable -> "읽기 불가"
            ArtifactProbeState.WrongType -> "유형 불일치"
            ArtifactProbeState.Unknown -> "확인 필요"
        }
        AppLocale.English -> when (this) {
            ArtifactProbeState.WorkspaceNotConfigured -> "Not selected"
            ArtifactProbeState.Exists -> "Confirmed"
            ArtifactProbeState.Missing -> "Missing"
            ArtifactProbeState.NotReadable -> "Not readable"
            ArtifactProbeState.WrongType -> "Wrong type"
            ArtifactProbeState.Unknown -> "Needs review"
        }
    }

private fun ArtifactProbeState.tone(): String =
    when (this) {
        ArtifactProbeState.Exists -> "success"
        ArtifactProbeState.Missing -> "warning"
        ArtifactProbeState.NotReadable -> "danger"
        ArtifactProbeState.WrongType -> "danger"
        ArtifactProbeState.WorkspaceNotConfigured -> "muted"
        ArtifactProbeState.Unknown -> "muted"
    }
