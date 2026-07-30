package io.hrns_now.app.presentation.viewmodel

import io.hrns_now.core.domain.model.AppLocale
import io.hrns_now.core.domain.model.RuntimeIssue
import io.hrns_now.core.domain.model.RuntimeSource
import io.hrns_now.core.usecase.RegistrationRejectionReason
import java.time.LocalDate

/**
 * [AppViewModel]이 조립하는 registryMessage/알림/실행 notice 문구다(Phase 8 보완 §1). `core`가
 * 낸 typed 값(예: [RegistrationRejectionReason])만 소비하고, 이미 사람이 읽는 문자열로 굳어진
 * `message: String` 필드(예: `RegisterProjectResult.SaveFailed.message`)는 파일시스템/Registry
 * 어댑터가 이미 안전하게 만든 하위 오류 상세로만 그대로 이어붙인다 — session ID/secret/raw
 * process output은 여기서도 새로 노출하지 않는다.
 */

private fun runtimeSourceLabel(source: RuntimeSource, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> when (source) {
        RuntimeSource.InternalDeveloperSdk -> "개발용 내장 SDK"
        is RuntimeSource.ExternalKit -> "외부 Harness Kit"
    }
    AppLocale.English -> when (source) {
        RuntimeSource.InternalDeveloperSdk -> "internal developer SDK"
        is RuntimeSource.ExternalKit -> "external Harness Kit"
    }
}

/** [RegistrationRejectionReason]만으로 원인을 설명한다 — core의 raw message 문자열을 참조하지 않는다. */
fun registrationWhatHappenedText(reason: RegistrationRejectionReason, locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (reason) {
            RegistrationRejectionReason.BlankDisplayName -> "표시명을 입력하세요."
            RegistrationRejectionReason.BlankProfile -> "Profile을 입력하세요."
            RegistrationRejectionReason.BlankExternalKitPath -> "외부 Harness Kit 경로를 입력하세요."
            RegistrationRejectionReason.InvalidExternalKitPathFormat -> "Kit 경로 형식이 올바르지 않습니다."
            is RegistrationRejectionReason.RuntimeMissing -> when (val source = reason.source) {
                RuntimeSource.InternalDeveloperSdk -> "개발용 내장 SDK(.local\\harness-kit)를 찾을 수 없습니다."
                is RuntimeSource.ExternalKit -> "지정한 외부 Harness Kit 경로를 찾을 수 없습니다: ${source.root}"
            }
            is RegistrationRejectionReason.RuntimeInvalid -> {
                val reasonText = when (reason.issue) {
                    RuntimeIssue.NotDirectory -> "경로가 디렉터리가 아닙니다."
                    RuntimeIssue.NotReadable -> "경로를 읽을 수 없습니다."
                    RuntimeIssue.MissingEntrypoint -> "필요한 Harness 파일(doctor.ps1/validate-ops.ps1/run-cycle.ps1/kit-version.json)이 없습니다."
                }
                "${runtimeSourceLabel(reason.source, locale)} 확인 실패: $reasonText"
            }
        }

        AppLocale.English -> when (reason) {
            RegistrationRejectionReason.BlankDisplayName -> "Enter a display name."
            RegistrationRejectionReason.BlankProfile -> "Enter a profile."
            RegistrationRejectionReason.BlankExternalKitPath -> "Enter the external Harness Kit path."
            RegistrationRejectionReason.InvalidExternalKitPathFormat -> "The Kit path format is invalid."
            is RegistrationRejectionReason.RuntimeMissing -> when (val source = reason.source) {
                RuntimeSource.InternalDeveloperSdk -> "The internal developer SDK (.local\\harness-kit) couldn't be found."
                is RuntimeSource.ExternalKit -> "The specified external Harness Kit path couldn't be found: ${source.root}"
            }
            is RegistrationRejectionReason.RuntimeInvalid -> {
                val reasonText = when (reason.issue) {
                    RuntimeIssue.NotDirectory -> "The path isn't a directory."
                    RuntimeIssue.NotReadable -> "The path couldn't be read."
                    RuntimeIssue.MissingEntrypoint -> "Required Harness files (doctor.ps1/validate-ops.ps1/run-cycle.ps1/kit-version.json) are missing."
                }
                "${runtimeSourceLabel(reason.source, locale)} check failed: $reasonText"
            }
        }
    }

