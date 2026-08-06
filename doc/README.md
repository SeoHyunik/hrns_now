# HRNS-NOW 문서 안내

이 디렉터리는 현재 production source를 설명하는 문서와 아직 닫히지 않은 검증 Gate만 유지한다. 완료된 구현 Phase의 실행 프롬프트와 시점별 보고서는 Git 이력에서 조회하며, 현재 계약의 근거로 사용하지 않는다.

## 문서 권위

충돌 시 다음 순서로 판단한다.

1. 현재 `core`, `infra`, `composeApp` production source와 실행 결과
2. 현재 live Harness Kit의 public contract와 실제 생성 artifact
3. [현행 계획과 계약](./hrns_now_claude_plan.md)
4. [Kotlin 아키텍처 규범](./hrns_now_design_pattern.md)
5. 현재 검증 보고서
6. 사용자 QA 메모와 패키징 초안

테스트 성공이나 과거 `PASS` 판정은 production-to-production 계약 검증을 대체하지 않는다.

## 현재 상태

기준일은 2026-08-06이다.

| 영역 | 상태 | 근거 |
|---|---|---|
| 애플리케이션 구조 | 구현됨 | Hexagonal Architecture, typed command/policy, MVVM/UDF, CQRS-lite |
| 기본 자동 테스트 | 통과 | 강제 재실행 기준 `core` 141, `infra` 174, `composeApp` 122 — 총 437 |
| live Kit 진단 | 통과 | 등록된 외부 Kit에서 Doctor 167 checks, Validate-Ops 19 checks, 각각 `overall=ok` |
| live Kit 호환성 | **차단 결함 확인** | fresh onboarding State에 UI 보장 필드 `required_next_action`이 없어 HRNS production parser가 fail-closed할 수 있음 |
| Native UI QA | 대기 | 호환성 blocker 해결 후 실제 사용자 클릭·캡처 필요 |
| Windows MSI lifecycle | 대기 | clean Windows 설치→표준 cycle→제거 증거 미완료 |
| Bundled Harness Runtime | 차단 | owner가 승인한 immutable runtime artifact·manifest·checksum이 없음 |

live Kit 호환성은 [감사 프롬프트](./claude_prompts/harness-kit-live-compatibility-audit.md)로 전수 재검증한다. 보고서가 생성되면 `phase_reports/harness-kit-live-compatibility-audit-report.md`가 현재 판정의 직접 근거가 된다.

## 현재 유지 문서

- [현행 계획과 계약](./hrns_now_claude_plan.md) — 제품 불변식, 현재 구현, 우선순위, Gate, State/CTA 계약
- [Kotlin 아키텍처 규범](./hrns_now_design_pattern.md) — 계층, 패턴, port/adapter, 실행 lifecycle, 테스트 원칙
- [Native QA 체크리스트](./native_qa_checklist.md) — 실제 사용자 상호작용과 증거 요구사항
- [Live Harness Kit 호환성 감사 프롬프트](./claude_prompts/harness-kit-live-compatibility-audit.md) — 현재 `D:\harness-kit` 전수 감사 절차
- [현재 검증 보고서](./phase_reports/README.md) — 아직 유효한 현재 Gate 보고서만 유지

다음 파일은 사용자 작업 자료이며 production 계약의 정본이 아니다. 존재할 경우 명시적 요청 없이 수정·삭제·stage하지 않는다.

- `hrns_now_packaging_plan.md` — 패키징 초안
- `user_workflow_qa_notes.md` — 사용자 QA 관찰 메모
- `QA_captures/` — 사용자 캡처

## 핵심 계약 요약

### State와 파일

- runtime truth는 `WORKFLOW_STATE.json` 하나다.
- UI는 `WORKFLOW_STATE.json`을 직접 쓰지 않는다.
- 오늘 required surface는 정확히 다음 4개다.

```text
REQUEST_INBOX.md
TODAY_STRATEGY.md
DAILY_HANDOFF.md
WORKFLOW_STATE.json
```

- `REQUEST_STRUCTURED.md`는 optional이다.
- `WORK_QUEUE.json`, `WORKDAY_STATE.json`은 명시적 legacy compatibility 외에는 readiness 근거가 아니다.
- repository bridge는 정확히 다음 3개다.

```text
.claude/settings.local.json
.claude/CLAUDE.md
tools/run-cycle.ps1
```

### 실행

- 모든 실행은 typed `HarnessCommand`에서 argument list로 encode한다.
- planning과 replan은 별도 lane이다.
- execution wrapper 외부 계약은 `none|code|doc|auto`다. UI는 active slice가 명시한 `code` 또는 `doc`만 dispatch한다.
- closure validation은 실제 `run-cycle.ps1 -ValidateForClosure`를 사용한다.
- 프로세스 종료 후 lock을 보유한 채 State를 다시 읽고, 그 뒤 lock을 해제한다.
- malformed, unknown, unsupported schema, 경계 불명확성은 fail-closed한다.

### 경계와 보안

- Harness Kit, external workspace, repository는 서로 같거나 포함 관계이면 안 된다.
- junction/symlink를 고려한 real path 비교가 가능하지 않으면 안전한 쓰기를 허용하지 않는다.
- Registry와 lock은 Harness workspace 밖의 앱 소유 경로에 둔다.
- raw session ID, token, secret, raw response, raw log는 Registry와 UI projection에 저장하지 않는다.
- Harness Runtime을 MSI에 포함하려면 owner-approved immutable artifact가 먼저 있어야 한다.

## 문서 관리 규칙

- 현재 source를 설명하지 않는 일회성 작업 프롬프트는 완료 후 삭제한다.
- 시점별 테스트 결과를 새 보고서로 계속 누적하지 않는다. 현재 Gate에 필요한 보고서만 유지한다.
- 반복되는 계약은 이 파일, 현행 계획, 아키텍처 규범 중 한 곳에만 정본으로 두고 다른 문서는 링크한다.
- 코드 심볼·entrypoint·파일명·test count를 변경하면 관련 현행 문서를 같은 변경에서 갱신한다.
- 새로운 감사 보고서가 완료되면 finding과 미해결 Gate만 현행 계획에 반영하고, 보고서 자체는 다음 판정으로 대체될 때까지 유지한다.
