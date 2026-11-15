param([Parameter(Mandatory=$true)][string]$Package)
$ErrorActionPreference = 'Stop'
# CI is synthetic, hosted and isolated: never the owner's persistent personal runner.
. (Join-Path $Package 'Common.ps1')
Get-PackageManifest $Package | Out-Null
# Parse every packaged script before any installation.
foreach ($file in Get-ChildItem -LiteralPath $Package -Filter '*.ps1') {
    $parseErrors=$null; $tokens=$null
    [Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors) | Out-Null
    if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
}
$instance = Join-Path $env:RUNNER_TEMP ('gbd14-'+[Guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $instance | Out-Null
Set-PrivatePath $instance $true
Assert-PrivatePath $instance
# Regression for the reviewed ancestor-walk failure: native directory/file objects,
# plus hostile ACL, checkout and reparse cases. No protection check is bypassed.
function Assert-Rejected([scriptblock]$Operation) {
    $rejected=$false
    try { & $Operation } catch { $rejected=$true }
    if(-not $rejected){throw 'An unsafe path was accepted.'}
}
# Amended acceptance is not satisfied by the earlier READINESS PASSED prompt.
# Missing, false and string-valued eligibility all fail closed.
foreach ($candidate in @([PSCustomObject]@{},[PSCustomObject]@{productionEligible=$false},[PSCustomObject]@{productionEligible='true'})) {
    Assert-Rejected { Assert-ProductionReleaseCleared $candidate }
}
$release=Get-PackageManifest $Package
Assert-Rejected { Assert-ProductionReleaseCleared $release }
$same=($release | ConvertTo-Json -Depth 5 | ConvertFrom-Json)
$same.productionEligible=$true
Assert-Rejected { Assert-SameRelease $same $release }
# Even a locally modified manifest that claims eligibility cannot activate banking.
Assert-Rejected { Assert-ProductionReleaseCleared $same }
# Hosted runner is an administrator and must never qualify as the production
# runtime even with a filtered token. This does not certify the owner's users.
Assert-Rejected { Assert-StandardRuntimeIdentity }
$nested=Join-Path $instance 'nested\private'
New-Item -ItemType Directory -Path $nested -Force | Out-Null
Set-PrivatePath $nested $true
Assert-PrivatePath $nested
$ordinary=Join-Path $nested 'ordinary.txt'
'synthetic' | Set-Content -LiteralPath $ordinary
Set-PrivatePath $ordinary $false
Assert-PrivatePath $ordinary
$checkout=Join-Path $instance 'checkout'
New-Item -ItemType Directory -Path (Join-Path $checkout '.git') -Force | Out-Null
Assert-Rejected { Assert-PrivatePath $checkout }
$junction=Join-Path $instance 'junction'
New-Item -ItemType Junction -Path $junction -Target $nested | Out-Null
Assert-Rejected { Assert-PrivatePath $junction }
[IO.Directory]::Delete($junction)
$acl=Get-Acl -LiteralPath $nested
$everyone=New-Object Security.Principal.SecurityIdentifier('S-1-1-0')
$acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($everyone,'Read','Allow')))
Set-Acl -LiteralPath $nested -AclObject $acl
Assert-Rejected { Assert-PrivatePath $nested }
Set-PrivatePath $nested $true
# Round-trip Windows DPAPI without disclosing any plaintext in logs.
$secret=ConvertTo-SecureString 'synthetic-test-secret-16' -AsPlainText -Force
[PSCustomObject]@{Password=$secret} | Export-Clixml -LiteralPath (Join-Path $instance 'test.xml')
Set-PrivatePath (Join-Path $instance 'test.xml') $false
$loaded=Import-Clixml -LiteralPath (Join-Path $instance 'test.xml')
if ((Get-PlainSecret $loaded.Password) -ne 'synthetic-test-secret-16') { throw 'DPAPI roundtrip failed.' }
# Exercise the real installer and updater against a temporary synthetic profile.
$originalLocalAppData=$env:LOCALAPPDATA
$originalManifest=Get-Content -LiteralPath (Join-Path $Package 'manifest.json') -Raw
function Read-Host { param([string]$Prompt,[switch]$AsSecureString)
    if($AsSecureString){ConvertTo-SecureString 'synthetic-install-password' -AsPlainText -Force}
    else {'BACKUP VERIFIED'}
}
try {
    $env:LOCALAPPDATA=Join-Path $instance 'profile'
    New-Item -ItemType Directory -Path $env:LOCALAPPDATA | Out-Null
    & (Join-Path $Package 'Install.ps1')
    $installed=Join-Path $env:LOCALAPPDATA 'HouseholdFinance'
    $first=(Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim()
    $versionsPath=Join-Path $installed 'versions'
    Assert-PrivatePath $versionsPath
    # A weak versions ACL must prevent reinstalling even if the root stays private.
    $versionsAcl=Get-Acl -LiteralPath $versionsPath
    $everyone=New-Object Security.Principal.SecurityIdentifier('S-1-1-0')
    $versionsAcl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($everyone,'Read','Allow')))
    Set-Acl -LiteralPath $versionsPath -AclObject $versionsAcl
    try { Assert-Rejected { & (Join-Path $Package 'Install.ps1') } }
    finally { Set-PrivatePath $versionsPath $true }
    Assert-PrivatePath $versionsPath
    # A coherent but stale local manifest cannot replace the incoming reviewed release.
    $versionRoot=Join-Path $installed ('versions\'+$first)
    $storedManifestPath=Join-Path $versionRoot 'manifest.json'
    $storedManifest=[IO.File]::ReadAllBytes($storedManifestPath)
    $storedCommonPath=Join-Path $versionRoot 'Common.ps1'
    $storedCommon=[IO.File]::ReadAllBytes($storedCommonPath)
    try {
        Add-Content -LiteralPath $storedCommonPath -Value '# synthetic stale copy'
        $stale=Get-Content -LiteralPath $storedManifestPath -Raw | ConvertFrom-Json
        ($stale.files | Where-Object { $_.path -eq 'Common.ps1' }).sha256=(Get-FileHash -LiteralPath $storedCommonPath).Hash
        $stale | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $storedManifestPath -Encoding UTF8
        Assert-Rejected { & (Join-Path $Package 'Install.ps1') }
        if((Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim() -ne $first){throw 'Rejected reinstall changed the active release.'}
    } finally {
        [IO.File]::WriteAllBytes($storedCommonPath,$storedCommon)
        [IO.File]::WriteAllBytes($storedManifestPath,$storedManifest)
    }
    New-Item -ItemType Directory -Path (Join-Path $installed 'data') | Out-Null
    Set-PrivatePath (Join-Path $installed 'data') $true
    'synthetic-preserved-record' | Set-Content -LiteralPath (Join-Path $installed 'data\sentinel.txt')
    $changed=$originalManifest | ConvertFrom-Json
    $changed.commit='bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb'
    $changed | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Package 'manifest.json') -Encoding UTF8
    & (Join-Path $Package 'Install.ps1')
    if((Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim() -ne $changed.commit){throw 'Update did not activate.'}
    if((Get-Content -LiteralPath (Join-Path $installed 'previous.txt') -Raw).Trim() -ne $first){throw 'Previous release not retained.'}
    if((Get-Content -LiteralPath (Join-Path $installed 'data\sentinel.txt') -Raw).Trim() -ne 'synthetic-preserved-record'){throw 'Update changed records.'}
    $private=Read-PrivateConfig $installed
    if((Get-PlainSecret $private.Password) -ne 'synthetic-install-password' -or $private.Production){throw 'Update changed private configuration.'}
    # Old local readiness cannot activate the held release. The actual launcher
    # must fail before Java/provider activity, retaining configuration and data.
    $private.Production=$true
    $private | Export-Clixml -LiteralPath (Join-Path $installed 'config.xml')
    $held=$false
    try { & (Join-Path $installed ('versions\'+$changed.commit+'\Start.ps1')) }
    catch { $held=$_.Exception.Message -like 'Production release hold:*' }
    if(-not $held){throw 'Previously enabled configuration bypassed the release hold.'}
    $private.Production=$false
    $private | Export-Clixml -LiteralPath (Join-Path $installed 'config.xml')
    Set-PrivatePath (Join-Path $installed 'config.xml') $false
} finally {
    $env:LOCALAPPDATA=$originalLocalAppData
    $originalManifest | Set-Content -LiteralPath (Join-Path $Package 'manifest.json') -Encoding UTF8
    Remove-Item Function:\Read-Host
}
# Launch the real installed Start.ps1 under Windows PowerShell, including DPAPI configuration.
$env:LOCALAPPDATA=Join-Path $instance 'profile'
$activeVersion=(Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim()
$startScript=Join-Path $installed ('versions\'+$activeVersion+'\Start.ps1')
function Stop-SyntheticInstance {
    param($Previous)
    if(-not $Previous.HasExited) { & taskkill.exe /PID $Previous.Id /T /F | Out-Null; $Previous.WaitForExit() }
    # A killed PowerShell parent can leave a JVM exiting asynchronously. Wait for
    # this exact fixture's Java processes, not any unrelated Java on the runner.
    for($attempt=0;$attempt -lt 30;$attempt++) {
        $children=@(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like ('*'+$installed+'*app.jar*') })
        if($children.Count -eq 0) { break }
        foreach($child in $children) { Stop-Process -Id $child.ProcessId -Force -ErrorAction SilentlyContinue }
        Start-Sleep -Milliseconds 300
    }
    if(@(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like ('*'+$installed+'*app.jar*') }).Count -ne 0) {
        throw 'Previous synthetic Java process was not terminated; refusing overlapping database use.'
    }
    # Give H2/Windows time to release file locks and the listen port before restart.
    Start-Sleep -Milliseconds 500
}
function Restart-SyntheticInstance {
    param($Previous)
    Stop-SyntheticInstance $Previous
    $next=Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-File',('"'+$startScript+'"')) -WorkingDirectory $instance -PassThru -RedirectStandardOutput (Join-Path $instance 'restart.log') -RedirectStandardError (Join-Path $instance 'restart-error.log')
    for($attempt=0;$attempt -lt 90;$attempt++) {
        if($next.HasExited){throw 'Synthetic restart failed.'}
        try { $health=Invoke-WebRequest 'http://127.0.0.1:8080/health' -UseBasicParsing; if($health.StatusCode -eq 200){return $next} } catch {}
        Start-Sleep -Seconds 1
    }
    if(-not $next.HasExited){ & taskkill.exe /PID $next.Id /T /F | Out-Null; $next.WaitForExit() }
    throw 'Synthetic restart timeout.'
}
function Unlock-SyntheticInstance {
    $web=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-WebRequest 'http://127.0.0.1:8080/api/workspace/unlock' -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body '{"password":"synthetic-install-password"}' -WebSession $web -UseBasicParsing | Out-Null
    return $web
}
$process=Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-File',('"'+$startScript+'"')) -WorkingDirectory $instance -PassThru -RedirectStandardOutput (Join-Path $instance 'app.log') -RedirectStandardError (Join-Path $instance 'error.log')
try {
    $ready=$false
    for($i=0;$i -lt 90;$i++) {
        if($process.HasExited) { Get-Content -LiteralPath (Join-Path $instance 'error.log'); throw 'Packaged launcher failed to start.' }
        try { $response=Invoke-WebRequest 'http://127.0.0.1:8080/health' -UseBasicParsing; if($response.StatusCode -eq 200) {$ready=$true;break} } catch {}
        Start-Sleep -Seconds 1
    }
    if(-not $ready) { throw 'Packaged launcher health timeout.' }
    $beforeConfig=(Get-FileHash -LiteralPath (Join-Path $installed 'config.xml')).Hash
    $locked=$false
    try { & (Join-Path (Split-Path $startScript) 'Readiness.ps1') }
    catch { $locked=$_.Exception.Message -like 'Stop Household Finance or the other configuration operation*' }
    if(-not $locked -or (Get-FileHash -LiteralPath (Join-Path $installed 'config.xml')).Hash -ne $beforeConfig){throw 'Readiness did not reject the active instance before config access.'}
    $shell=Invoke-WebRequest 'http://127.0.0.1:8080/' -UseBasicParsing
    if($shell.StatusCode -ne 200 -or $shell.Content -notmatch '/assets/') { throw 'Packaged app shell missing.' }
    $callback=Invoke-WebRequest 'http://127.0.0.1:8080/bank/callback?code=synthetic-secret&state=synthetic-state' -UseBasicParsing
    if($callback.Headers['Referrer-Policy'] -ne 'no-referrer') { throw 'Callback referrer protection missing.' }
    try { Invoke-WebRequest 'http://127.0.0.1:8080/api/banking/status' -UseBasicParsing | Out-Null; throw 'Unprotected banking endpoint.' }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 401) { throw } }
    $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-WebRequest 'http://127.0.0.1:8080/api/workspace/unlock' -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body '{"password":"synthetic-install-password"}' -WebSession $session -UseBasicParsing | Out-Null
    $status=Invoke-RestMethod 'http://127.0.0.1:8080/api/banking/status' -WebSession $session
    if($status.configured -or $status.environment -ne 'SANDBOX') { throw 'Production was enabled by default.' }
    if(-not (Test-Path -LiteralPath (Join-Path $installed 'data\household-finance.mv.db'))) { throw 'Launcher did not use the persistent private data directory.' }
    $today=[DateTime]::UtcNow.ToString('yyyy-MM-dd')
    $account=@{name='Synthetic Windows recovery account';kind='CASH';openingDate=$today;openingEuro='123.45';source='Synthetic Windows opening'}
    Invoke-RestMethod 'http://127.0.0.1:8080/api/ledger/accounts' -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($account | ConvertTo-Json) -WebSession $session | Out-Null
    $backupFile=Join-Path $instance 'synthetic.hfb'
    Invoke-WebRequest 'http://127.0.0.1:8080/api/workspace/backup' -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body '{"password":"synthetic-recovery-passphrase"}' -WebSession $session -UseBasicParsing -OutFile $backupFile | Out-Null
    $bytes=[IO.File]::ReadAllBytes($backupFile)
    if($bytes.Length -lt 45 -or [Text.Encoding]::UTF8.GetString($bytes).Contains('Synthetic Windows recovery account')){throw 'Encrypted backup failed.'}
    $beforeRestart=Invoke-RestMethod ('http://127.0.0.1:8080/api/ledger?asOf='+$today) -WebSession $session
    if($beforeRestart.cashMinor -ne 12345){throw 'Synthetic opening position was not committed before restart.'}
    $process=Restart-SyntheticInstance $process
    $session=Unlock-SyntheticInstance
    $position=Invoke-RestMethod ('http://127.0.0.1:8080/api/ledger?asOf='+$today) -WebSession $session
    if($position.cashMinor -ne 12345){throw ('Synthetic records lost after actual Windows restart: observed '+$position.cashMinor+' minor units.')}
    Stop-SyntheticInstance $process
    Move-Item -LiteralPath (Join-Path $installed 'data') -Destination (Join-Path $installed 'original-synthetic-data')
    $process=Restart-SyntheticInstance $process
    $session=Unlock-SyntheticInstance
    $restore=@{password='synthetic-recovery-passphrase';backup=[Convert]::ToBase64String($bytes);confirmation='RESTORE INTO EMPTY WORKSPACE'}
    Invoke-RestMethod 'http://127.0.0.1:8080/api/workspace/restore' -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($restore | ConvertTo-Json) -WebSession $session | Out-Null
    $position=Invoke-RestMethod ('http://127.0.0.1:8080/api/ledger?asOf='+$today) -WebSession $session
    if($position.cashMinor -ne 12345){throw 'Packaged Windows encrypted restore changed synthetic records.'}
    [Array]::Clear($bytes,0,$bytes.Length)
    Write-Host 'Packaged Windows runtime, actual launcher/restart/encrypted restore, static app, callback, private ACLs, DPAPI and authenticated banking checks passed using synthetic data.'
} finally {
    $env:LOCALAPPDATA=$originalLocalAppData
    Stop-SyntheticInstance $process
    for($attempt=0;$attempt -lt 12;$attempt++) {
        try { Remove-Item -LiteralPath $instance -Recurse -Force -ErrorAction Stop; break }
        catch { if($attempt -eq 11){throw}; Start-Sleep -Seconds 1 }
    }
}
