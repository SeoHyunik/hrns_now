package io.hrns_now.core.port

import io.hrns_now.core.domain.model.RuntimeResolution
import io.hrns_now.core.domain.model.RuntimeSource

/**
 * [RuntimeSource]를 실제 root로 안전하게 해석하는 의미만 표현하는 port다
 * (`doc/hrns_now_design_pattern.md` §20.1). repository-relative 개발용 SDK 경로 계산이나
 * 임의 파일 생성·복사는 이 interface가 아니라 구현체(infra adapter/composition root)만 안다.
 * [io.hrns_now.core.port.KitVersionManifestPort]와 같은 이유로 `suspend`가 아니다 — 순수 조회
 * 함수이며 호출자가 IO dispatcher 안에서 호출해야 한다.
 */
fun interface RuntimeSourceResolverPort {
    fun resolve(source: RuntimeSource): RuntimeResolution
}
