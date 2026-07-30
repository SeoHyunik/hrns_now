package io.hrns_now.core.domain.model

/**
 * live Harness Kit이 일반 프로젝트에 제공하는 기본 profile ID다.
 *
 * 화면에 보이는 "기본" 같은 번역 문구와 달리, 이 값은 `profiles/<id>.yaml`을 찾는
 * Harness 실행 계약이므로 저장·명령 전달에는 항상 실제 ID를 사용해야 한다.
 */
const val DEFAULT_HARNESS_PROFILE_ID: String = "corp-default"