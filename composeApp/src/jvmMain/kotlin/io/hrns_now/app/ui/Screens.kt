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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.hrns_now.app.presentation.mapper.displayLabel
import io.hrns_now.app.presentation.mapper.retryLabel
import io.hrns_now.app.presentation.mapper.toRetryAction
import io.hrns_now.app.presentation.model.CockpitActionItem
import io.hrns_now.app.presentation.model.CockpitDiagnostics
import io.hrns_now.app.presentation.model.CockpitProjection
import io.hrns_now.app.presentation.model.HrnsUiEvent
import io.hrns_now.app.presentation.model.RegistryProjectItem
import io.hrns_now.app.presentation.model.WorkspaceDayItem
import io.hrns_now.app.presentation.model.RecoveryProjection
import io.hrns_now.app.presentation.model.RunStatusProjection
import io.hrns_now.app.presentation.model.SetupProjection
import io.hrns_now.app.presentation.model.TodayWorkProjection
import io.hrns_now.core.domain.model.RequestEntryDraft
import io.hrns_now.core.domain.model.RequestEntryPriority
import io.hrns_now.core.domain.model.RequestEntrySource
import io.hrns_now.core.domain.model.RequestEntryType
import io.hrns_now.core.domain.model.UiAction
import io.hrns_now.core.usecase.RegisterProjectCandidate

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
    recoveryProjection: RecoveryProjection,
    readiness: WorkspaceReadiness,
    onCockpitAction: (UiAction) -> Unit,
    registryProjects: List<RegistryProjectItem>,
    workspaceDays: List<WorkspaceDayItem>,
    activeProjectSourceLabel: String,
    registryMessage: String?,
    onUiEvent: (HrnsUiEvent) -> Unit,
) {
    when (route) {
        AppRoute.Setup -> SetupScreen(
            projection = setupProjection,
            workspaceConfig = workspaceConfig,
            workspaceProbeSummary = workspaceProbeSummary,
            readiness = readiness,
            registryProjects = registryProjects,
            workspaceDays = workspaceDays,
            selectedDayReadOnly = cockpitProjection.isReadOnlyDay,
            activeProjectSourceLabel = activeProjectSourceLabel,
            registryMessage = registryMessage,
            activeProjectName = cockpitProjection.projectName,
            activeProjectProfileLabel = cockpitProjection.profileLabel,
            activeProjectIsStale = cockpitProjection.isStale,
            activeProjectRuntimeSourceLabel = cockpitProjection.runtimeSourceLabel,
            activeProjectRuntimeDiagnostics = cockpitProjection.runtimeSourceDiagnostics,
            runStatusProjection = runStatusProjection,
            onUiEvent = onUiEvent,
        )
        AppRoute.Cockpit -> CockpitScreen(cockpitProjection, onCockpitAction)
        AppRoute.Strategy -> StrategyScreen(todayWorkProjection, runStatusProjection, onUiEvent)
        AppRoute.Run -> RunScreen(runStatusProjection, onUiEvent)
        AppRoute.Recovery -> RecoveryScreen(recoveryProjection, onUiEvent)
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
    registryProjects: List<RegistryProjectItem> = emptyList(),
    workspaceDays: List<WorkspaceDayItem> = emptyList(),
    selectedDayReadOnly: Boolean = false,
    activeProjectSourceLabel: String = "",
    registryMessage: String? = null,
    activeProjectName: String? = null,
    activeProjectProfileLabel: String = "",
    activeProjectIsStale: Boolean = false,
    activeProjectRuntimeSourceLabel: String = "",
    activeProjectRuntimeDiagnostics: CockpitDiagnostics? = null,
    runStatusProjection: RunStatusProjection? = null,
    onUiEvent: (HrnsUiEvent) -> Unit = {},
) {
    val colors = LocalHrnsColors.current

    ScreenContainer {
        ScreenHero(
            eyebrow = "01 · Project",
            title = projection.title,
            subtitle = projection.subtitle,
        )

        // 사용자가 화면을 열자마자 어느 프로젝트를 보고 있는지 알 수 있어야 한다(새 Phase 6 제품 목표 1).
        ActiveProjectSummaryCard(
            projectName = activeProjectName,
            profileLabel = activeProjectProfileLabel,
            isStale = activeProjectIsStale,
            runtimeSourceLabel = activeProjectRuntimeSourceLabel,
            runtimeSourceDiagnostics = activeProjectRuntimeDiagnostics,
            workspaceConfig = workspaceConfig,
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

        ProjectManagementSection(
            registryProjects = registryProjects,
            activeProjectSourceLabel = activeProjectSourceLabel,
            registryMessage = registryMessage,
            onUiEvent = onUiEvent,
        )

        WorkspaceDaySection(
            workspaceDays = workspaceDays,
            selectedDayReadOnly = selectedDayReadOnly,
            onUiEvent = onUiEvent,
        )

        SectionCard(title = "실행 작업", eyebrow = "Actions") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButtonGroup(projection.actions, onAction = { action -> onUiEvent(HrnsUiEvent.ActionRequested(action)) })
                Text(
                    text = projection.note,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.tertiaryText,
                )
                runStatusProjection?.let { HarnessRunFeedback(it, onUiEvent) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 활성 프로젝트 요약 — 이름·상태·핵심 경로를 화면 상단에서 즉시 식별(새 Phase 6)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ActiveProjectSummaryCard(
    projectName: String?,
    profileLabel: String,
    isStale: Boolean,
    runtimeSourceLabel: String,
    runtimeSourceDiagnostics: CockpitDiagnostics?,
    workspaceConfig: WorkspaceConfig,
) {
    val colors = LocalHrnsColors.current
    SectionCard(title = "활성 프로젝트", eyebrow = "Active project") {
        if (projectName == null) {
            Text(
                text = "선택된 프로젝트가 없습니다. 아래 프로젝트 관리에서 등록하거나 선택하세요.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = colors.tertiaryText,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = projectName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, letterSpacing = (-0.2).sp),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        text = if (isStale) "오래된 정보" else "정상",
                        tone = if (isStale) "warning" else "success",
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Runtime source",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = colors.tertiaryText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(
                        text = if (runtimeSourceDiagnostics != null) "$runtimeSourceLabel · 문제 있음" else runtimeSourceLabel,
                        tone = if (runtimeSourceDiagnostics != null) "danger" else "success",
                        showDot = false,
                    )
                }
                runtimeSourceDiagnostics?.let { diagnostics ->
                    Text(
                        text = "${diagnostics.whatHappened} ${diagnostics.nextStep}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                        color = colors.danger,
                    )
                }
                KeyValueGrid(
                    rows = listOf(
                        "프로필" to profileLabel,
                        "작업공간 경로" to (workspaceConfig.roots.workspaceRoot ?: "미설정"),
                        "저장소 경로" to (workspaceConfig.roots.projectRoot ?: "미설정"),
                    ),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 프로젝트 관리 — 등록·전환·삭제는 modal에서, 기본 화면은 활성 프로젝트만 보여준다(새 Phase 6)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProjectManagementSection(
    registryProjects: List<RegistryProjectItem>,
    activeProjectSourceLabel: String,
    registryMessage: String?,
    onUiEvent: (HrnsUiEvent) -> Unit,
) {
    val colors = LocalHrnsColors.current
    var showManagementModal by remember { mutableStateOf(false) }

    SectionCard(
        title = "프로젝트 관리",
        eyebrow = "Projects",
        trailing = {
            if (activeProjectSourceLabel.isNotBlank()) {
                StatusChip(text = "선택 근거: $activeProjectSourceLabel", tone = "accent", showDot = false)
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            registryMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    color = colors.secondaryText,
                )
            }

            if (registryProjects.isEmpty()) {
                // 프로젝트가 전혀 없을 때만 등록 온보딩을 이 화면에 직접 보여준다 — modal이 아니다.
                Text(
                    text = "등록된 프로젝트가 없습니다. 아래에서 새 프로젝트를 등록하세요.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = colors.tertiaryText,
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                ProjectRegistrationForm(onUiEvent = onUiEvent)
            } else {
                val active = registryProjects.firstOrNull { it.isActive }
                if (active != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = active.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, letterSpacing = (-0.1).sp),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primaryText,
                            modifier = Modifier.weight(1f),
                        )
                        StatusChip(text = "활성", tone = "success")
                    }
                } else {
                    Text(
                        text = "활성 프로젝트가 없습니다. 아래에서 프로젝트를 선택하세요.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = colors.tertiaryText,
                    )
                }
                PlaceholderActionButton(
                    text = "프로젝트 등록",
                    primary = true,
                    enabled = true,
                    onClick = { showManagementModal = true },
                )
            }
        }
    }

    if (showManagementModal) {
        ModalDialog(title = "프로젝트 관리", onDismissRequest = { showManagementModal = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    text = "등록된 프로젝트",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText,
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    registryProjects.forEach { project -> ProjectRow(project = project, onUiEvent = onUiEvent) }
                }
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.borderSubtle))
                ProjectRegistrationForm(onUiEvent = onUiEvent)
            }
        }
    }
}

@Composable
private fun WorkspaceDaySection(
    workspaceDays: List<WorkspaceDayItem>,
    selectedDayReadOnly: Boolean,
    onUiEvent: (HrnsUiEvent) -> Unit,
) {
    val colors = LocalHrnsColors.current
    SectionCard(title = "작업 날짜", eyebrow = "Workspace days") {
        if (workspaceDays.isEmpty()) {
            Text(
                text = "유효한 yyyy-MM-dd 날짜 폴더가 없습니다.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = colors.tertiaryText,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                workspaceDays.forEach { day ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = day.date.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                            ),
                            color = colors.primaryText,
                            modifier = Modifier.weight(1f),
                        )
                        if (day.isSelected && selectedDayReadOnly) {
                            StatusChip(text = "읽기 전용", tone = "muted")
                            Spacer(Modifier.width(8.dp))
                        }
                        PlaceholderActionButton(
                            text = if (day.isSelected) "선택됨" else "열기",
                            enabled = !day.isSelected,
                            onClick = { onUiEvent(HrnsUiEvent.WorkspaceDaySelected(day.date)) },
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun ProjectRow(project: RegistryProjectItem, onUiEvent: (HrnsUiEvent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(
                text = project.label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = (-0.1).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = LocalHrnsColors.current.primaryText,
            )
            Spacer(Modifier.width(8.dp))
            StatusChip(text = project.runtimeSourceLabel, tone = "muted", showDot = false)
            if (project.isActive) {
                Spacer(Modifier.width(8.dp))
                StatusChip(text = "활성", tone = "success")
            }
        }
        PlaceholderActionButton(
            text = "선택",
            enabled = !project.isActive,
            onClick = { onUiEvent(HrnsUiEvent.ProjectSelected(project.id)) },
        )
        Spacer(Modifier.width(8.dp))
        PlaceholderActionButton(
            text = "삭제",
            enabled = true,
            onClick = { onUiEvent(HrnsUiEvent.ProjectDeletionRequested(project.id)) },
        )
    }
}

/**
 * 표준 등록 흐름은 Kit 경로를 입력받지 않는다 — 기본은 `개발용 내장 SDK`이고, `고급 설정`을
 * 명시적으로 펼쳤을 때만 `외부 Harness Kit 사용` 토글과 경로 입력이 나타난다(새 Phase 7).
 * runtime source 선택 자체가 typed `RegisterProjectCandidate.useInternalDeveloperSdk`로만
 * 전달되며, 이 화면은 파일 존재 확인이나 경로 조립을 하지 않는다.
 */
@Composable
private fun ProjectRegistrationForm(onUiEvent: (HrnsUiEvent) -> Unit) {
    var displayName by remember { mutableStateOf("") }
    var workspaceRoot by remember { mutableStateOf("") }
    var repositoryRoot by remember { mutableStateOf("") }
    var profileId by remember { mutableStateOf("기본") }
    var showAdvanced by remember { mutableStateOf(false) }
    var useExternalKit by remember { mutableStateOf(false) }
    var kitRoot by remember { mutableStateOf("") }
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "새 프로젝트 등록",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
        )
        Text(
            text = "기본값은 개발용 내장 SDK(.local\\harness-kit)입니다. Kit 경로를 직접 입력할 필요가 없습니다.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
            color = colors.tertiaryText,
        )
        LabeledTextField(label = "표시명", value = displayName, onValueChange = { displayName = it })
        LabeledTextField(
            label = "Workspace root",
            value = workspaceRoot,
            onValueChange = { workspaceRoot = it },
            monospace = true,
        )
        LabeledTextField(
            label = "Repository root",
            value = repositoryRoot,
            onValueChange = { repositoryRoot = it },
            monospace = true,
        )
        LabeledTextField(label = "Profile", value = profileId, onValueChange = { profileId = it })

        PlaceholderActionButton(
            text = if (showAdvanced) "고급 설정 숨기기" else "고급 설정",
            enabled = true,
            onClick = { showAdvanced = !showAdvanced },
        )
        if (showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useExternalKit, onCheckedChange = { useExternalKit = it })
                    Text(
                        text = "외부 Harness Kit 사용",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = colors.primaryText,
                    )
                }
                if (useExternalKit) {
                    LabeledTextField(label = "Kit root", value = kitRoot, onValueChange = { kitRoot = it }, monospace = true)
                }
            }
        }

        PlaceholderActionButton(
            text = "진단 후 등록",
            primary = true,
            enabled = displayName.isNotBlank() && workspaceRoot.isNotBlank() &&
                repositoryRoot.isNotBlank() && profileId.isNotBlank() &&
                (!useExternalKit || kitRoot.isNotBlank()),
            onClick = {
                onUiEvent(
                    HrnsUiEvent.ProjectRegistrationRequested(
                        RegisterProjectCandidate(
                            displayName = displayName,
                            useInternalDeveloperSdk = !useExternalKit,
                            kitRootRaw = if (useExternalKit) kitRoot else null,
                            projectWorkspaceRootRaw = workspaceRoot,
                            repositoryRootRaw = repositoryRoot,
                            profileId = profileId,
                        ),
                    ),
                )
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cockpit (작업 현황)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CockpitScreen(
    projection: CockpitProjection,
    onAction: (UiAction) -> Unit,
) {
    ScreenContainer {
        ScreenHero(
            eyebrow = "02 · Today",
            title = "작업 현황" + (projection.projectName?.let { " · $it" } ?: ""),
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

        projection.runtimeSourceDiagnostics?.let { diagnostics ->
            CockpitDiagnosticsCard(
                title = "Runtime source 확인 (${projection.runtimeSourceLabel})",
                eyebrow = "Runtime source",
                diagnostics = diagnostics,
            )
        }

        projection.compatibilityDiagnostics?.let { diagnostics ->
            CockpitDiagnosticsCard(
                title = "Harness 호환성 확인",
                eyebrow = "Compatibility",
                diagnostics = diagnostics,
            )
        }

        projection.diagnostics?.let { diagnostics ->
            CockpitDiagnosticsCard(
                title = "확인이 필요합니다",
                eyebrow = "상태 진단",
                diagnostics = diagnostics,
            )
        }

        SectionCard(title = "현재 상태", eyebrow = "State") {
            KeyValueGrid(rows = cockpitStateRows(projection))
        }

        SectionCard(title = "기준 파일", eyebrow = "Artifacts") {
            InlineChips(chips = projection.artifactItems)
        }

        SectionCard(title = "다음 작업", eyebrow = "Next action") {
            CockpitActionButtonGroup(cockpitActions(projection), onAction)
        }
    }
}

@Composable
private fun CockpitDiagnosticsCard(
    title: String,
    eyebrow: String,
    diagnostics: CockpitDiagnostics,
) {
    SectionCard(title = title, eyebrow = eyebrow, warning = true) {
        KeyValueGrid(
            rows = listOf(
                "최근 작업 기록" to diagnostics.whatHappened,
                "마지막 정상 상태" to if (diagnostics.lastKnownGoodPreserved) {
                    "보존됨 (아래는 마지막 정상 값입니다)"
                } else {
                    "없음"
                },
                "다음 작업" to diagnostics.nextStep,
            ),
        )
    }
}

private fun cockpitStateRows(projection: CockpitProjection): List<Pair<String, String>> = buildList {
    add("프로필" to projection.profileLabel)
    add("작업 단계" to projection.phaseLabel)
    add("작업 상태" to projection.statusLabel)
    add("작업 대기열 상태" to projection.queueStatusLabel)
    add("현재 작업 카드" to (projection.activeCardId ?: "없음"))
    add("현재 작업 단위" to (projection.activeSliceId ?: "없음"))
    add("허용된 대상 파일" to (projection.authorizedTargetLabel ?: "없음"))
    projection.stopReasonLabel?.let { add("중단 사유" to it) }
    projection.blockedReasonLabel?.takeIf { it.isNotBlank() }?.let { add("차단 사유" to it) }
    add("작업 기준 점검" to projection.opsValidationLabel)
    add("마감 상태" to projection.closureLabel)
    add("실행 완료 여부" to projection.executionCompletedLabel)
    add("마지막 정상 상태 읽기" to (projection.lastSuccessfulReadAtLabel ?: "없음"))
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
fun StrategyScreen(
    projection: TodayWorkProjection,
    runStatusProjection: RunStatusProjection? = null,
    onUiEvent: (HrnsUiEvent) -> Unit = {},
) {
    val colors = LocalHrnsColors.current
    var showRequestModal by remember { mutableStateOf(false) }

    // 저장 성공은 feedback으로 알리고 modal을 닫는다 — 다음 저장을 위해 requestSaveSucceeded는
    // ViewModel이 다음 제출 시작 시점에 다시 false로 되돌린다.
    LaunchedEffect(projection.requestSaveSucceeded) {
        if (projection.requestSaveSucceeded) showRequestModal = false
    }

    ScreenContainer {
        ScreenHero(
            eyebrow = "03 · Plan",
            title = projection.title,
            subtitle = projection.subtitle,
            statusContent = { StatusChip(projection.statusChip) },
        )

        // 요구사항을 바로 추가해야 하는 사용자의 주 동작이므로 계획 상세보다 먼저 둔다.
        SectionCard(title = "요구사항 작성", eyebrow = "REQUEST_INBOX.md") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "REQUEST_INBOX.md에 새 요구사항을 기록합니다. 구조화된 계획 입력(REQUEST_STRUCTURED.md)은 별도로 다룹니다.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.tertiaryText,
                )
                projection.requestInboxNotice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = colors.accent,
                    )
                }
                PlaceholderActionButton(
                    text = "요구사항 작성",
                    primary = true,
                    enabled = projection.requestEditingEnabled,
                    onClick = { showRequestModal = true },
                )
                if (!projection.requestEditingEnabled) {
                    Text(
                        text = "현재 날짜 또는 상태에서는 요구사항을 작성할 수 없습니다. 새로고침한 뒤 다시 확인하세요.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.tertiaryText,
                    )
                }
            }
        }

        projection.sections.forEach { section ->
            ProjectionInfoCard(section)
        }

        SectionCard(title = "실행 작업", eyebrow = "Actions") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ActionButtonGroup(projection.actions, onAction = { action -> onUiEvent(HrnsUiEvent.ActionRequested(action)) })
                Text(
                    text = projection.note,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    ),
                    color = colors.tertiaryText,
                )
                runStatusProjection?.let { HarnessRunFeedback(it, onUiEvent) }
            }
        }
    }

    if (showRequestModal) {
        RequestEntryModal(
            saving = projection.requestSaving,
            editingEnabled = projection.requestEditingEnabled,
            clearDraftAfterSave = projection.requestSaveSucceeded,
            notice = projection.requestInboxNotice,
            onSubmit = { draft -> onUiEvent(HrnsUiEvent.RequestEntrySubmitted(draft)) },
            onDismiss = { showRequestModal = false },
        )
    }
}

