. "$PSScriptRoot\Common.ps1"
$root=Get-InstanceRoot
$config=Read-PrivateConfig $root
$mutex=New-Object Threading.Mutex($false,'Local\HouseholdFinance-Instance')
if(-not $mutex.WaitOne(0)){throw 'Stop Household Finance first.'}
try {
    $config.Production=$false
    $config | Export-Clixml -LiteralPath (Join-Path $root 'config.xml')
    Set-PrivatePath (Join-Path $root 'config.xml') $false
    Write-Host 'Production disabled locally. Manual/CSV operation can continue. Existing provider consent is not revoked; revoke it through AIB/Enable Banking when needed.'
} finally {$mutex.ReleaseMutex();$mutex.Dispose()}
