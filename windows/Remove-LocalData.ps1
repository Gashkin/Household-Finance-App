. "$PSScriptRoot\Common.ps1"
$root = Get-InstanceRoot
$mutex = New-Object Threading.Mutex($false, 'Local\HouseholdFinance-Instance')
if (-not $mutex.WaitOne(0)) { throw 'Stop Household Finance before deleting local records.' }
try {
    $config=Read-PrivateConfig $root
    $answer=Read-Host 'First revoke access in AIB/Enable Banking. This erases ALL local financial records and connection references, but not old backups or provider consent. Type DELETE LOCAL DATA'
    if ($answer -ne 'DELETE LOCAL DATA') { throw 'Deletion cancelled.' }
    $data=Join-Path $root 'data'
    Assert-PrivatePath $data
    # Reject nested reparse points; never follow a link outside the fixed private data folder.
    foreach($item in Get-ChildItem -LiteralPath $data -Recurse -Force) {
        if($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Unexpected reparse point; deletion stopped.' }
    }
    Remove-Item -LiteralPath $data -Recurse -Force
    $config.Production=$false; $config.ReadinessCommit=''; $config.ReadinessAt=''
    $config | Export-Clixml -LiteralPath (Join-Path $root 'config.xml')
    Set-PrivatePath (Join-Path $root 'config.xml') $false
    Write-Host 'Local data removed and production disabled. This is logical deletion; disk encryption protects residual blocks. Backups and provider consent must be handled separately.'
} finally { $mutex.ReleaseMutex(); $mutex.Dispose() }
