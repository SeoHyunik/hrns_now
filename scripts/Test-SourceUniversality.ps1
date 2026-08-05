#requires -Version 5.1

<#
.SYNOPSIS
Regression-prevention scanner: fails if development-history residue (Phase/Patch/QA
numbering, internal review-tool references, retired symbols, personal host paths) reappears
in hrns_now product source, build config, or CI config.
#>

param(
    [AllowEmptyString()][string]$RepositoryRoot = '',
    [switch]$SelfTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepositoryRoot)) {
    $RepositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}

function Test-HrnsCommentLine {
    param([string]$Line)
    $trimmed = $Line.TrimStart()
    return $trimmed.StartsWith('//') -or $trimmed.StartsWith('*') -or $trimmed.StartsWith('/*') -or $trimmed.StartsWith('#')
}

function Get-HrnsSourceUniversalityContentRules {
    $koreanDeveloper = -join ([char[]]@(0xAC1C, 0xBC1C, 0xC6A9))
    $koreanBundled = -join ([char[]]@(0xB0B4, 0xC7A5))
    $developerSdkPattern = '(?i)\b(?:internal\s+)?developer\s+SDK\b|' +
        [regex]::Escape($koreanDeveloper) + '\s*(?:' +
        [regex]::Escape($koreanBundled) + '\s*)?SDK'

    @(
        [pscustomobject]@{ category = 'numbered_development_phase'; pattern = '(?i)\bPhase\s*[0-9]+[A-Za-z0-9.-]*'; comment_only = $false }
        [pscustomobject]@{ category = 'numbered_development_patch'; pattern = '(?i)\bPatch\s*[0-9]+[A-Za-z0-9.-]*'; comment_only = $false }
        [pscustomobject]@{ category = 'qa_round_marker'; pattern = '(?i)\bQA\s*[0-9]{1,3}[A-Za-z-]*'; comment_only = $false }
        [pscustomobject]@{ category = 'internal_review_status'; pattern = '(?i)\bCodex\b|\bPASS_WITH_FIXES\b|\bREADY_FOR_CODEX_REVIEW\b'; comment_only = $false }
        [pscustomobject]@{ category = 'historical_doc_normative_citation'; pattern = '(?i)doc[\\/]claude_prompts[\\/]phase[0-9]|doc[\\/]phase_reports[\\/]phase[0-9]'; comment_only = $false }
        [pscustomobject]@{ category = 'retired_symbol'; pattern = '(?i)\bInternalDeveloperSdk\b|\bDeveloperSdkRuntimeResolver\b|\buseInternalDeveloperSdk\b|\binternalSdkRootProvider\b|\bdefaultInternalSdkRoot\b|\bPlaceholderRow\b|\bPlaceholderActionButton\b|\bJVMPlatform\b|\bInfraMarker\b'; comment_only = $false }
        [pscustomobject]@{ category = 'developer_sdk_term'; pattern = $developerSdkPattern; comment_only = $false }
        [pscustomobject]@{ category = 'personal_identifier'; pattern = '(?i)\bauziraum\b|\btest-hantu\b|\bhos0917\b|C:[\\/]Users[\\/]LG\b|S:[\\/]dev[\\/]project\b'; comment_only = $false }
        [pscustomobject]@{ category = 'personal_dev_host_path'; pattern = 'D:[\\/]harness-kit'; comment_only = $true }
    )
}

function Get-HrnsSourceUniversalityFilenameRules {
    @(
        [pscustomobject]@{ category = 'numbered_development_phase'; pattern = '(?i)(?:^|[^A-Za-z0-9])Phase\s*[0-9]+[A-Za-z0-9.-]*' }
        [pscustomobject]@{ category = 'numbered_development_patch'; pattern = '(?i)(?:^|[^A-Za-z0-9])Patch\s*[0-9]+[A-Za-z0-9.-]*' }
        [pscustomobject]@{ category = 'qa_round_marker'; pattern = '(?i)(?:^|[^A-Za-z0-9])QA\s*[0-9]{1,3}[A-Za-z-]*' }
        [pscustomobject]@{ category = 'internal_review_status'; pattern = '(?i)(?:^|[^A-Za-z0-9])Codex(?:[^A-Za-z0-9]|$)|PASS_WITH_FIXES|READY_FOR_CODEX_REVIEW' }
        [pscustomobject]@{ category = 'personal_identifier'; pattern = '(?i)(?:^|[^A-Za-z0-9])(?:auziraum|test-hantu|hos0917)(?:[^A-Za-z0-9]|$)' }
    )
}

function Get-HrnsSourceUniversalityRetiredFilenames {
    @('Greeting.kt', 'Platform.kt', 'InfraMarker.kt', 'ComposeAppCommonTest.kt', 'DeveloperSdkRuntimeResolver.kt', 'DeveloperSdkRuntimeResolverTest.kt', 'Invoke-Phase6ACleanWindowsSmoke.ps1')
}