fun registrationInvalidCandidateRegistryMessage(reason: RegistrationRejectionReason, locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> "등록할 수 없습니다: ${registrationWhatHappenedText(reason, locale)}"
        AppLocale.English -> "Can't register: ${registrationWhatHappenedText(reason, locale)}"
    }

fun registrationNextStepGuidance(reason: RegistrationRejectionReason, locale: AppLocale): String =
    when (locale) {
        AppLocale.Korean -> when (reason) {
            RegistrationRejectionReason.BlankDisplayName -> "표시명을 입력하세요."
            RegistrationRejectionReason.BlankProfile -> "Profile을 입력하세요."
            RegistrationRejectionReason.BlankExternalKitPath -> "고급 설정에서 외부 Harness Kit 경로를 입력하세요."
            RegistrationRejectionReason.InvalidExternalKitPathFormat -> "고급 설정에서 Kit 경로 형식을 다시 확인하세요."
            is RegistrationRejectionReason.RuntimeMissing -> if (reason.source == RuntimeSource.InternalDeveloperSdk) {
                "개발용 SDK(.local\\harness-kit)를 준비하거나 고급 설정을 열어 외부 Harness Kit을 선택하세요."
            } else {
                "고급 설정에서 외부 Harness Kit 경로를 다시 확인하세요."
            }
            is RegistrationRejectionReason.RuntimeInvalid -> if (reason.source == RuntimeSource.InternalDeveloperSdk) {
                "개발용 SDK 내용을 확인하거나 고급 설정을 열어 외부 Harness Kit을 선택하세요."
            } else {
                "고급 설정에서 외부 Harness Kit 경로 내용을 다시 확인하세요."
            }
        }

        AppLocale.English -> when (reason) {
            RegistrationRejectionReason.BlankDisplayName -> "Enter a display name."
            RegistrationRejectionReason.BlankProfile -> "Enter a profile."
            RegistrationRejectionReason.BlankExternalKitPath -> "Enter the external Harness Kit path in advanced settings."
            RegistrationRejectionReason.InvalidExternalKitPathFormat -> "Recheck the Kit path format in advanced settings."
            is RegistrationRejectionReason.RuntimeMissing -> if (reason.source == RuntimeSource.InternalDeveloperSdk) {
                "Prepare the developer SDK (.local\\harness-kit), or open advanced settings to select an external Harness Kit."
            } else {
                "Recheck the external Harness Kit path in advanced settings."
            }
            is RegistrationRejectionReason.RuntimeInvalid -> if (reason.source == RuntimeSource.InternalDeveloperSdk) {
                "Check the developer SDK contents, or open advanced settings to select an external Harness Kit."
            } else {
                "Recheck the external Harness Kit path contents in advanced settings."
            }
        }
    }

fun registrationBoundaryRejectedRegistryMessage(violationCount: Int, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "등록할 수 없습니다: 경로 경계 조건을 확인하세요 (${violationCount}건 위반)."
    AppLocale.English -> "Can't register: check the path boundary conditions ($violationCount violation(s))."
}

fun registrationBoundaryRejectedWhatHappened(violationCount: Int, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "작업공간·저장소·Kit 경로가 서로 겹치거나 일치합니다 (${violationCount}건 위반)."
    AppLocale.English -> "The workspace/repository/Kit paths overlap or match each other ($violationCount violation(s))."
}

fun registrationBoundaryRejectedNextStep(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "각 경로가 서로 다른 위치를 가리키는지 확인한 뒤 다시 등록하세요."
    AppLocale.English -> "Confirm each path points to a different location, then register again."
}

fun onboardingLockFailedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "온보딩 진단 잠금을 안전하게 획득하지 못해 Registry를 저장하지 않았습니다."
    AppLocale.English -> "Didn't save to the registry because the onboarding diagnostic lock couldn't be acquired safely."
}

