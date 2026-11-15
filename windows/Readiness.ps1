# Run only after the release's independent security review and synthetic checks have passed.
# No bank authentication or provider request occurs here.
. "$PSScriptRoot\Common.ps1"
$root = Get-InstanceRoot
$mutex = New-Object Threading.Mutex($false, 'Local\HouseholdFinance-Instance')
if (-not $mutex.WaitOne(0)) { $mutex.Dispose(); throw 'Stop Household Finance or the other configuration operation before readiness.' }
try {
    $manifest = Get-PackageManifest $PSScriptRoot
    $config = Read-PrivateConfig $root
    Test-EncryptedDevice $root
    Assert-PrivatePath (Join-Path $root 'data')
    $review = Read-Host 'Confirm exact-release independent security review, synthetic tests and encrypted recovery passed: type READINESS PASSED'
    if ($review -ne 'READINESS PASSED') { throw 'Readiness remains disabled.' }
    $id = Read-Host 'Production AIS-only application UUID (register without linking accounts yet)'
    $parsed = [Guid]::Empty
    if (-not [Guid]::TryParse($id,[ref]$parsed)) { throw 'Invalid application UUID.' }
    $source = Read-Host 'Local path to production PKCS#8 PEM private key (never enter key content)'
    Assert-PrivatePath $source
    if ((Get-Item -LiteralPath $source).Length -gt 16384) { throw 'Unsupported key file.' }
    $secrets = Join-Path $root 'secrets'
    if (-not (Test-Path -LiteralPath $secrets)) { New-Item -ItemType Directory -Path $secrets | Out-Null; Set-PrivatePath $secrets $true }
    Assert-PrivatePath $secrets
    $target = Join-Path $secrets ($parsed.ToString()+'.pem')
    if (-not (Test-Path -LiteralPath $target)) { Copy-Item -LiteralPath $source -Destination $target; Set-PrivatePath $target $false }
    elseif ((Get-FileHash -LiteralPath $source).Hash -ne (Get-FileHash -LiteralPath $target).Hash) { throw 'Existing key differs. Rotate and revoke deliberately.' }
    Assert-PrivatePath $target
    $config.AppId=$parsed.ToString(); $config.KeyPath=$target; $config.ReadinessCommit=$manifest.commit; $config.ReadinessAt=[DateTimeOffset]::UtcNow.ToString('o'); $config.Production=$true
    $config | Export-Clixml -LiteralPath (Join-Path $root 'config.xml')
    Set-PrivatePath (Join-Path $root 'config.xml') $false
    Write-Host 'Local checks recorded for seven days and this release only. Account activation/linking and bank consent are the owner-only next step, after reviewing the controlled validation instructions. No live connectivity is claimed.'
} finally { $mutex.ReleaseMutex(); $mutex.Dispose() }
