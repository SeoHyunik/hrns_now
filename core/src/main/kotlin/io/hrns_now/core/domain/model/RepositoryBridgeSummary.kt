package io.hrns_now.core.domain.model

/** 개별 bridge 파일의 read-only 확인 결과다. write는 절대 하지 않는다. */
enum class BridgeFileState {
    Ready,
    Missing,
}

/**
 * `scripts/enter-project.ps1`이 만드는 최소 repository bridge 3종의 read-only 확인 결과다
 * (Harness Kit `docs/PROJECT_ONBOARDING.md` §4.1의 bridge 3-file 계약). Doctor/validate-ops는
 * bridge generator가 아니므로 이 값은 오직 [io.hrns_now.core.port.RepositoryBridgeProbePort]로만
 * 채워진다.
 */
data class RepositoryBridgeSummary(
    val settingsLocalJson: BridgeFileState,
    val projectClaudeMd: BridgeFileState,
    val toolsRunCycle: BridgeFileState,
) {
    val isReady: Boolean
        get() = settingsLocalJson == BridgeFileState.Ready &&
            projectClaudeMd == BridgeFileState.Ready &&
            toolsRunCycle == BridgeFileState.Ready
}