fun onboardingLockFailedWhatHappened(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "온보딩 진단 잠금을 안전하게 획득하지 못했습니다."
    AppLocale.English -> "The onboarding diagnostic lock couldn't be acquired safely."
}

fun onboardingLockFailedNextStep(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "다른 HRNS-NOW 실행이 끝난 뒤 다시 시도하세요."
    AppLocale.English -> "Try again after the other HRNS-NOW run finishes."
}

fun onboardingProcessObserveFailed(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "온보딩 진단 프로세스를 안전하게 관찰하지 못했습니다."
    AppLocale.English -> "Couldn't safely observe the onboarding diagnostic process."
}

/** Phase 10: `enter-project`(프로젝트 준비) 실행 자체를 관찰하지 못했을 때 쓴다. */
fun projectOnboardingProcessObserveFailedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트 준비 프로세스를 안전하게 관찰하지 못했습니다."
    AppLocale.English -> "Couldn't safely observe the project preparation process."
}

/**
 * Phase 10: enter-project 종료·validate-ops overall·bridge probe·4-file probe·State 재조회 중
 * 하나 이상이 충족되지 않아 "준비됨"으로 판정할 수 없을 때 쓰는 안전한 안내다. 어떤 근거가
 * 부족했는지의 raw 세부사항은 담지 않는다 — 재시도 CTA로 안내한다.
 */
fun onboardingIncompleteNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean ->
        "프로젝트 준비가 아직 완전히 끝나지 않았습니다. 프로젝트 관리 화면에서 \"프로젝트 준비\"를 다시 시도하세요."
    AppLocale.English ->
        "Project preparation hasn't finished completely yet. Try \"Prepare project\" again from project management."
}

fun doctorFailedRegistryMessage(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "연결 점검을 통과하지 못해 Registry를 저장하지 않았습니다. 실행 기록에서 결과를 확인하세요."
    AppLocale.English -> "Didn't save to the registry because the connection check didn't pass. Check the result in run history."
}

fun doctorFailedWhatHappened(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "연결 점검(Doctor)을 통과하지 못했습니다."
    AppLocale.English -> "The connection check (Doctor) didn't pass."
}

fun doctorFailedNextStep(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "실행 기록에서 상세 결과를 확인한 뒤 경로와 설정을 점검하세요."
    AppLocale.English -> "Check the detailed result in run history, then review the paths and settings."
}

fun compatibilityFailedRegistryMessage(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Harness 호환성을 확인하지 못해 Registry를 저장하지 않았습니다."
    AppLocale.English -> "Didn't save to the registry because Harness compatibility couldn't be confirmed."
}

fun compatibilityFailedWhatHappened(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Harness 버전 호환성을 확인하지 못했습니다."
    AppLocale.English -> "Harness version compatibility couldn't be confirmed."
}

fun compatibilityFailedNextStep(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Harness Kit 버전을 확인하세요."
    AppLocale.English -> "Check the Harness Kit version."
}

fun projectRegisteredNotification(name: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "'$name' 프로젝트를 등록했습니다."
    AppLocale.English -> "Registered project '$name'."
}

fun projectRegisteredRefreshMessage(name: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Doctor·호환성 확인 후 '$name' 프로젝트를 등록했습니다."
    AppLocale.English -> "Registered project '$name' after the doctor/compatibility check."
}

fun projectRegisteredButNotFoundMessage(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트는 저장됐지만 다시 찾을 수 없습니다."
    AppLocale.English -> "The project was saved but couldn't be found again."
}

fun projectRegisteredButSelectSaveFailedMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트는 저장됐지만 활성 선택을 기록하지 못했습니다: $detail"
    AppLocale.English -> "The project was saved but the active selection couldn't be recorded: $detail"
}

fun registrySaveFailedRegistryMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Registry 저장 실패: $detail"
    AppLocale.English -> "Registry save failed: $detail"
}

fun registrySaveFailedWhatHappened(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Registry 저장에 실패했습니다: $detail"
    AppLocale.English -> "Registry save failed: $detail"
}

