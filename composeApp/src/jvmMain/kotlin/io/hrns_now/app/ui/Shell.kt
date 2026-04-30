package io.hrns_now.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.hrns_now.app.AppProjections
import io.hrns_now.core.AppRoute
import io.hrns_now.core.config.WorkspaceReadiness
import io.hrns_now.core.projection.ShellProjection
import io.hrns_now.core.projection.StatusChipModel
import io.hrns_now.infra.InfraMarker

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
            HorizontalDivider(color = colors.border)
            Row(modifier = Modifier.fillMaxSize()) {
                LeftRail(
                    selectedRoute = selectedRoute,
                    onRouteSelected = onRouteSelected,
                    modifier = Modifier
                        .width(224.dp)
                        .fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxSize()
                        .background(colors.border),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ScreenRoute(
                        route = selectedRoute,
                        setupProjection = projections.setup,
                        workspaceConfig = projections.workspaceConfig,
                        todayStatusProjection = projections.todayStatus,
                        todayWorkProjection = projections.todayWork,
                        runStatusProjection = projections.runStatus,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxSize()
                        .background(colors.border),
                )
                InspectorPanel(
                    projection = projections.shell,
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TopRibbon(
    projection: ShellProjection,
    readiness: WorkspaceReadiness,
    themeMode: HrnsThemeMode,
    onThemeToggle: () -> Unit,
) {
    val colors = LocalHrnsColors.current
    val themeLabel = when (themeMode) {
        HrnsThemeMode.Dark -> "화면: 어두운 모드"
        HrnsThemeMode.Light -> "화면: 밝은 모드"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panelBackground)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.widthIn(min = 160.dp)) {
            Text(
                text = projection.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primaryText,
            )
            Text(
                text = projection.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryText,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            InlineChips(
                chips = listOf(
                    StatusChipModel("작업공간", readiness.workspaceLabel),
                    StatusChipModel("하네스 엔진", readiness.engineLabel),
                    StatusChipModel("저장소 연결", readiness.bridgeLabel),
                    StatusChipModel("실행 프로필", readiness.profileLabel),
                    StatusChipModel("상태 점검", readiness.doctorLabel),
                    StatusChipModel("실행 중", "없음"),
                ),
            )
        }

        OutlinedButton(
            onClick = onThemeToggle,
            border = BorderStroke(1.dp, colors.accent),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.accentSoft,
                contentColor = colors.primaryText,
            ),
        ) {
            Text(themeLabel)
        }
    }
}

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
            .background(colors.panelBackground)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "작업 탐색",
            style = MaterialTheme.typography.labelLarge,
            color = colors.secondaryText,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        NavigationButton(
            text = "작업공간 연결",
            selected = selectedRoute == AppRoute.Setup,
            onClick = { onRouteSelected(AppRoute.Setup) },
        )
        NavigationButton(
            text = "오늘 현황",
            selected = selectedRoute == AppRoute.Cockpit,
            onClick = { onRouteSelected(AppRoute.Cockpit) },
        )
        NavigationButton(
            text = "오늘 할 일",
            selected = selectedRoute == AppRoute.Strategy,
            onClick = { onRouteSelected(AppRoute.Strategy) },
        )
        NavigationButton(
            text = "실행 현황",
            selected = selectedRoute == AppRoute.Run,
            onClick = { onRouteSelected(AppRoute.Run) },
        )
    }
}

@Composable
private fun InspectorPanel(
    projection: ShellProjection,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val colors = LocalHrnsColors.current

    Column(
        modifier = modifier
            .background(colors.panelBackground)
            .padding(20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "기준 파일 / 최신성",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
        )

        projection.sourceItems.forEach { item ->
            SectionCard(title = item.label) {
                PlaceholderRow(item.fileName, "${item.fileName} · ${item.stateLabel}")
            }
        }

        SectionCard(title = "앱이 소유하지 않음", warning = true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projection.notAppOwnedMessages.forEach { message ->
                    Text(message, color = colors.primaryText)
                }
            }
        }

        SectionCard(title = "기타") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(projection.subtitle)
                Text("infra: ${InfraMarker.name}", color = colors.secondaryText)
            }
        }
    }
}
