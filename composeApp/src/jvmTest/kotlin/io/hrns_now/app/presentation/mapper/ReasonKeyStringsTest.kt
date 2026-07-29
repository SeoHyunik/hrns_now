package io.hrns_now.app.presentation.mapper

import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.BlockedReasonKey
import io.hrns_now.core.domain.model.ProcessRunStatus
import io.hrns_now.core.domain.model.StateInvalidKind
import io.hrns_now.core.domain.model.StopReason
import io.hrns_now.core.domain.model.UnknownDomainKind
import io.hrns_now.core.domain.policy.ClosureBlockReasonKey
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [BlockedReasonKey]/[ClosureBlockReasonKey]는 typed key이므로 raw 원문을 절대 담을 수 없다
 * (Phase 8 보완 §1) — 이 테스트는 모든 case가 ko/en 둘 다에서 공백이 아닌 실제 문구를 내고,
 * 두 locale의 문구가 서로 다른지(=실제로 번역됐는지)만 확인한다.
 */
class ReasonKeyStringsTest {

    private val allBlockedReasonKeys: List<BlockedReasonKey> = listOf(
        BlockedReasonKey.ProjectNotConnected,
        BlockedReasonKey.DayNotSelected,
        BlockedReasonKey.StateInvalid(StateInvalidKind.NotYetRead),
        BlockedReasonKey.StateInvalid(StateInvalidKind.Missing),
        BlockedReasonKey.StateInvalid(StateInvalidKind.Malformed),
        BlockedReasonKey.StateInvalid(StateInvalidKind.EncodingError),
        BlockedReasonKey.StateInvalid(StateInvalidKind.UnsupportedSchema),
        BlockedReasonKey.StateInvalid(StateInvalidKind.AccessDenied),
        BlockedReasonKey.UnsupportedCompatibility,
        BlockedReasonKey.BoundaryInvalid,
        BlockedReasonKey.PastDateReadOnly,
        BlockedReasonKey.ProcessBusy(ProcessRunStatus.Locked),
        BlockedReasonKey.ProcessBusy(ProcessRunStatus.Running),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.Phase),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.Status),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.QueueStatus),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.QueueBlockedReason),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.ExecutionWrapper),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.StopReason),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.ArtifactReadiness),
        BlockedReasonKey.UnknownDomainValue(UnknownDomainKind.ActiveSliceKind),
        BlockedReasonKey.DispatchMetadataConflict,
        BlockedReasonKey.StopReasonBlocking(StopReason.UsageLimitBlocked),
        BlockedReasonKey.StopReasonBlocking(StopReason.ClaudeContextLimit),
        BlockedReasonKey.StopReasonBlocking(StopReason.ClaudeCallTimeout),
        BlockedReasonKey.StopReasonBlocking(StopReason.ClaudeResponseEmpty),
        BlockedReasonKey.StopReasonBlocking(StopReason.ClaudeResponseTooShort),
        BlockedReasonKey.StopReasonBlocking(StopReason.BudgetMaxTurns),
        BlockedReasonKey.StopReasonBlocking(StopReason.BudgetOrManualStop),
        BlockedReasonKey.StopReasonBlocking(StopReason.TransientClaudeOverloaded),
        BlockedReasonKey.StopReasonBlocking(StopReason.DispatchContractMismatch),
        BlockedReasonKey.StopReasonBlocking(StopReason.ManualPrerequisiteRequired),
        BlockedReasonKey.StopReasonBlocking(StopReason.RoleSlicedWrapperException),
        BlockedReasonKey.HumanActionRequired,
        BlockedReasonKey.ClosureValidationMismatch,
        BlockedReasonKey.ClosurePhaseMismatch,
        BlockedReasonKey.ExecutionCompletionMismatch,
        BlockedReasonKey.PlanningFailed,
        BlockedReasonKey.GenericExecutionBlocked,
        BlockedReasonKey.ExecutionContractUnclear,
        BlockedReasonKey.CodeSliceTargetMissing,
        BlockedReasonKey.DocSliceTargetMissing,
        BlockedReasonKey.ExecutionReadyUnknownSlice,
    )

    private val allClosureBlockReasonKeys: List<ClosureBlockReasonKey> = listOf(
        ClosureBlockReasonKey.StateInvalid(StateInvalidKind.Missing),
        ClosureBlockReasonKey.UnknownDomainValue(UnknownDomainKind.Phase),
        ClosureBlockReasonKey.RequiredArtifactsNotReady,
        ClosureBlockReasonKey.OpsValidationFailed,
        ClosureBlockReasonKey.ActiveSliceRemaining,
        ClosureBlockReasonKey.ResumeStepRemaining,
        ClosureBlockReasonKey.ClosureValidationFlagMismatch,
        ClosureBlockReasonKey.ClosureFieldConsistencyMismatch,
        ClosureBlockReasonKey.CleanHandoffConsistencyMismatch,
        ClosureBlockReasonKey.LockHeldByOtherRun,
    )

    @Test
    fun `모든 BlockedReasonKey는 ko en 둘 다에서 공백이 아닌 서로 다른 문구를 낸다`() {
        allBlockedReasonKeys.forEach { key ->
            val ko = key.toDisplayText(AppLocale.Korean)
            val en = key.toDisplayText(AppLocale.English)
            assertTrue(ko.isNotBlank(), "$key 의 한국어 문구가 비었다")
            assertTrue(en.isNotBlank(), "$key 의 영어 문구가 비었다")
            assertNotEquals(ko, en, "$key 의 ko/en 문구가 동일하다 — 번역되지 않았을 수 있다")
        }
    }

    @Test
    fun `모든 ClosureBlockReasonKey는 ko en 둘 다에서 공백이 아닌 서로 다른 문구를 낸다`() {
        allClosureBlockReasonKeys.forEach { key ->
            val ko = key.toDisplayText(AppLocale.Korean)
            val en = key.toDisplayText(AppLocale.English)
            assertTrue(ko.isNotBlank(), "$key 의 한국어 문구가 비었다")
            assertTrue(en.isNotBlank(), "$key 의 영어 문구가 비었다")
            assertNotEquals(ko, en, "$key 의 ko/en 문구가 동일하다 — 번역되지 않았을 수 있다")
        }
    }
}