/**
 * "요구사항 작성" modal editor다(새 Phase 6). 미저장 변경이 있는 상태에서 ESC/바깥 클릭/닫기
 * 버튼으로 닫으려 하면 먼저 확인을 요구한다 — [RequestInboxWriterAdapter]의 atomic write/optimistic
 * concurrency 계약은 이 화면이 바꾸지 않고 [onSubmit]으로만 위임한다.
 */
@Composable
private fun RequestEntryModal(
    saving: Boolean,
    editingEnabled: Boolean,
    clearDraftAfterSave: Boolean,
    notice: String?,
    onSubmit: (RequestEntryDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalHrnsColors.current
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RequestEntryType.Bug) }
    var source by remember { mutableStateOf(RequestEntrySource.Human) }
    var priority by remember { mutableStateOf(RequestEntryPriority.Unknown) }
    var summary by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var constraints by remember { mutableStateOf("") }
    var showUnsavedConfirm by remember { mutableStateOf(false) }

    val isDirty = title.isNotBlank() || summary.isNotBlank() || detail.isNotBlank() || constraints.isNotBlank()

    LaunchedEffect(clearDraftAfterSave) {
        if (clearDraftAfterSave) {
            title = ""
            summary = ""
            detail = ""
            constraints = ""
        }
    }

    fun attemptClose() {
        if (isDirty) showUnsavedConfirm = true else onDismiss()
    }

    ModalDialog(title = "요구사항 작성", onDismissRequest = ::attemptClose) {
        if (showUnsavedConfirm) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "저장하지 않은 변경사항이 있습니다. 닫으시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = colors.primaryText,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaceholderActionButton(
                        text = "계속 작성",
                        primary = true,
                        enabled = true,
                        onClick = { showUnsavedConfirm = false },
                    )
                    PlaceholderActionButton(
                        text = "저장하지 않고 닫기",
                        enabled = true,
                        onClick = onDismiss,
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "이 file은 raw 입력 영역입니다. 구조화된 계획 입력은 REQUEST_STRUCTURED.md에서 별도로 다룹니다.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = colors.tertiaryText,
                )
                LabeledTextField(label = "제목", value = title, onValueChange = { title = it })
                EnumOptionRow(
                    label = "유형",
                    options = RequestEntryType.entries,
                    selected = type,
                    optionLabel = { it.label },
                    onSelect = { type = it },
                )
                EnumOptionRow(
                    label = "출처",
                    options = RequestEntrySource.entries,
                    selected = source,
                    optionLabel = { it.label },
                    onSelect = { source = it },
                )
                EnumOptionRow(
                    label = "우선순위",
                    options = RequestEntryPriority.entries,
                    selected = priority,
                    optionLabel = { it.label },
                    onSelect = { priority = it },
                )
                LabeledTextField(label = "요약", value = summary, onValueChange = { summary = it })
                LabeledTextField(label = "상세", value = detail, onValueChange = { detail = it }, multiline = true)
                LabeledTextField(label = "제약", value = constraints, onValueChange = { constraints = it }, multiline = true)

                notice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = colors.accent,
                    )
                }

                val blockReason = when {
                    saving -> "저장 중입니다…"
                    !editingEnabled -> "현재 날짜 또는 상태에서는 요구사항을 저장할 수 없습니다."
                    title.isBlank() || summary.isBlank() -> "제목과 요약을 입력하세요."
                    else -> null
                }

                PlaceholderActionButton(
                    text = if (saving) "저장 중..." else "요구사항 저장",
                    primary = true,
                    enabled = editingEnabled && !saving && title.isNotBlank() && summary.isNotBlank(),
                    onClick = {
                        onSubmit(
                            RequestEntryDraft(
                                title = title,
                                type = type,
                                source = source,
                                priority = priority,
                                summary = summary,
                                detail = detail,
                                constraints = constraints,
                            ),
                        )
                    },
                )
                blockReason?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.tertiaryText,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 실행 feedback — 환경 점검/작업 기준 점검 등의 진행 중·성공·실패 인라인 표시(새 Phase 6)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 프로젝트 관리/작업 계획 화면 어디서든 재사용하는 인라인 실행 feedback이다. `RunStatusProjection`은
 * 실행 하나(harness 실행은 한 번에 하나만 허용)의 최신 상태만 담으므로, 여러 action 버튼이 이
 * 카드 하나를 공유해도 항상 실제로 진행 중이거나 마지막으로 실행된 action만 보여준다.
 */
