package io.hrns_now.core.domain.model

import java.nio.file.Path
import java.time.LocalDate

/**
 * Phase 3에서 실제로 프로세스와 연결하는 command의 안정된 식별자다. 표시 label이 아니라
 * lock payload/로그 등 machine 값으로 쓰인다 (`doc/hrns_now_design_pattern.md` §5.3).
 */
enum class HarnessCommandKind {
    Doctor,
    ValidateOps,
}

/**
 * Harness PowerShell 실행을 위한 typed command다(`doc/hrns_now_design_pattern.md` §6). 문자열
 * 조립이 아니라 [io.hrns_now.core.port.HarnessCommandEncoder]류의 순수 encoder가 이 값을
 * argument 목록으로 변환한다.
 *
 * 이번 Phase에서 실제로 연결하는 command는 read-only `Doctor`/`ValidateOps` 둘뿐이다. Phase
 * 4/5(Planning/Replan/Execution/Closure 등 mutating command)를 여기서 선구현하지 않는다.
 */
sealed interface HarnessCommand {
    val kind: HarnessCommandKind

    /** `scripts/doctor.ps1 -Json`에 대응한다. */
    data class Doctor(
        val kitRoot: Path,
        val workspaceRoot: Path?,
        val projectRoot: Path?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.Doctor
    }

    /** `scripts/validate-ops.ps1 -Json`에 대응한다. `workspaceRoot`는 이 스크립트의 필수 인자다. */
    data class ValidateOps(
        val workspaceRoot: Path,
        val kitRoot: Path,
        val profile: String?,
        val date: LocalDate,
    ) : HarnessCommand {
        override val kind: HarnessCommandKind = HarnessCommandKind.ValidateOps
    }
}
