param(
    [Parameter(Mandatory=$true)][ValidateSet('Runtime','Excluded')][string]$Mode,
    [Parameter(Mandatory=$true)][string]$RuntimeSid,
    [Parameter(Mandatory=$true)][string]$InstanceRoot
)
# Operator-local audit, not provisioning or an authorisation receipt. Run the
# Excluded mode in each ACTUAL development/agent and runner Windows logon.
# Never supply credentials on the command line. No bank requests or file output.
. "$PSScriptRoot\Common.ps1"
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$sid = $identity.User.Value
if ($RuntimeSid -notmatch '^S-1-5-21-[0-9-]+$') { throw 'A local runtime user SID is required.' }
if ($Mode -eq 'Runtime') {
    $sid = Assert-StandardRuntimeIdentity
    if ($sid -ne $RuntimeSid) { throw 'Wrong runtime identity.' }
    Assert-PrivatePath $InstanceRoot
    $count=0
    # Inspect code, state, config, keys and local encrypted backups; never read
    # their contents. Reparse points and foreign ACL grants fail the audit.
    $pending=New-Object 'Collections.Generic.Stack[string]'
    $pending.Push($InstanceRoot)
    while ($pending.Count) {
        $directory=$pending.Pop()
        foreach ($item in Get-ChildItem -LiteralPath $directory -Force) {
            Assert-PrivatePath $item.FullName
            $count++
            if ($item -is [IO.DirectoryInfo]) { $pending.Push($item.FullName) }
        }
    }
    Test-EncryptedDevice $InstanceRoot
    [PSCustomObject]@{Version=1;Mode=$Mode;RuntimeIdentityMatches=$true;PrivateObjectsChecked=$count;DeviceEncryptionVerified=$true;BankAccessPerformed=$false}
} else {
    # The owner's main development account is an administrator, but Codex and
    # CI normally run with its UAC-filtered, non-elevated token. Test that token
    # directly instead of treating the account's admin-group membership as
    # proof that its ordinary file opens can bypass the runtime DACL. An
    # elevated administrator or SYSTEM token is not a valid denial probe.
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    if ($identity.IsSystem -or $sid -notlike 'S-1-5-21-*' -or $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
        throw 'Run the excluded-identity probe as a distinct local user with a non-elevated token; elevated administrator/SYSTEM access is outside the DACL boundary.'
    }
    if ($sid -eq $RuntimeSid) { throw 'Development/runner and runtime are the same identity: isolation FAILED.' }
    # Count only actual access-denied results. Missing paths/other errors are
    # not evidence of isolation. Owner must first verify installation exists.
    $denied=0
    foreach ($relative in @('config.xml','data\household-finance.mv.db')) {
        $stream=$null
        try {
            $stream=[IO.File]::Open((Join-Path $InstanceRoot $relative),[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
        } catch [UnauthorizedAccessException] { $denied++; continue }
        catch { throw 'Probe inconclusive: expected an existing protected file and access denial.' }
        finally { if ($stream) { $stream.Dispose() } }
        throw 'Excluded identity can read production configuration or financial records: isolation FAILED.'
    }
    foreach ($relative in @('secrets','versions','backups')) {
        try { [IO.Directory]::GetFileSystemEntries((Join-Path $InstanceRoot $relative)) | Out-Null }
        catch [UnauthorizedAccessException] { $denied++; continue }
        catch { throw 'Probe inconclusive: protected directory must exist and deny enumeration.' }
        throw 'Excluded identity can enumerate a protected production directory: isolation FAILED.'
    }
    [PSCustomObject]@{Version=2;Mode=$Mode;DistinctLocalIdentity=$true;NonElevatedToken=$true;AdministratorSidPresent=(@($identity.Groups | ForEach-Object {$_.Value}) -contains 'S-1-5-32-544');AccessDeniedChecks=$denied;ElevatedAdministratorOverridePossible=$true;BankAccessPerformed=$false}
}
