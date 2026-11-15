# Install or update an already extracted, reviewed release. Does not contact a bank.
. "$PSScriptRoot\Common.ps1"
$root = Get-InstanceRoot
$manifest = Get-PackageManifest $PSScriptRoot
if (-not (Test-Path -LiteralPath $root)) {
    New-Item -ItemType Directory -Path $root | Out-Null
    Set-PrivatePath $root $true
}
Assert-PrivatePath $root
$mutex = New-Object Threading.Mutex($false, 'Local\HouseholdFinance-Instance')
if (-not $mutex.WaitOne(0)) { throw 'Close Household Finance before installing or updating.' }
try {
    $versions = Join-Path $root 'versions'
    if (-not (Test-Path -LiteralPath $versions)) { New-Item -ItemType Directory -Path $versions | Out-Null }
    $target = Join-Path $versions $manifest.commit
    if (Test-Path -LiteralPath $target) { Get-PackageManifest $target | Out-Null }
    else {
        $stage = Join-Path $versions ('staging-' + [Guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $stage | Out-Null
        foreach ($entry in $manifest.files) {
            $destination = Join-Path $stage $entry.path
            New-Item -ItemType Directory -Force -Path (Split-Path $destination) | Out-Null
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot $entry.path) -Destination $destination
        }
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'manifest.json') -Destination $stage
        Get-PackageManifest $stage | Out-Null
        Move-Item -LiteralPath $stage -Destination $target
    }
    if (-not (Test-Path -LiteralPath (Join-Path $root 'config.xml'))) {
        $password = Read-Host 'Create a workspace password (16-256 characters; save in your password manager)' -AsSecureString
        $plain = Get-PlainSecret $password
        if ($plain.Length -lt 16 -or $plain.Length -gt 256) { throw 'Password length must be 16-256.' }
        $plain = $null
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $config = [PSCustomObject]@{ Password=$password; StorageKey=(ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force); Production=$false; AppId=''; KeyPath=''; ReadinessCommit=''; ReadinessAt='' }
        [Array]::Clear($bytes, 0, $bytes.Length)
        $config | Export-Clixml -LiteralPath (Join-Path $root 'config.xml')
        Set-PrivatePath (Join-Path $root 'config.xml') $false
    }
    # The data directory is never copied, replaced or deleted by installation/update.
    $active = Join-Path $root 'active.txt'
    if (Test-Path -LiteralPath $active) {
        $old = (Get-Content -LiteralPath $active -Raw).Trim()
        if ($old -ne $manifest.commit) {
            $answer = Read-Host 'Before updating, export and verify an encrypted recovery backup. Type BACKUP VERIFIED to continue'
            if ($answer -ne 'BACKUP VERIFIED') { throw 'Update stopped; existing version and data retained.' }
        }
    }
    $next = Join-Path $root 'active.next'
    [IO.File]::WriteAllText($next, $manifest.commit)
    if (Test-Path -LiteralPath $active) { [IO.File]::Replace($next,$active,(Join-Path $root 'previous.txt')) }
    else { Move-Item -LiteralPath $next -Destination $active }
    # Stable shortcut locates the active version without changing any data paths.
    $launcher = Join-Path $root 'Launch.cmd'
    '@echo off' + "`r`n" + 'set /p VERSION=<"%LOCALAPPDATA%\HouseholdFinance\active.txt"' + "`r`n" + 'powershell.exe -NoProfile -File "%LOCALAPPDATA%\HouseholdFinance\versions\%VERSION%\Start.ps1"' | Set-Content -LiteralPath $launcher -Encoding ASCII
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut((Join-Path ([Environment]::GetFolderPath('Desktop')) 'Household Finance.lnk'))
    $shortcut.TargetPath = $launcher
    $shortcut.WorkingDirectory = $root
    $shortcut.Save()
    Write-Host 'Installed. Use Household Finance on your desktop. Production remains gated by readiness for this exact version.'
} finally { $mutex.ReleaseMutex(); $mutex.Dispose() }
