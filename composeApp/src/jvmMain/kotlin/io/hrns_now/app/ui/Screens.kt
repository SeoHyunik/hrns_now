package io.hrns_now.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.hrns_now.core.AppRoute
import io.hrns_now.core.config.PathProbeResult
import io.hrns_now.core.config.PathProbeState
import io.hrns_now.core.config.WorkspaceConfig
import io.hrns_now.core.config.WorkspaceProbeSummary
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.RunStatusProjection
import io.hrns_now.app.presentation.model.SetupProjection
import io.hrns_now.app.presentation.model.TodayWorkProjection
import io.hrns_now.core.domain.model.UiAction

// ─────────────────────────────────────────────────────────────────────────────
// 라우터
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScreenRoute(
    route: AppRoute,
    setupProjection: SetupProjection,
    workspaceConfig: WorkspaceConfig,
    workspaceProbeSummary: WorkspaceProbeSummary,
    cockpitProjection: CockpitProjection,
    todayWorkProjection: TodayWorkProjection,
    runStatusProjection: RunStatusProjection,
    readiness: WorkspaceReadiness,
    onCockpitAction: (UiAction) -> Unit,
) {
    when (route) {
        AppRoute.Setup -> SetupScreen(setupProjection, workspaceConfig, workspaceProbeSummary, readiness)
        AppRoute.Cockpit -> CockpitScreen(cockpitProjection, onCockpitAction)
        AppRoute.Strategy -> StrategyScreen(todayWorkProjection)
        AppRoute.Run -> RunScreen(runStatusProjection)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 공용 헤더
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScreenHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    statusContent: (@Composable () -> Unit)? = null,
) {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.accent, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = eyebrow.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.6.sp,
                    fontSize = 11.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 36.sp,
                letterSpacing = (-1.0).sp,
                lineHeight = 42.sp,
            ),
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
            color = colors.secondaryText,
        )
        if (statusContent != null) {
            Spacer(Modifier.height(2.dp))
            statusContent()
        }
    }
}

