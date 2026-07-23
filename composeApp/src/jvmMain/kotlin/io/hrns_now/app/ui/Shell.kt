package io.hrns_now.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hrns_now.app.AppProjections
import io.hrns_now.core.AppRoute
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.domain.model.ArtifactProbeResult
import io.hrns_now.core.domain.model.ArtifactProbeState
import io.hrns_now.core.domain.model.ArtifactRequirement
import io.hrns_now.core.domain.model.WorkspaceArtifactSummary
import io.hrns_now.core.projection.ShellProjection
import io.hrns_now.infra.InfraMarker

// ─────────────────────────────────────────────────────────────────────────────
// 최상위 Shell
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HrnsShell(
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
    projections: AppProjections,
    themeMode: HrnsThemeMode,
    onThemeToggle: () -> Unit,
) {
    val colors = LocalHrnsColors.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.appBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopRibbon(
                projection = projections.shell,
                readiness = projections.workspaceReadiness,
                themeMode = themeMode,
                onThemeToggle = onThemeToggle,
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
                        setupProjection = projections.setup,
                        workspaceConfig = projections.workspaceConfig,
                        workspaceProbeSummary = projections.workspaceProbeSummary,
                        todayStatusProjection = projections.todayStatus,
                        todayWorkProjection = projections.todayWork,
                        runStatusProjection = projections.runStatus,
                        readiness = projections.workspaceReadiness,
                    )
                }
                InspectorPanel(
                    projection = projections.shell,
                    artifactSummary = projections.workspaceArtifactSummary,
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 상단 리본  ──  brand · 페이지 라벨 · 준비 상태 요약 · 테마 토글
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TopRibbon(
    projection: ShellProjection,
    readiness: WorkspaceReadiness,
    themeMode: HrnsThemeMode,
    onThemeToggle: () -> Unit,
) {
    val colors = LocalHrnsColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.appBackground)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 브랜드
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
                Text(
                    text = projection.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.tertiaryText,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 준비 상태 요약 (1행, 가운데)
        ReadinessRibbon(readiness)

        Spacer(modifier = Modifier.weight(1f))

        // 테마 토글
        ThemeToggle(themeMode = themeMode, onClick = onThemeToggle)
    }
}

@Composable
private fun BrandMark() {
    val colors = LocalHrnsColors.current
    Box(
        modifier = Modifier
            .size(40.dp)
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
private fun ReadinessRibbon(readiness: WorkspaceReadiness) {
    val colors = LocalHrnsColors.current
    val items = listOf(
        "작업공간" to readiness.workspaceLabel,
        "엔진" to readiness.engineLabel,
        "저장소" to readiness.bridgeLabel,
        "프로필" to readiness.profileLabel,
        "점검" to readiness.doctorLabel,
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
                    text = value,
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
        v.contains("확인됨") || v.contains("준비") || v == "OK" -> c.success
        v.contains("없음") || v.contains("미설정") || v.contains("미선택") -> c.warning
        v.contains("불가") || v.contains("실패") || v.contains("불일치") -> c.danger
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

    Column(
        modifier = modifier
            .background(colors.appBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SidebarSectionLabel("작업 흐름")

        NavigationButton(
            text = "작업공간 연결",
            selected = selectedRoute == AppRoute.Setup,
            onClick = { onRouteSelected(AppRoute.Setup) },
            leadingGlyph = "01",
        )
        NavigationButton(
            text = "오늘 현황",
            selected = selectedRoute == AppRoute.Cockpit,
            onClick = { onRouteSelected(AppRoute.Cockpit) },
            leadingGlyph = "02",
        )
        NavigationButton(
            text = "오늘 할 일",
            selected = selectedRoute == AppRoute.Strategy,
            onClick = { onRouteSelected(AppRoute.Strategy) },
            leadingGlyph = "03",
        )
        NavigationButton(
            text = "실행 현황",
            selected = selectedRoute == AppRoute.Run,
            onClick = { onRouteSelected(AppRoute.Run) },
            leadingGlyph = "04",
        )

        Spacer(modifier = Modifier.height(28.dp))
        SidebarSectionLabel("안내")

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .fillMaxWidth(),
        ) {
            Text(
                text = "작업공간 경로와 기준 파일 존재 여부만 읽는 read-only 셸입니다.",
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
                text = "기준 파일",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    letterSpacing = (-0.5).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            )
            Text(
                text = "워크스페이스 안에서 추적 중인 항목들",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.tertiaryText,
            )
        }

        SectionCard(title = "아티팩트", eyebrow = "Artifacts") {
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

        SectionCard(
            title = "앱이 소유하지 않음",
            eyebrow = "Read-only",
            warning = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                projection.notAppOwnedMessages.forEach { message ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(4.dp)
                                .background(colors.warning, CircleShape),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 20.sp,
                            ),
                            color = colors.primaryText,
                        )
                    }
                }
            }
        }

        SectionCard(title = "환경", eyebrow = "Meta") {
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
            }
        }
    }
}

@Composable
private fun ArtifactRow(item: ArtifactProbeResult) {
    val colors = LocalHrnsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = (-0.1).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                text = item.state.koreanLabel(),
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

private fun ArtifactProbeState.koreanLabel(): String =
    when (this) {
        ArtifactProbeState.WorkspaceNotConfigured -> "미선택"
        ArtifactProbeState.Exists -> "확인됨"
        ArtifactProbeState.Missing -> "없음"
        ArtifactProbeState.NotReadable -> "읽기 불가"
        ArtifactProbeState.WrongType -> "유형 불일치"
        ArtifactProbeState.Unknown -> "확인 필요"
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
