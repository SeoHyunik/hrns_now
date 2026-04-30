package io.hrns_now.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.hrns_now.core.AppRoute
import io.hrns_now.core.projection.RunStatusProjection
import io.hrns_now.core.projection.SetupProjection
import io.hrns_now.core.projection.TodayStatusProjection
import io.hrns_now.core.projection.TodayWorkProjection

@Composable
fun ScreenRoute(
    route: AppRoute,
    setupProjection: SetupProjection,
    todayStatusProjection: TodayStatusProjection,
    todayWorkProjection: TodayWorkProjection,
    runStatusProjection: RunStatusProjection,
) {
    when (route) {
        AppRoute.Setup -> SetupScreen(setupProjection)
        AppRoute.Cockpit -> CockpitScreen(todayStatusProjection)
        AppRoute.Strategy -> StrategyScreen(todayWorkProjection)
        AppRoute.Run -> RunScreen(runStatusProjection)
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String) {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.secondaryText,
        )
    }
}

@Composable
fun SetupScreen(projection: SetupProjection) {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = projection.title,
            subtitle = projection.subtitle,
        )

        projection.cards.forEach { card ->
            ProjectionInfoCard(card)
        }

        SectionCard(title = "실행 작업") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButtonGroup(projection.actions)
                Text(
                    text = projection.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }
        }
    }
}

@Composable
fun CockpitScreen(projection: TodayStatusProjection) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = projection.title,
            subtitle = projection.subtitle,
        )

        SectionCard(title = "현재 상태") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                projection.stateRows.forEach { (label, value) ->
                    PlaceholderRow(label, value)
                }
            }
        }

        SectionCard(title = "현재 작업 카드") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                projection.activeCardRows.forEach { (label, value) ->
                    PlaceholderRow(label, value)
                }
            }
        }

        SectionCard(title = "역할별 실행 상태") {
            InlineChips(chips = projection.roleStages)
        }

        SectionCard(title = "바로 실행") {
            ActionButtonGroup(projection.actions)
        }
    }
}

@Composable
fun StrategyScreen(projection: TodayWorkProjection) {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = projection.title,
            subtitle = projection.subtitle,
        )

        StatusChip(projection.statusChip)

        projection.sections.forEach { section ->
            ProjectionInfoCard(section)
        }

        SectionCard(title = "실행 작업") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButtonGroup(projection.actions)
                Text(
                    text = projection.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }
        }
    }
}

@Composable
fun RunScreen(projection: RunStatusProjection) {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = projection.title,
            subtitle = projection.subtitle,
        )

        SectionCard(title = "역할별 진행 단계") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                projection.stages.forEach { stage ->
                    StageRow(stage.displayText())
                }
            }
        }

        SectionCard(title = "실행 로그") {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    projection.consoleLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primaryText,
                        )
                    }
                }
            }
        }

        SectionCard(title = "단계 상세") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                projection.stageDetailRows.forEach { (label, value) ->
                    PlaceholderRow(label, value)
                }
            }
        }

        SectionCard(title = "실패 유형") {
            InlineChips(chips = projection.failureChips)
        }

        SectionCard(title = "실행 작업") {
            ActionButtonGroup(projection.actions)
        }
    }
}

@Composable
private fun StageRow(text: String) {
    val colors = LocalHrnsColors.current

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.cardBackground),
        border = BorderStroke(1.dp, colors.border),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.primaryText,
        )
    }
}

private fun io.hrns_now.core.projection.StatusChipModel.displayText(): String =
    if (value.isBlank()) label else "$label: $value"
