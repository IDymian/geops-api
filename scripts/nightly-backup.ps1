<#
.SYNOPSIS
  Nightly backup of unfinished work to the `wip/nightly` branch (Windows).

.DESCRIPTION
  - Snapshots the working tree (tracked + untracked, honoring .gitignore)
    without touching your branches, index or files.
  - Pushes the snapshot to origin/wip/nightly only if something changed.
  - Never modifies main, develop or feature/* branches.
  - Aborts (and shows a Windows notification) if the snapshot contains
    sensitive files or secrets.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\nightly-backup.ps1
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\nightly-backup.ps1 -RepoPath D:\Personal\geops-api
#>
param(
    [string]$RepoPath = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$BackupBranch = 'wip/nightly'
$Remote = 'origin'
$LogDir = Join-Path $HOME '.geops-backup'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$LogFile = Join-Path $LogDir 'nightly-backup.log'

# Files that must never be pushed (.env.example is allowed).
$SensitivePaths = '(^|/)\.env(\.[^/]+)?$|\.(jks|keystore|p12|pfx|pem|key)$|(^|/)key\.properties$|(^|/)google-services\.json$|(^|/)GoogleService-Info\.plist$|firebase-service-account[^/]*\.json$'
$AllowedPaths = '(^|/)\.env\.example$'
# Secret-looking content in changed lines.
$SensitiveContent = '-----BEGIN [A-Z ]*PRIVATE KEY-----|sk_live_[0-9A-Za-z]+|rk_live_[0-9A-Za-z]+|AKIA[0-9A-Z]{16}|gh[pousr]_[0-9A-Za-z]{30,}'

Set-Location $RepoPath
$Name = Split-Path -Leaf (git rev-parse --show-toplevel)

function Write-Log([string]$Message) {
    $line = '{0} [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Name, $Message
    Write-Output $line
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
}

function Show-Alert([string]$Message) {
    try {
        Add-Type -AssemblyName System.Windows.Forms
        $icon = New-Object System.Windows.Forms.NotifyIcon
        $icon.Icon = [System.Drawing.SystemIcons]::Warning
        $icon.Visible = $true
        $icon.ShowBalloonTip(15000, "GeoPS nightly backup ($Name)", $Message, 'Warning')
        Start-Sleep -Seconds 15
        $icon.Dispose()
    } catch { }
}

# Runs git and fails loudly on a non-zero exit code.
function Invoke-Git {
    $out = & git @args
    if ($LASTEXITCODE -ne 0) { throw "git $($args -join ' ') failed ($LASTEXITCODE)" }
    $out
}

# Runs git where failure is expected and fine (stderr discarded, no throw).
function Invoke-GitQuiet {
    $ErrorActionPreference = 'Continue'
    $out = & git @args 2>$null
    $script:GitExitCode = $LASTEXITCODE
    $out
}

$tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) ('geops-backup-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmpDir | Out-Null
try {
    # 1. Build a snapshot tree in a temporary index (your real index is untouched).
    Invoke-GitQuiet rev-parse -q --verify HEAD | Out-Null
    $hasHead = ($script:GitExitCode -eq 0)
    $env:GIT_INDEX_FILE = Join-Path $tmpDir 'index'
    if ($hasHead) {
        Invoke-Git read-tree HEAD | Out-Null
    }
    Invoke-Git add -A | Out-Null
    $tree = (Invoke-Git write-tree).Trim()
    Remove-Item Env:\GIT_INDEX_FILE

    if ($hasHead) {
        $headTree = (Invoke-Git rev-parse 'HEAD^{tree}').Trim()
        $unpushed = Invoke-Git rev-list -n 1 HEAD --not --remotes
    } else {
        $headTree = ''
        $unpushed = $null
    }

    # 2. Nothing uncommitted and nothing unpushed -> do nothing.
    if ($tree -eq $headTree -and -not $unpushed) {
        Write-Log 'No changes to back up.'
        exit 0
    }

    # 3. Same content as the last backup -> do nothing.
    Invoke-GitQuiet fetch -q $Remote "+refs/heads/${BackupBranch}:refs/remotes/$Remote/$BackupBranch" | Out-Null
    $lastBackup = Invoke-GitQuiet rev-parse -q --verify "refs/remotes/$Remote/$BackupBranch"
    if ($script:GitExitCode -ne 0) { $lastBackup = $null }
    if ($lastBackup -and $tree -eq (Invoke-Git rev-parse "$lastBackup^{tree}").Trim()) {
        Write-Log 'No changes since the last backup.'
        exit 0
    }

    # 4. Safety check: abort if sensitive files or secrets would be pushed.
    $badFiles = @(Invoke-Git ls-tree -r --name-only $tree |
        Where-Object { $_ -match $SensitivePaths -and $_ -notmatch $AllowedPaths })
    $badContent = $false
    if ($hasHead) {
        $badContent = [bool](Invoke-Git diff $headTree $tree |
            Where-Object { $_.StartsWith('+') -and $_ -cmatch $SensitiveContent } |
            Select-Object -First 1)
    }
    if ($badFiles.Count -gt 0 -or $badContent) {
        Write-Log 'ABORTED: sensitive data found, nothing was pushed.'
        if ($badFiles.Count -gt 0) { Write-Log ('  files: ' + ($badFiles -join ' ')) }
        if ($badContent) { Write-Log '  content matches a secret pattern (value not logged)' }
        Show-Alert "Backup aborted: sensitive data found. See $LogFile"
        exit 2
    }

    # 5. Commit the snapshot on top of the last backup and push it.
    $parents = @()
    if ($lastBackup) { $parents += @('-p', $lastBackup.Trim()) }
    if ($hasHead) { $parents += @('-p', 'HEAD') }
    $message = 'chore(wip): nightly backup ' + (Get-Date -Format 'yyyy-MM-dd')
    $commit = (Invoke-Git commit-tree $tree @parents -m $message).Trim()
    Invoke-Git push -q $Remote "${commit}:refs/heads/$BackupBranch" | Out-Null
    Write-Log "Pushed $message ($($commit.Substring(0, 7))) to $Remote/$BackupBranch."
}
catch {
    Write-Log "ERROR: $($_.Exception.Message)"
    Show-Alert "Backup failed: $($_.Exception.Message)"
    exit 1
}
finally {
    Remove-Item Env:\GIT_INDEX_FILE -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
}