function Get-HrnsSourceUniversalityScanScope {
    param([string]$RepoRoot)

    $scopeRelative = @(
        '.github',
        'build.gradle.kts',
        'settings.gradle.kts',
        'composeApp/build.gradle.kts',
        'core/build.gradle.kts',
        'infra/build.gradle.kts',
        'composeApp/src',
        'core/src',
        'infra/src',
        'scripts'
    )
    $files = @()
    foreach ($relative in $scopeRelative) {
        $path = Join-Path $RepoRoot $relative
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            $files += Get-Item -LiteralPath $path
        } elseif (Test-Path -LiteralPath $path -PathType Container) {
            $files += Get-ChildItem -LiteralPath $path -Recurse -File -Force
        }
    }

    $textExtensions = @('.kt', '.kts', '.yml', '.yaml', '.ps1')
    return @($files | Where-Object {
        $textExtensions -contains $_.Extension.ToLowerInvariant() -and
        $_.FullName -notmatch '[\\/](build|\.gradle|\.gradle-user|\.idea|\.kotlin)[\\/]'
    } | Sort-Object FullName -Unique)
}

function Get-HrnsSourceUniversalityRelativePath {
    param([string]$FullPath, [string]$RepoRootFull)
    return $FullPath.Substring($RepoRootFull.TrimEnd('\', '/').Length).TrimStart('\', '/').Replace('\', '/')
}

function Invoke-HrnsSourceUniversalityScan {
    param([string]$RepoRoot, [string]$SelfPath = '')

    $repoRootFull = [System.IO.Path]::GetFullPath($RepoRoot)
    $files = Get-HrnsSourceUniversalityScanScope -RepoRoot $repoRootFull
    $rules = Get-HrnsSourceUniversalityContentRules
    $filenameRules = Get-HrnsSourceUniversalityFilenameRules
    $retiredNames = Get-HrnsSourceUniversalityRetiredFilenames

    $findings = @()

    foreach ($file in $files) {
        $relativePath = Get-HrnsSourceUniversalityRelativePath -FullPath $file.FullName -RepoRootFull $repoRootFull

        if ($retiredNames -contains $file.Name) {
            $findings += [pscustomobject]@{ category = 'retired_filename'; path = $relativePath; line = 0 }
        }
        foreach ($filenameRule in $filenameRules) {
            if ([regex]::IsMatch($relativePath, [string]$filenameRule.pattern)) {
                $findings += [pscustomobject]@{
                    category = ('filename_' + [string]$filenameRule.category)
                    path = $relativePath
                    line = 0
                }
            }
        }

        $isSelf = (-not [string]::IsNullOrWhiteSpace($SelfPath)) -and
            ([System.IO.Path]::GetFullPath($file.FullName) -eq [System.IO.Path]::GetFullPath($SelfPath))
        if ($isSelf) { continue }

        $lines = [System.IO.File]::ReadAllLines($file.FullName, [System.Text.Encoding]::UTF8)
        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            foreach ($rule in $rules) {
                if ([bool]$rule.comment_only -and -not (Test-HrnsCommentLine -Line $line)) { continue }
                if ([regex]::IsMatch($line, [string]$rule.pattern)) {
                    $findings += [pscustomobject]@{ category = [string]$rule.category; path = $relativePath; line = ($i + 1) }
                }
            }
        }
    }

    return $findings
}

function Invoke-HrnsSourceUniversalitySelfTest {
    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('hrns-source-universality-selftest-' + [guid]::NewGuid().ToString('N'))
    $exitCode = 1
    try {
        $forbiddenDir = Join-Path $tempRoot 'composeApp/src/jvmMain/kotlin/io/hrns_now/app'
        [void][System.IO.Directory]::CreateDirectory($forbiddenDir)
        $forbiddenFile = Join-Path $forbiddenDir 'SelfTestForbidden.kt'
        $forbiddenContent = @'
package io.hrns_now.app

// Phase 9 QA03-A: legacy comment referencing an old QA round.
// Patch21: temporary migration note.
// Codex 보정: this line should never survive review.
// PASS_WITH_FIXES marker left behind by a review process.
// see doc/claude_prompts/phase9-workflow.md for historical instructions.
// auziraum is a developer-specific fixture.
// see D:\harness-kit\docs\PROJECT_ONBOARDING.md for more.
// developer SDK is a retired product term.
class SelfTestForbidden {
    val useInternalDeveloperSdk = true
    fun placeholder() = PlaceholderRow("a", "b")
}
'@
        [System.IO.File]::WriteAllText($forbiddenFile, $forbiddenContent, [System.Text.UTF8Encoding]::new($false))

        $forbiddenFilename = Join-Path $forbiddenDir 'Phase12-Patch21-QA10-Codex-auziraum.kt'
        [System.IO.File]::WriteAllText(
            $forbiddenFilename,
            'package io.hrns_now.app',
            [System.Text.UTF8Encoding]::new($false)
        )

        $protectedDir = Join-Path $tempRoot 'core/src/main/kotlin/io/hrns_now/core/domain/model'
        [void][System.IO.Directory]::CreateDirectory($protectedDir)
        $protectedFile = Join-Path $protectedDir 'SelfTestProtected.kt'
        $protectedContent = @'
package io.hrns_now.core.domain.model

/** state.current_phase의 typed 표현이다. */
sealed interface WorkflowPhase {
    data object PlanningRequired : WorkflowPhase
}

private const val RUNTIME_SOURCE_DEFAULT_KIT_WIRE_VALUE = "internal_developer_sdk"

class SelfTestProtected {
    val samplePath = "D:/harness-kit/scripts/doctor.ps1"
}
'@
        [System.IO.File]::WriteAllText($protectedFile, $protectedContent, [System.Text.UTF8Encoding]::new($false))

        $findings = @(Invoke-HrnsSourceUniversalityScan -RepoRoot $tempRoot -SelfPath '')

        $forbiddenRelative = 'composeApp/src/jvmMain/kotlin/io/hrns_now/app/SelfTestForbidden.kt'
        $forbiddenFilenameRelative = 'composeApp/src/jvmMain/kotlin/io/hrns_now/app/Phase12-Patch21-QA10-Codex-auziraum.kt'
        $protectedRelative = 'core/src/main/kotlin/io/hrns_now/core/domain/model/SelfTestProtected.kt'

        $forbiddenFindings = @($findings | Where-Object { $_.path -eq $forbiddenRelative })
        $forbiddenFilenameFindings = @($findings | Where-Object { $_.path -eq $forbiddenFilenameRelative })
        $protectedFindings = @($findings | Where-Object { $_.path -eq $protectedRelative })

        $expectedCategories = @(
            'numbered_development_phase',
            'numbered_development_patch',
            'qa_round_marker',
            'internal_review_status',
            'historical_doc_normative_citation',
            'retired_symbol',
            'developer_sdk_term',
            'personal_identifier',
            'personal_dev_host_path'
        )
        $expectedFilenameCategories = @(
            'filename_numbered_development_phase',
            'filename_numbered_development_patch',
            'filename_qa_round_marker',
            'filename_internal_review_status',
            'filename_personal_identifier'
        )
        $foundCategories = @($forbiddenFindings | ForEach-Object { $_.category } | Select-Object -Unique)
        $missingCategories = @($expectedCategories | Where-Object { $foundCategories -notcontains $_ })
        $foundFilenameCategories = @($forbiddenFilenameFindings | ForEach-Object { $_.category } | Select-Object -Unique)
        $missingFilenameCategories = @(
            $expectedFilenameCategories | Where-Object { $foundFilenameCategories -notcontains $_ }
        )

        Write-Host ("forbidden sample findings: {0}" -f $forbiddenFindings.Count)
        Write-Host ("forbidden filename findings: {0}" -f $forbiddenFilenameFindings.Count)
        Write-Host ("protected sample findings: {0} (expected 0)" -f $protectedFindings.Count)
        if ($missingCategories.Count -gt 0) {
            Write-Host ("missing expected categories: {0}" -f ($missingCategories -join ','))
        }
        if ($missingFilenameCategories.Count -gt 0) {
            Write-Host ("missing expected filename categories: {0}" -f ($missingFilenameCategories -join ','))
        }

        if (
            ($missingCategories.Count -eq 0) -and
            ($missingFilenameCategories.Count -eq 0) -and
            ($protectedFindings.Count -eq 0)
        ) {
            Write-Host '[OK] Self-test passed: content and filename violations were detected, protected sample raised zero findings.'
            $exitCode = 0
        } else {
            Write-Host '[FAIL] Self-test failed.'
            $exitCode = 1
        }
    } finally {
        if (Test-Path -LiteralPath $tempRoot) {
            Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
    return $exitCode
}

if ($SelfTest) {
    $selfTestExitCode = Invoke-HrnsSourceUniversalitySelfTest
    exit $selfTestExitCode
}

$repoRootFull = [System.IO.Path]::GetFullPath($RepositoryRoot)
if (-not (Test-Path -LiteralPath $repoRootFull -PathType Container)) {
    throw ('RepositoryRoot not found: {0}' -f $repoRootFull)
}

$findings = @(Invoke-HrnsSourceUniversalityScan -RepoRoot $repoRootFull -SelfPath $PSCommandPath)

if ($findings.Count -gt 0) {
    Write-Host 'Source universality violations found:'
    $findings | Sort-Object path, line | ForEach-Object {
        Write-Host ("{0}:{1} [{2}]" -f $_.path, $_.line, $_.category)
    }
    Write-Host ("[FAIL] {0} violation(s) found." -f $findings.Count)
    exit 1
}

Write-Host '[OK] No source universality violations found.'
exit 0
