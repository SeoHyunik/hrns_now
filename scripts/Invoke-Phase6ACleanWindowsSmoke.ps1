[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Baseline', 'Install', 'Snapshot', 'Uninstall', 'Reinstall')]
    [string]$Stage,

    [Parameter(Mandatory)]
    [string]$MsiPath,

    [Parameter(Mandatory)]
    [string]$EvidenceRoot,

    [string]$KitRoot,
    [string]$ProjectWorkspaceRoot,
    [string]$RepositoryRoot,

    [ValidatePattern('^\d{4}-\d{2}-\d{2}$')]
    [string]$WorkDate = (Get-Date -Format 'yyyy-MM-dd')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Phase 6A의 clean Windows 증빙 수집 도구다. Harness를 수정하거나 UI flow를 자동으로
# 성공 처리하지 않는다. Install/Reinstall 뒤에는 앱 UI에서 Kit 등록 → Doctor → State 조회 →
# 허용된 daily cycle을 실제로 수행한 다음 Snapshot을 실행한다.

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = [Security.Principal.WindowsPrincipal]::new($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-FileEvidence {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return [ordered]@{ path = $Path; exists = $false }
    }

    $item = Get-Item -LiteralPath $Path
    return [ordered]@{
        path = $item.FullName
        exists = $true
        length = $item.Length
        last_write_utc = $item.LastWriteTimeUtc.ToString('o')
        sha256 = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash
    }
}

function Get-DirectoryEvidence {
    param([string]$Path, [switch]$Recursive)

    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        return [ordered]@{ path = $Path; exists = $false; entries = @() }
    }

    $root = (Get-Item -LiteralPath $Path).FullName
    $children = if ($Recursive) {
        Get-ChildItem -LiteralPath $Path -Force -Recurse
    } else {
        Get-ChildItem -LiteralPath $Path -Force
    }
    $entries = @(
        $children |
            Sort-Object FullName |
            ForEach-Object {
                [ordered]@{
                    relative_path = $_.FullName.Substring($root.Length).TrimStart('\')
                    directory = $_.PSIsContainer
                    length = if ($_.PSIsContainer) { $null } else { $_.Length }
                    last_write_utc = $_.LastWriteTimeUtc.ToString('o')
                }
            }
    )
    return [ordered]@{ path = $root; exists = $true; entries = $entries }
}

function Get-DailyArtifactEvidence {
    param([string]$WorkspaceRoot, [string]$Date)

    if ([string]::IsNullOrWhiteSpace($WorkspaceRoot)) {
        return [ordered]@{ supplied = $false }
    }

    $dayRoot = Join-Path $WorkspaceRoot $Date
    return [ordered]@{
        supplied = $true
        workspace_root = $WorkspaceRoot
        day_root = $dayRoot
        request_inbox = Get-FileEvidence (Join-Path $dayRoot 'REQUEST_INBOX.md')
        today_strategy = Get-FileEvidence (Join-Path $dayRoot 'TODAY_STRATEGY.md')
        daily_handoff = Get-FileEvidence (Join-Path $dayRoot 'DAILY_HANDOFF.md')
        workflow_state = Get-FileEvidence (Join-Path $dayRoot 'WORKFLOW_STATE.json')
    }
}

function Get-EnvironmentEvidence {
    $java = Get-Command java.exe -ErrorAction SilentlyContinue
    $hrnsVariables = @(
        Get-ChildItem Env: |
            Where-Object { $_.Name -like 'HRNS_*' } |
            Sort-Object Name |
            ForEach-Object { $_.Name }
    )
    return [ordered]@{
        windows = (Get-CimInstance Win32_OperatingSystem | Select-Object Caption, Version, BuildNumber, OSArchitecture)
        is_administrator = Test-IsAdministrator
        java_on_path = if ($null -eq $java) { $null } else { $java.Source }
        program_files_java = Get-DirectoryEvidence 'C:\Program Files\Java'
        adoptium = Get-DirectoryEvidence 'C:\Program Files\Eclipse Adoptium'
        hrns_environment_variable_names = $hrnsVariables
    }
}

function Get-ProductEvidence {
    $programFilesRoot = 'C:\Program Files\HRNS-NOW'
    $registryRoot = Join-Path $env:APPDATA 'hrns-now'
    $localRoot = Join-Path $env:LOCALAPPDATA 'hrns-now'
    return [ordered]@{
        install = Get-DirectoryEvidence $programFilesRoot -Recursive
        executable = Get-FileEvidence (Join-Path $programFilesRoot 'HRNS-NOW.exe')
        icon = Get-FileEvidence (Join-Path $programFilesRoot 'HRNS-NOW.ico')
        registry_directory = Get-DirectoryEvidence $registryRoot
        registry = Get-FileEvidence (Join-Path $registryRoot 'projects.json')
        local_data_directory = Get-DirectoryEvidence $localRoot
        start_menu_directory = Get-DirectoryEvidence 'C:\ProgramData\Microsoft\Windows\Start Menu\Programs\HRNS-NOW'
    }
}

function Write-Evidence {
    param([string]$Name, [object]$Payload)

    New-Item -ItemType Directory -Path $EvidenceRoot -Force | Out-Null
    $destination = Join-Path $EvidenceRoot $Name
    $json = $Payload | ConvertTo-Json -Depth 10
    [System.IO.File]::WriteAllText(
        $destination,
        $json,
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Output $destination
}

function Invoke-MsiOperation {
    param(
        [ValidateSet('Install', 'Uninstall')]
        [string]$Operation,
        [string]$PackagePath,
        [string]$LogPath
    )

    $switch = if ($Operation -eq 'Install') { '/i' } else { '/x' }
    # Keep space-containing MSI and log paths intact when Start-Process builds its command line.
    $arguments = '{0} "{1}" /qn /norestart /l*v "{2}"' -f $switch, $PackagePath, $LogPath
    return Start-Process -FilePath 'msiexec.exe' -ArgumentList $arguments -Wait -PassThru
}

if (-not (Test-Path -LiteralPath $MsiPath -PathType Leaf)) {
    throw "Release MSI를 찾을 수 없습니다: $MsiPath"
}

if ($Stage -in @('Install', 'Uninstall', 'Reinstall') -and -not (Test-IsAdministrator)) {
    throw "machine-wide MSI $Stage 단계는 관리자 PowerShell에서 실행해야 합니다."
}

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$common = [ordered]@{
    generated_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    stage = $Stage
    msi = Get-FileEvidence $MsiPath
    environment = Get-EnvironmentEvidence
    supplied_paths = [ordered]@{
        kit_root = $KitRoot
        project_workspace_root = $ProjectWorkspaceRoot
        repository_root = $RepositoryRoot
        work_date = $WorkDate
    }
}

switch ($Stage) {
    'Baseline' {
        $common.product = Get-ProductEvidence
        $common.daily_artifacts = Get-DailyArtifactEvidence $ProjectWorkspaceRoot $WorkDate
        Write-Evidence "phase6a-baseline-$timestamp.json" $common
    }
    'Install' {
        $logPath = Join-Path $EvidenceRoot "msi-install-$timestamp.log"
        $process = Invoke-MsiOperation -Operation Install -PackagePath $MsiPath -LogPath $logPath
        $common.msiexec_exit_code = $process.ExitCode
        $common.msi_log = $logPath
        $common.product = Get-ProductEvidence
        Write-Evidence "phase6a-install-$timestamp.json" $common
        if ($process.ExitCode -ne 0) { throw "MSI install failed with exit code $($process.ExitCode)." }
    }
    'Snapshot' {
        $common.product = Get-ProductEvidence
        $common.daily_artifacts = Get-DailyArtifactEvidence $ProjectWorkspaceRoot $WorkDate
        Write-Evidence "phase6a-snapshot-$timestamp.json" $common
    }
    'Uninstall' {
        $logPath = Join-Path $EvidenceRoot "msi-uninstall-$timestamp.log"
        $process = Invoke-MsiOperation -Operation Uninstall -PackagePath $MsiPath -LogPath $logPath
        $common.msiexec_exit_code = $process.ExitCode
        $common.msi_log = $logPath
        $common.product = Get-ProductEvidence
        $common.daily_artifacts = Get-DailyArtifactEvidence $ProjectWorkspaceRoot $WorkDate
        Write-Evidence "phase6a-uninstall-$timestamp.json" $common
        if ($process.ExitCode -ne 0) { throw "MSI uninstall failed with exit code $($process.ExitCode)." }
    }
    'Reinstall' {
        $logPath = Join-Path $EvidenceRoot "msi-reinstall-$timestamp.log"
        $process = Invoke-MsiOperation -Operation Install -PackagePath $MsiPath -LogPath $logPath
        $common.msiexec_exit_code = $process.ExitCode
        $common.msi_log = $logPath
        $common.product = Get-ProductEvidence
        $common.daily_artifacts = Get-DailyArtifactEvidence $ProjectWorkspaceRoot $WorkDate
        Write-Evidence "phase6a-reinstall-$timestamp.json" $common
        if ($process.ExitCode -ne 0) { throw "MSI reinstall failed with exit code $($process.ExitCode)." }
    }
}