@Composable
private fun HarnessRunFeedback(runStatus: RunStatusProjection, onUiEvent: (HrnsUiEvent) -> Unit) {
    val colors = LocalHrnsColors.current

    if (runStatus.isRunning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InlineSpinner()
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${runStatus.lastCommandKind?.displayLabel() ?: "실행"} 진행 중입니다…",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = colors.secondaryText,
            )
        }
        return
    }

    val kind = runStatus.lastCommandKind ?: return
    val outcome = runStatus.lastOutcome ?: return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(model = outcome)
            runStatus.lastCompletedAtLabel?.let { completedAt ->
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "완료 $completedAt",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.tertiaryText,
                )
            }
        }
        runStatus.lastSummaryLine?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                color = colors.secondaryText,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kind.toRetryAction()?.let { retryAction ->
                PlaceholderActionButton(
                    text = kind.retryLabel(),
                    enabled = true,
                    onClick = { onUiEvent(HrnsUiEvent.ActionRequested(retryAction)) },
                )
            }
            if (runStatus.cancelEnabled) {
                PlaceholderActionButton(
                    text = "실행 취소",
                    enabled = true,
                    onClick = { onUiEvent(HrnsUiEvent.HarnessRunCancelRequested) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Run (실행 기록)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RunScreen(projection: RunStatusProjection, onUiEvent: (HrnsUiEvent) -> Unit) {
    val colors = LocalHrnsColors.current

    ScreenContainer {
        ScreenHero(
            eyebrow = "04 · Run",
            title = projection.title,
            subtitle = projection.subtitle,
        )

        SectionCard(title = "실행 상세", eyebrow = "Details") {
            // 기본 화면에서는 접어 둔다 — 필요할 때만 펼쳐서 보는 저수준 상세 정보다(새 Phase 6).
            var stagesExpanded by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceholderActionButton(
                    text = if (stagesExpanded) "진행 단계 접기" else "진행 단계 보기",
                    enabled = true,
                    onClick = { stagesExpanded = !stagesExpanded },
                )
                if (stagesExpanded) {
                    projection.stages.forEachIndexed { index, stage ->
                        StageRow(index = index + 1, text = stage.displayText())
                    }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceholderActionButton(
                    text = "실행 취소",
                    enabled = projection.cancelEnabled,
                    onClick = { onUiEvent(HrnsUiEvent.HarnessRunCancelRequested) },
                )
                PlaceholderActionButton(
                    text = "잠금 강제 해제",
                    enabled = projection.forceReleaseEnabled,
                    onClick = { onUiEvent(HrnsUiEvent.LockForceReleaseRequested) },
                )
            }
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
// Recovery (복구 센터 · 마감 확인)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RecoveryScreen(projection: RecoveryProjection, onUiEvent: (HrnsUiEvent) -> Unit = {}) {
    val colors = LocalHrnsColors.current
    var incompleteHandoffAcknowledged by remember(projection.incompleteHandoffItems) { mutableStateOf(false) }

    ScreenContainer {
        ScreenHero(
            eyebrow = "05 · Recovery",
            title = projection.title,
            subtitle = projection.subtitle,
        )

        val card = projection.activeCard
        if (card != null) {
            SectionCard(title = card.title, eyebrow = "최근 작업 기록 · 보존된 기록 · 허용된 행동") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecoveryFact(label = "최근 작업 기록", value = card.whatHappened)
                    RecoveryFact(label = "보존된 기록", value = card.preservedRecord)
                    RecoveryFact(label = "현재 허용된 행동", value = card.allowedNextAction)
                }
            }
        } else {
            SectionCard(title = "복구가 필요한 문제 없음", eyebrow = "Recovery") {
                Text(
                    text = "현재 관측된 stop reason이나 queue blocked marker가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = colors.secondaryText,
                )
            }
        }

        SectionCard(title = "마감 체크리스트", eyebrow = "Closure") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InlineChips(chips = projection.closureChecklistRows)
                Text(
                    text = projection.closureNote,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = colors.secondaryText,
                )
                if (projection.incompleteHandoffItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        projection.incompleteHandoffItems.forEach { item ->
                            Text(
                                text = "• $item",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                ),
                                color = colors.tertiaryText,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = incompleteHandoffAcknowledged,
                                onCheckedChange = { incompleteHandoffAcknowledged = it },
                            )
                            Text(
                                text = "위 미완료 항목을 인지했으며 이 상태로 마감을 진행합니다.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                                color = colors.primaryText,
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = "읽기 전용 진단", eyebrow = "상태 진단") {
            KeyValueGrid(
                rows = listOf(
                    "연속성 진단" to projection.continuityDiagnosticsLabel,
                    "Usage ledger" to projection.usageLedgerLabel,
                    "실패 이력" to projection.failureHistoryLabel,
                    "마지막 정상 State" to projection.lastKnownGoodLabel,
                    "Harness 호환성" to projection.compatibilityLabel,
                    "잠금" to projection.lockLabel,
                ),
            )
        }

        SectionCard(title = "실행 작업", eyebrow = "Actions") {
            val gatedActions = projection.actions.map { action ->
                if (
                    action.action == UiAction.RunClosureValidation &&
                    projection.incompleteHandoffItems.isNotEmpty()
                ) {
                    action.copy(
                        enabled = projection.closureValidationEnabledAfterAcknowledgement &&
                            incompleteHandoffAcknowledged,
                    )
                } else {
                    action
                }
            }
            ActionButtonGroup(gatedActions, onAction = { action ->
                if (action == UiAction.RunClosureValidation) {
                    onUiEvent(HrnsUiEvent.ClosureValidationRequested(incompleteHandoffAcknowledged))
                } else {
                    onUiEvent(HrnsUiEvent.ActionRequested(action))
                }
            })
        }
    }
}

@Composable
private fun RecoveryFact(label: String, value: String) {
    val colors = LocalHrnsColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.secondaryText,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
            color = colors.primaryText,
        )
    }
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