fun registrySaveFailedNextStep(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "잠시 후 다시 시도하세요."
    AppLocale.English -> "Try again shortly."
}

fun projectSelectedMessage(name: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "'$name' 프로젝트를 선택했습니다."
    AppLocale.English -> "Selected project '$name'."
}

fun projectNotFoundMessage(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "선택한 프로젝트를 찾을 수 없습니다."
    AppLocale.English -> "The selected project couldn't be found."
}

fun projectSelectSaveFailedMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트 선택을 저장하지 못했습니다: $detail"
    AppLocale.English -> "Couldn't save the project selection: $detail"
}

fun projectDeletedMessage(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트를 삭제했습니다."
    AppLocale.English -> "Deleted the project."
}

fun projectDeleteFailedMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트를 삭제하지 못했습니다: $detail"
    AppLocale.English -> "Couldn't delete the project: $detail"
}

/** Phase 9 QA03-A: 해제는 등록된 project entry를 지우지 않는다 — 문구도 삭제와 분명히 구분한다. */
fun activeProjectReleasedMessage(projectName: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "'$projectName' 프로젝트를 해제했습니다. 등록 정보는 그대로 남아 있습니다."
    AppLocale.English -> "Released '$projectName'. Its registry entry is still there."
}

fun activeProjectReleaseFailedMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트 해제를 저장하지 못했습니다: $detail"
    AppLocale.English -> "Couldn't save the project release: $detail"
}

/** Phase 9 QA03-B: Bootstrap이 끝났지만 재조회한 State가 Success가 아닐 때 쓰는 안전한 안내다. */
fun workspacePreparationNotConfirmedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "등록은 완료됐지만, 오늘 작업공간 준비 결과를 아직 확인하지 못했습니다. 작업 계획 화면에서 다시 확인하세요."
    AppLocale.English -> "Registration is complete, but today's workspace preparation result isn't confirmed yet. Check the Plan screen again."
}

fun workspacePreparationFailedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "등록은 완료됐지만, 오늘 작업공간 준비 실행이 실패했습니다. 작업 계획 화면에서 다시 시도하세요."
    AppLocale.English -> "Registration is complete, but preparing today's workspace failed. Try again from the Plan screen."
}

fun dayFolderNotFoundMessage(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "선택한 날짜 폴더를 찾을 수 없습니다."
    AppLocale.English -> "The selected date folder couldn't be found."
}

fun daySelectedMessage(date: LocalDate, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$date 날짜를 선택했습니다."
    AppLocale.English -> "Selected date $date."
}

fun daySelectedButSaveFailedMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "날짜는 열었지만 마지막 선택을 저장하지 못했습니다: $detail"
    AppLocale.English -> "Opened the date, but couldn't save the last selection: $detail"
}

fun registryRecoveredFromCorruptionMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Registry 손상을 복구했습니다. $detail"
    AppLocale.English -> "Recovered from registry corruption. $detail"
}

fun registryUnreadableMessage(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Registry를 읽을 수 없습니다: $detail"
    AppLocale.English -> "The registry couldn't be read: $detail"
}

fun closureValidationBlockedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "현재 Closure 조건에서는 마감 검증을 실행할 수 없습니다. 복구 센터의 checklist를 확인하세요."
    AppLocale.English -> "Closure validation can't run under the current closure conditions. Check the checklist in the recovery center."
}

fun harnessRunNotAllowedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "현재 상태에서 허용되지 않은 실행입니다. 상태를 새로고침한 뒤 권장 행동을 확인하세요."
    AppLocale.English -> "This run isn't allowed in the current state. Refresh, then check the recommended action."
}

fun externalExecutionSuspectedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "WORKFLOW_STATE.json의 외부 변경 가능성이 감지되어 새 실행을 보류했습니다. 새로고침으로 다시 확인하세요."
    AppLocale.English -> "A possible external change to WORKFLOW_STATE.json was detected, so the new run was held back. Refresh to check again."
}

