# Read-only host discovery. This is NOT a readiness or identity-boundary receipt.
# Run under each actual excluded logon; never pass credentials to this script.
$ErrorActionPreference='Stop'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
$principal=New-Object Security.Principal.WindowsPrincipal($identity)
$result=[ordered]@{
    SchemaVersion=1
    Purpose='Read-only deployment discovery; no production approval'
    ElevatedAdministrator=$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    AdministratorGroupPresent=(@($identity.Groups | ForEach-Object {$_.Value}) -contains 'S-1-5-32-544')
    IdentityValuesEmitted=$false
    SecuritySettingsChanged=$false
}
try {
    $os=Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
    $result.WindowsVersion=$os.Caption
    $result.WindowsBuild=$os.BuildNumber
} catch { $result.OSMetadata='Unavailable' }
try {
    $volumes=@(Get-BitLockerVolume -ErrorAction Stop)
    $result.VolumesDiscovered=$volumes.Count
    $result.FullyEncryptedProtectedVolumes=@($volumes | Where-Object {$_.VolumeStatus -eq 'FullyEncrypted' -and $_.ProtectionStatus -eq 'On'}).Count
    $result.VolumeEncryptionInventory='Read; actual instance-volume mapping still required'
} catch { $result.VolumeEncryptionInventory='Unavailable; device encryption not verified' }
$admins=@()
try {
    $admins=@(Get-LocalGroupMember -SID 'S-1-5-32-544' -ErrorAction Stop | ForEach-Object {$_.SID.Value})
    $result.LocalAdministratorsInventoryAvailable=$true
} catch { $result.LocalAdministratorsInventoryAvailable=$false }
$counts=[ordered]@{Found=0;OwnerResolved=0;LocalAdministratorOwners=0;BuiltInServiceOwners=0;OwnerUnknown=0}
try {
    foreach($process in @(Get-CimInstance Win32_Process -Filter "Name='Runner.Listener.exe' OR Name='Runner.Worker.exe'" -ErrorAction Stop)) {
        $counts.Found++
        try {
            $owner=Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid -ErrorAction Stop
            if($owner.ReturnValue -ne 0 -or -not $owner.Sid) { $counts.OwnerUnknown++; continue }
            $counts.OwnerResolved++
            if($owner.Sid -in $admins) { $counts.LocalAdministratorOwners++ }
            if($owner.Sid -in @('S-1-5-18','S-1-5-19','S-1-5-20')) { $counts.BuiltInServiceOwners++ }
        } catch { $counts.OwnerUnknown++ }
    }
    $result.RunnerEnumerationAvailable=$true
} catch { $result.RunnerEnumerationAvailable=$false }
$result.RunnerProcesses=$counts
$result.PrivilegedRunnerBoundaryBlocked=($counts.LocalAdministratorOwners -gt 0 -or $counts.BuiltInServiceOwners -gt 0)
$result.ProductionIsolationVerified=$false
$result | ConvertTo-Json -Depth 4
