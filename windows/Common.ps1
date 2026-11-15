Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
function Get-InstanceRoot {
    if ($env:OS -ne 'Windows_NT') { throw 'This installer requires Windows.' }
    Join-Path $env:LOCALAPPDATA 'HouseholdFinance'
}
function Set-PrivatePath([string]$Path, [bool]$Directory) {
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse points are not supported.' }
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    if ($Directory) {
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $acl.SetOwner((New-Object Security.Principal.NTAccount($identity)))
        $acl.SetAccessRuleProtection($true, $false)
        $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
    } else {
        $acl = New-Object Security.AccessControl.FileSecurity
        $acl.SetOwner((New-Object Security.Principal.NTAccount($identity)))
        $acl.SetAccessRuleProtection($true, $false)
        $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')))
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
}
function Assert-PrivatePath([string]$Path) {
    $item = Get-Item -LiteralPath $Path -Force
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse points are not supported.' }
    $current = if ($item -is [IO.DirectoryInfo]) { $item } else { $item.Directory }
    while ($current) {
        if ($current.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse points are not supported.' }
        if ($current -is [IO.DirectoryInfo] -and (Test-Path -LiteralPath (Join-Path $current.FullName '.git'))) { throw 'Instance data must be outside a checkout.' }
        $current = $current.Parent
    }
    $acl = Get-Acl -LiteralPath $Path
    $owner = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    if ((New-Object Security.Principal.NTAccount($acl.Owner)).Translate([Security.Principal.SecurityIdentifier]).Value -ne $owner) { throw 'Instance owner differs from current Windows user.' }
    foreach ($rule in $acl.Access) {
        $sid = $rule.IdentityReference.Translate([Security.Principal.SecurityIdentifier]).Value
        if ($rule.AccessControlType -eq 'Allow' -and $sid -notin @($owner, 'S-1-5-18', 'S-1-5-32-544')) { throw 'Instance permissions allow another principal.' }
    }
}
function Read-PrivateConfig([string]$Root) {
    Assert-PrivatePath $Root
    $file = Join-Path $Root 'config.xml'
    Assert-PrivatePath $file
    Import-Clixml -LiteralPath $file
}
function Get-PlainSecret([Security.SecureString]$Secret) {
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
function Test-EncryptedDevice([string]$Path) {
    $volume = [IO.Path]::GetPathRoot($Path).TrimEnd('\')
    try {
        $disk = Get-CimInstance -Namespace 'root/CIMV2/Security/MicrosoftVolumeEncryption' -ClassName Win32_EncryptableVolume -Filter "DriveLetter='$volume'" -ErrorAction Stop
        if (-not $disk) { throw 'Volume encryption status unavailable.' }
        $protection = Invoke-CimMethod -InputObject $disk -MethodName GetProtectionStatus
        $conversion = Invoke-CimMethod -InputObject $disk -MethodName GetConversionStatus
        if ($protection.ReturnValue -ne 0 -or $protection.ProtectionStatus -ne 1 -or $conversion.ReturnValue -ne 0 -or $conversion.ConversionStatus -ne 1) { throw 'Full device encryption with protection enabled is required.' }
    } catch { throw 'Device encryption could not be verified. Run the readiness check with rights to inspect BitLocker/device encryption; keep production disabled.' }
}
function Get-PackageManifest([string]$Directory) {
    $manifest = Get-Content -LiteralPath (Join-Path $Directory 'manifest.json') -Raw | ConvertFrom-Json
    if ($manifest.version -ne 1 -or $manifest.commit -notmatch '^[0-9a-f]{40}$') { throw 'Invalid release manifest.' }
    foreach ($entry in $manifest.files) {
        if ($entry.path -notmatch '^[A-Za-z0-9_./-]+$' -or $entry.path.Contains('..') -or [IO.Path]::IsPathRooted($entry.path)) { throw 'Invalid release path.' }
        $path = Join-Path $Directory $entry.path
        if ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Invalid release link.' }
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256) { throw 'Release integrity check failed.' }
    }
    $manifest
}