fun harnessRunObserveFailedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "Harness 실행 프로세스를 안전하게 관찰하지 못했습니다."
    AppLocale.English -> "Couldn't safely observe the Harness run process."
}

/** [io.hrns_now.core.usecase.ExecuteHarnessActionOutcome.Rejected]의 typed reasonKey가 null일 때만 쓰는 fallback이다. */
fun rejectedFallbackNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "현재 상태에서 허용되지 않은 실행입니다."
    AppLocale.English -> "This run isn't allowed in the current state."
}

fun lockBusyNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "다른 HRNS-NOW 실행이 이 프로젝트와 날짜의 잠금을 보유 중입니다."
    AppLocale.English -> "Another HRNS-NOW run holds the lock for this project and date."
}

fun lockFailedNotice(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "실행 잠금을 안전하게 획득하지 못했습니다: $detail"
    AppLocale.English -> "Couldn't safely acquire the run lock: $detail"
}

fun unsupportedActionNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "현재 Phase에서 지원하지 않는 실행 action입니다."
    AppLocale.English -> "This run action isn't supported in the current phase."
}

fun runCompletedNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업이 완료되었습니다."
    AppLocale.English -> "$label completed."
}

fun runNeedsReviewNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업 결과를 확인하세요."
    AppLocale.English -> "Check the result of $label."
}

fun runNotCompletedNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업을 완료하지 못했습니다."
    AppLocale.English -> "$label didn't complete."
}

fun runRejectedNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업을 지금 실행할 수 없습니다."
    AppLocale.English -> "$label can't run right now."
}

fun runFailedNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업에 실패했습니다."
    AppLocale.English -> "$label failed."
}

fun runLockUnavailableNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업의 실행 잠금을 획득하지 못했습니다."
    AppLocale.English -> "Couldn't acquire the run lock for $label."
}

fun runUnsupportedNotification(label: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "$label 작업은 현재 지원하지 않습니다."
    AppLocale.English -> "$label isn't supported right now."
}

fun registrationFailureNotification(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "프로젝트 등록을 완료하지 못했습니다."
    AppLocale.English -> "Couldn't complete project registration."
}

fun pastDayReadOnlyNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "과거 날짜는 읽기 전용입니다. 오늘 날짜에서만 요청을 저장할 수 있습니다."
    AppLocale.English -> "Past dates are read-only. Requests can only be saved on today's date."
}

fun requestEditingNotAllowedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "현재 상태에서는 요청을 저장할 수 없습니다. 상태를 새로고침한 뒤 허용된 다음 작업을 확인하세요."
    AppLocale.English -> "Requests can't be saved in the current state. Refresh, then check the next allowed action."
}

fun requestSavedNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "요청을 저장했습니다."
    AppLocale.English -> "Saved the request."
}

fun requestInboxUnavailableNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "요청 입력 파일을 찾을 수 없습니다. 먼저 '오늘 작업 시작'을 실행하세요."
    AppLocale.English -> "The request inbox file couldn't be found. Run 'Start today's work' first."
}

fun requestConflictNotice(locale: AppLocale): String = when (locale) {
    AppLocale.Korean ->
        "다른 곳에서 파일이 변경되어 저장하지 못했습니다. 초안은 유지됩니다. 다시 시도하면 최신 내용을 다시 읽으며, 필요하면 REQUEST_INBOX.md를 확인해 수동 병합하세요."
    AppLocale.English ->
        "Couldn't save because the file changed elsewhere. Your draft is kept. Retrying will re-read the latest content — check REQUEST_INBOX.md and merge manually if needed."
}

fun requestFailedNotice(detail: String, locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "요청을 저장하지 못했습니다: $detail"
    AppLocale.English -> "Couldn't save the request: $detail"
}

fun requestSavedNotification(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "요구사항을 저장했습니다."
    AppLocale.English -> "Saved the requirement."
}

fun requestNotSavedNotification(locale: AppLocale): String = when (locale) {
    AppLocale.Korean -> "요구사항을 저장하지 못했습니다."
    AppLocale.English -> "Couldn't save the requirement."
}
