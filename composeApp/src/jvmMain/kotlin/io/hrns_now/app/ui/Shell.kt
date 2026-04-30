package io.hrns_now.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.hrns_now.core.AppRoute
import io.hrns_now.infra.InfraMarker

val HrnsDarkColorScheme = androidx.compose.material3.darkColorScheme(
    background = Color(0xFF0B1016),
    surface = Color(0xFF121923),
    surfaceVariant = Color(0xFF18222D),
    primary = Color(0xFF8AC7FF),
    primaryContainer = Color(0xFF12324A),
    secondary = Color(0xFF82E6C7),
    secondaryContainer = Color(0xFF123D34),
    tertiary = Color(0xFFF5C26B),
    tertiaryContainer = Color(0xFF4A3110),
    error = Color(0xFFFF8A8A),
    errorContainer = Color(0xFF4A1E20),
    onBackground = Color(0xFFE6EEF6),
    onSurface = Color(0xFFE6EEF6),
    onSurfaceVariant = Color(0xFFB9C5D1),
    outline = Color(0xFF405160),
    outlineVariant = Color(0xFF2A3946),
    scrim = Color(0xFF000000),
)

@Composable
fun HrnsShell(
    selectedRoute: AppRoute,
    onRouteSelected: (AppRoute) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = HrnsDarkColorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopRibbon()
            HorizontalDivider(color = HrnsDarkColorScheme.outlineVariant)
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
                        .background(HrnsDarkColorScheme.outlineVariant),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ScreenRoute(selectedRoute)
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxSize()
                        .background(HrnsDarkColorScheme.outlineVariant),
                )
                InspectorPanel(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TopRibbon() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HrnsDarkColorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.widthIn(min = 160.dp)) {
            Text(
                text = "HRNS-NOW · v0",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "파일 우선 · 읽기 전용 투영 셸",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            InlineChips(
                values = listOf(
                    "작업공간: 미선택",
                    "하네스 엔진: 오프라인",
                    "저장소 연결: 미확인",
                    "실행 프로필: 기본",
                    "상태 점검: 대기",
                    "실행 중: 없음",
                ),
            )
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

    Column(
        modifier = modifier
            .background(HrnsDarkColorScheme.surface.copy(alpha = 0.94f))
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "작업 탐색",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun InspectorPanel(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .background(HrnsDarkColorScheme.surface.copy(alpha = 0.92f))
            .padding(20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "기준 파일 / 최신성",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        SectionCard(title = "오늘 상태 파일") {
            PlaceholderRow("WORKDAY_STATE.json", "WORKDAY_STATE.json · 없음")
        }

        SectionCard(title = "오늘 할 일 파일") {
            PlaceholderRow("TODAY_STRATEGY.md", "TODAY_STRATEGY.md · 없음")
        }

        SectionCard(title = "오늘 실행 로그") {
            PlaceholderRow("logs/YYYY-MM-DD/", "logs/YYYY-MM-DD/ · 없음")
        }

        SectionCard(title = "앱이 소유하지 않음") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("UI는 WORKDAY_STATE.json을 직접 쓰지 않습니다.")
                Text("UI는 마감 상태를 자체 확정하지 않습니다.")
                Text("현재 화면은 파일 기반 읽기 투영 셸입니다.")
            }
        }

        SectionCard(title = "기타") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("읽기 전용 투영 셸")
                Text("infra: ${InfraMarker.name}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
