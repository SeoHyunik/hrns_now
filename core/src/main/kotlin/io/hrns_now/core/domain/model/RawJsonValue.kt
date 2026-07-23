package io.hrns_now.core.domain.model

/**
 * 상세 계약이 아직 확정되지 않은 중첩 JSON 값을 안전하게 보존하는 wrapper다.
 *
 * `current_slice`, `slice_queue`, `role_sliced`, `usage_guard`는 harness-kit에서
 * 계속 진화 중인 진단/실행 세부 구조이며, live fixture 한 건만으로 전체 shape을
 * 확정해 typed model을 만들면 실제 계약과 다른 구조를 창작할 위험이 있다. 이 필드들은
 * Phase 1A의 목표(phase/status/queue/stop reason/artifact/validation/closure 추출)에
 * 직접 소비되지 않으므로 JSON 텍스트로 보존해 다음 Phase가 필요할 때 정식 typed model을
 * 설계할 수 있게 한다. 단, Harness state에 포함될 수 있는 raw session ID·token·secret은
 * infra의 ACL sanitizer가 반드시 치환한 뒤 이 타입으로 전달한다.
 *
 * `core`는 kotlinx.serialization을 모르므로 이 타입은 순수 String만 보유한다 — 실제
 * 안전한 JSON 텍스트로의 변환은 `infra.serialization.RawJsonValueSanitizer`가 수행한다.
 */
data class RawJsonValue(val text: String)
