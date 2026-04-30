package io.hrns_now.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.hrns_now.core.AppRoute

@Composable
fun ScreenRoute(route: AppRoute) {
    when (route) {
        AppRoute.Setup -> SetupScreen()
        AppRoute.Cockpit -> CockpitScreen()
        AppRoute.Strategy -> StrategyScreen()
        AppRoute.Run -> RunScreen()
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
fun SetupScreen() {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = "작업공간 연결",
            subtitle = "프로젝트와 harness-kit 실행 환경을 안전하게 연결합니다.",
        )

        SectionCard(title = "하네스 엔진") {
            PlaceholderRow("상태", "하네스 엔진: 연결되지 않음")
        }

        SectionCard(title = "작업공간") {
            PlaceholderRow("상태", "작업공간: 선택되지 않음")
        }

        SectionCard(title = "저장소 연결") {
            PlaceholderRow("상태", "저장소 연결: 아직 검사하지 않음")
        }

        SectionCard(title = "실행 프로필") {
            PlaceholderRow("상태", "실행 프로필: 기본")
        }

        SectionCard(title = "실행 작업") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceholderActionButton("상태 점검 실행", primary = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaceholderActionButton("프로젝트 연결")
                    Spacer(modifier = Modifier.width(8.dp))
                    PlaceholderActionButton("프로젝트 파악 실행")
                    Spacer(modifier = Modifier.width(8.dp))
                    PlaceholderActionButton("브리프 확정")
                }
                Text(
                    text = "PS1 실행 연결은 다음 단계에서 추가합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryText,
                )
            }
        }
    }
}

@Composable
fun CockpitScreen() {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = "오늘 현황",
            subtitle = "오늘의 작업 상태를 기준 파일과 작업 산출물 기준으로 읽어 보여줍니다.",
        )

        SectionCard(title = "현재 상태") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceholderRow("current_phase", "알 수 없음")
                PlaceholderRow("current_status", "not_loaded")
                PlaceholderRow("active_card", "없음")
                PlaceholderRow("작업 완료 execution_completed", "false")
                PlaceholderRow("마감 완료 closure_validated", "false")
            }
        }

        SectionCard(title = "현재 작업 카드") {
            Text(
                text = "현재 연결된 작업 카드가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.secondaryText,
            )
        }

        SectionCard(title = "역할별 실행 상태") {
            InlineChips(
                values = listOf(
                    "경로 확인 navi",
                    "작업 수행 worker",
                    "검토 reviewer",
                    "기록 정리 dockeeper",
                    "상태 확정 parent",
                ),
            )
        }

        SectionCard(title = "바로 실행") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceholderActionButton("다음 할 일 정리", primary = true)
                PlaceholderActionButton("선택된 코드 작업 실행")
                PlaceholderActionButton("마감 검증 실행")
            }
        }
    }
}

@Composable
fun StrategyScreen() {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = "오늘 할 일",
            subtitle = "선택된 단일 작업과 수동 실행 조건을 명확히 보여줍니다.",
        )

        StatusChip(
            text = "코드 실행 수동 대기 code_execution_held",
        )

        SectionCard(title = "현재 선택된 작업") {
            PlaceholderRow("작업", "선택되지 않음")
        }

        SectionCard(title = "실행 방식 Execution wrapper") {
            PlaceholderRow("형태", "Execution wrapper: 미정")
        }

        SectionCard(title = "허용된 대상 파일 Authorized target file") {
            PlaceholderRow("대상", "Authorized target file: 없음")
        }

        SectionCard(title = "멈춘 이유 Stop reason") {
            PlaceholderRow("이유", "Stop reason: 없음")
        }

        SectionCard(title = "이번 작업 범위") {
            Text("현재 작업 범위가 아직 정리되지 않았습니다.", color = colors.secondaryText)
        }

        SectionCard(title = "금지 범위") {
            Text("기준 파일 외 수정은 아직 허용되지 않습니다.", color = colors.secondaryText)
        }

        SectionCard(title = "확인 기준") {
            Text("수동 실행은 이후 PS1 façade에 연결됩니다.", color = colors.secondaryText)
        }

        SectionCard(title = "실행 작업") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceholderActionButton("선택된 코드 작업 실행", primary = true)
                PlaceholderActionButton("할 일 다시 정리")
            }
        }
    }
}

@Composable
fun RunScreen() {
    val colors = LocalHrnsColors.current

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenTitle(
            title = "실행 현황",
            subtitle = "활성 wrapper 실행과 역할별 실행 패킷 상태를 확인합니다.",
        )

        SectionCard(title = "역할별 진행 단계") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StageRow("경로 확인 navi")
                StageRow("작업 수행 worker")
                StageRow("검토 reviewer")
                StageRow("기록 정리 dockeeper")
                StageRow("상태 확정 parent")
            }
        }

        SectionCard(title = "실행 로그") {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = colors.cardBackground),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Text(
                    text = "[idle] 활성 wrapper 실행이 없습니다.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.primaryText,
                )
            }
        }

        SectionCard(title = "단계 상세") {
            Text(
                text = "역할별 실행 패킷과 응답 로그는 다음 단계에서 연결됩니다.",
                color = colors.secondaryText,
            )
        }

        SectionCard(title = "실패 유형") {
            InlineChips(
                values = listOf(
                    "Claude 사용량 대기 usage_limit_blocked",
                    "턴 예산 초과 budget_max_turns",
                    "래퍼 오류 wrapper_exception",
                    "패킷 형식 오류 packet_contract_failed",
                    "상태 확정 실패 state_finalization_failed",
                    "신규 파일 경로 처리 실패 new_target_path_failed",
                ),
            )
        }

        SectionCard(title = "실행 작업") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaceholderActionButton("실행 패킷 열기", primary = true)
                PlaceholderActionButton("응답 로그 열기")
                PlaceholderActionButton("조건 확인 후 재시도")
            }
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