@Composable
private fun ScreenContainer(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Setup
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SetupScreen(
    projection: SetupProjection,
    workspaceConfig: WorkspaceConfig,
    workspaceProbeSummary: WorkspaceProbeSummary,
    readiness: WorkspaceReadiness? = null,
) {
    val colors = LocalHrnsColors.current

    ScreenContainer {
        ScreenHero(
            eyebrow = "01 · Setup",
            title = projection.title,
            subtitle = projection.subtitle,
        )

        projection.cards.forEach { card ->
            ProjectionInfoCard(card)
        }

        SectionCard(title = "경로 점검", eyebrow = "Path probe") {
            Column {
                val rows = listOf(
                    workspaceProbeSummary.kitRoot,
                    workspaceProbeSummary.workspaceRoot,
                    workspaceProbeSummary.projectRoot,
                    workspaceProbeSummary.powerShellPath,
                    workspaceProbeSummary.claudeCommand,
                )
                rows.forEachIndexed { index, row ->
                    PathProbeRow(row)
                    if (index < rows.size) {
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.borderSubtle),
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "화면 언어",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            letterSpacing = (-0.1).sp,
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        text = workspaceConfig.runtime.uiLanguage,
                        tone = "accent",
                        showDot = false,
                    )
                }
            }
        }

        SectionCard(title = "실행 작업", eyebrow = "Actions") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButtonGroup(projection.actions)
                Text(
                    text = projection.note,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.tertiaryText,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cockpit (오늘 현황)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CockpitScreen(
    projection: CockpitProjection,
    onAction: (UiAction) -> Unit,
) {
    ScreenContainer {
        ScreenHero(
            eyebrow = "02 · Today",
            title = "오늘 현황" + (projection.projectName?.let { " · $it" } ?: ""),
            subtitle = projection.dateLabel,
            statusContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (projection.isStale) {
                        StatusChip(text = "오래된 정보", tone = "warning")
                    }
                    if (projection.isReadOnlyDay) {
                        StatusChip(text = "읽기 전용", tone = "muted")
                    }
                }
            },
        )

        projection.diagnostics?.let { diagnostics ->
            SectionCard(title = "확인이 필요합니다", eyebrow = "Diagnostics", warning = true) {
                KeyValueGrid(
                    rows = listOf(
                        "발생한 일" to diagnostics.whatHappened,
                        "이전 정상 기록" to if (diagnostics.lastKnownGoodPreserved) {
                            "보존됨 (아래는 마지막 정상 값입니다)"
                        } else {
                            "없음"
                        },
                        "다음 행동" to diagnostics.nextStep,
                    ),
                )
            }
        }

        SectionCard(title = "현재 상태", eyebrow = "State") {
            KeyValueGrid(rows = cockpitStateRows(projection))
        }

        SectionCard(title = "기준 파일", eyebrow = "Artifacts") {
            InlineChips(chips = projection.artifactItems)
        }

        SectionCard(title = "다음 행동", eyebrow = "Next action") {
            CockpitActionButtonGroup(cockpitActions(projection), onAction)
        }
    }
}

private fun cockpitStateRows(projection: CockpitProjection): List<Pair<String, String>> = buildList {
    add("Profile" to projection.profileLabel)
    add("단계 phase" to projection.phaseLabel)
    add("상태 status" to projection.statusLabel)
    add("큐 상태 queue" to projection.queueStatusLabel)
    add("현재 작업 카드" to (projection.activeCardId ?: "없음"))
    add("현재 작업 slice" to (projection.activeSliceId ?: "없음"))
    add("허용된 대상 파일" to (projection.authorizedTargetLabel ?: "없음"))
    projection.stopReasonLabel?.let { add("멈춘 이유 stop reason" to it) }
    projection.blockedReasonLabel?.takeIf { it.isNotBlank() }?.let { add("차단 사유" to it) }
    add("운영 검증 ops validation" to projection.opsValidationLabel)
    add("마감 closure" to projection.closureLabel)
    add("실행 완료 execution_completed" to projection.executionCompletedLabel)
    add("마지막 정상 읽기" to (projection.lastSuccessfulReadAtLabel ?: "없음"))
    add("마지막 읽기 시도" to (projection.lastAttemptAtLabel ?: "없음"))
}

/** typed action identity와 정책이 정한 enabled 상태를 보존하고 primary 하나만 앞에 둔다. */
private fun cockpitActions(projection: CockpitProjection): List<CockpitActionItem> = buildList {
    projection.primaryAction?.let { add(it) }
    addAll(projection.allowedActions.filter { it.action != projection.primaryAction?.action })
}

@Composable
private fun KeyValueGrid(rows: List<Pair<String, String>>) {
    val colors = LocalHrnsColors.current
    Column {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.borderSubtle),
                )
                Spacer(Modifier.height(14.dp))
            }
            PlaceholderRow(label, value)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Strategy (오늘 할 일)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StrategyScreen(projection: TodayWorkProjection) {
    val colors = LocalHrnsColors.current

    ScreenContainer {
        ScreenHero(
            eyebrow = "03 · Plan",
            title = projection.title,
            subtitle = projection.subtitle,
            statusContent = { StatusChip(projection.statusChip) },
        )

        projection.sections.forEach { section ->
            ProjectionInfoCard(section)
        }

        SectionCard(title = "실행 작업", eyebrow = "Actions") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButtonGroup(projection.actions)
                Text(
                    text = projection.note,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.tertiaryText,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Run (실행 현황)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RunScreen(projection: RunStatusProjection) {
    val colors = LocalHrnsColors.current

    ScreenContainer {
        ScreenHero(
            eyebrow = "04 · Run",
            title = projection.title,
            subtitle = projection.subtitle,
        )

        SectionCard(title = "역할별 진행 단계", eyebrow = "Stages") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projection.stages.forEachIndexed { index, stage ->
                    StageRow(index = index + 1, text = stage.displayText())
                }
            }
        }

        SectionCard(title = "실행 로그", eyebrow = "Console") {
            ConsoleBlock(lines = projection.consoleLines)
        }

        SectionCard(title = "단계 상세", eyebrow = "Detail") {
            KeyValueGrid(rows = projection.stageDetailRows)
        }

        SectionCard(title = "실패 유형", eyebrow = "Failures") {
            InlineChips(chips = projection.failureChips)
        }

        SectionCard(title = "실행 작업", eyebrow = "Actions") {
            ActionButtonGroup(projection.actions)
        }
    }
}

@Composable
private fun StageRow(index: Int, text: String) {
    val colors = LocalHrnsColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(colors.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                ),
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.5.sp,
                letterSpacing = (-0.1).sp,
            ),
            color = colors.primaryText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ConsoleBlock(lines: List<String>) {
    val colors = LocalHrnsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // 콘솔 헤더 (점 3개)
        Row(verticalAlignment = Alignment.CenterVertically) {
            ConsoleDot(colors.danger)
            Spacer(Modifier.width(6.dp))
            ConsoleDot(colors.warning)
            Spacer(Modifier.width(6.dp))
            ConsoleDot(colors.success)
            Spacer(Modifier.width(14.dp))
            Text(
                text = "console",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                ),
                color = colors.tertiaryText,
            )
        }
        Spacer(Modifier.height(4.dp))

        lines.forEach { line ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    color = colors.accent,
                    modifier = Modifier.width(18.dp),
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colors.primaryText,
                )
            }
        }
    }
}

@Composable
private fun ConsoleDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(color.copy(alpha = 0.85f), CircleShape),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Path probe row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PathProbeRow(result: PathProbeResult) {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = result.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = (-0.1).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                text = result.state.koreanLabel(),
                tone = result.state.tone(),
            )
        }
        val pathPart = result.rawPath?.takeIf { it.isNotBlank() }
        if (pathPart != null) {
            Text(
                text = pathPart,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                color = colors.secondaryText,
            )
        }
        if (result.message.isNotBlank()) {
            Text(
                text = result.message,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = colors.tertiaryText,
            )
        }
    }
}

private fun PathProbeState.koreanLabel(): String =
    when (this) {
        PathProbeState.NotConfigured -> "미설정"
        PathProbeState.Exists -> "확인됨"
        PathProbeState.Missing -> "없음"
        PathProbeState.NotReadable -> "읽기 불가"
        PathProbeState.WrongType -> "유형 불일치"
        PathProbeState.Unknown -> "확인 필요"
    }

private fun PathProbeState.tone(): String =
    when (this) {
        PathProbeState.Exists -> "success"
        PathProbeState.Missing -> "warning"
        PathProbeState.NotReadable -> "danger"
        PathProbeState.WrongType -> "danger"
        PathProbeState.NotConfigured -> "muted"
        PathProbeState.Unknown -> "muted"
    }

private fun io.hrns_now.app.presentation.model.StatusChipModel.displayText(): String =
    if (value.isBlank()) label else "$label · $value"
