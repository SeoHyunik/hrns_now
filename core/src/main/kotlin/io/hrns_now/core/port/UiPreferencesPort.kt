package io.hrns_now.core.port

import io.hrns_now.core.domain.model.AppLocale

/**
 * UI 소유 설정(현재는 표시 언어만) 저장/복원 전용 포트다(새 Phase 8 §7). `WORKFLOW_STATE.json`,
 * Harness workspace, project Registry, RuntimeSource/lock 같은 다른 책임과 결합하지 않는다 —
 * God settings service가 되지 않도록 이 포트의 책임은 locale 하나로 제한한다.
 */
interface UiPreferencesPort {
    fun readLocale(): AppLocale?
    fun writeLocale(locale: AppLocale)
}
