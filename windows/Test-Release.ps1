param([Parameter(Mandatory=$true)][string]$Package, [switch]$KeepEvidence)
$ErrorActionPreference = 'Stop'
# Synthetic temporary profile only; results never certify a real production identity.
. (Join-Path $Package 'Common.ps1')
Get-PackageManifest $Package | Out-Null
# Parse every packaged script before any installation.
foreach ($file in Get-ChildItem -LiteralPath $Package -Filter '*.ps1') {
    $parseErrors=$null; $tokens=$null
    [Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors) | Out-Null
    if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
}
$fixtureParent=[IO.Path]::GetFullPath($env:RUNNER_TEMP)
$instance = [IO.Path]::GetFullPath((Join-Path $fixtureParent ('gbd15-'+[Guid]::NewGuid().ToString())))
if(-not $instance.StartsWith($fixtureParent+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture directory escapes its temporary parent.' }
function Assert-FixturePath([string]$Path) {
    $resolved=[IO.Path]::GetFullPath($Path)
    if($resolved -ne $instance -and -not $resolved.StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture operation escapes its temporary instance.' }
    if((Test-Path -LiteralPath $resolved) -and ((Get-Item -LiteralPath $resolved -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Fixture operation cannot traverse a reparse point.' }
}
$listener=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
$listener.Start()
$fixturePort=$listener.LocalEndpoint.Port
$listener.Stop()
$baseUrl="http://127.0.0.1:$fixturePort"
$shortcutDirectory=Join-Path $instance 'synthetic-desktop'
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
function Set-SyntheticWeakDirectoryAcl([string]$Path) {
    Assert-FixturePath $Path
    # Native DACL-only grant avoids attempting privileged audit/SACL writes.
    & icacls.exe $Path /grant '*S-1-1-0:(R)' | Out-Null
    if($LASTEXITCODE -ne 0) { throw 'Could not prepare the synthetic weak DACL.' }
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
# Exercise the predicate for the actual test identity without treating a standard
# account as proof of host isolation. Hosted administrators must be rejected.
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
$groups=@($identity.Groups | ForEach-Object { $_.Value })
if($identity.IsSystem -or $identity.User.Value -notlike 'S-1-5-21-*' -or 'S-1-5-32-544' -in $groups) {
    Assert-Rejected { Assert-StandardRuntimeIdentity }
} else { Assert-StandardRuntimeIdentity | Out-Null }
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
Set-SyntheticWeakDirectoryAcl $nested
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
    & (Join-Path $Package 'Install.ps1') -ShortcutDirectory $shortcutDirectory
    $installed=Join-Path $env:LOCALAPPDATA 'HouseholdFinance'
    $first=(Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim()
    $versionsPath=Join-Path $installed 'versions'
    Assert-PrivatePath $versionsPath
    foreach($name in @('backups','secrets')) { $privatePath=Join-Path $installed $name; if(-not (Test-Path -LiteralPath $privatePath)){throw "Installer did not create the protected $name directory."}; Assert-PrivatePath $privatePath }
    # A weak versions ACL must prevent reinstalling even if the root stays private.
    Set-SyntheticWeakDirectoryAcl $versionsPath
    try { Assert-Rejected { & (Join-Path $Package 'Install.ps1') -ShortcutDirectory $shortcutDirectory } }
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
        Assert-Rejected { & (Join-Path $Package 'Install.ps1') -ShortcutDirectory $shortcutDirectory }
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
    & (Join-Path $Package 'Install.ps1') -ShortcutDirectory $shortcutDirectory
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
    $next=Start-Process -FilePath (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') -ArgumentList @('-NoProfile','-File',('"'+$startScript+'"'),'-Port',[string]$fixturePort) -WorkingDirectory $instance -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $instance 'restart.log') -RedirectStandardError (Join-Path $instance 'restart-error.log')
    for($attempt=0;$attempt -lt 90;$attempt++) {
        if($next.HasExited){throw 'Synthetic restart failed.'}
        try { $health=Invoke-WebRequest "${baseUrl}/health" -UseBasicParsing; if($health.StatusCode -eq 200){return $next} } catch {}
        Start-Sleep -Seconds 1
    }
    if(-not $next.HasExited){ & taskkill.exe /PID $next.Id /T /F | Out-Null; $next.WaitForExit() }
    throw 'Synthetic restart timeout.'
}
function Unlock-SyntheticInstance {
    $web=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-WebRequest "${baseUrl}/api/workspace/unlock" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body '{"password":"synthetic-install-password"}' -WebSession $web -UseBasicParsing | Out-Null
    return $web
}
# Even an inherited developer opt-in cannot enable binary intake in the installed runtime.
$originalWorkbookImportEnabled=$env:WORKBOOK_IMPORT_ENABLED
$env:WORKBOOK_IMPORT_ENABLED='true'
$process=Start-Process -FilePath (Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe') -ArgumentList @('-NoProfile','-File',('"'+$startScript+'"'),'-Port',[string]$fixturePort) -WorkingDirectory $instance -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $instance 'app.log') -RedirectStandardError (Join-Path $instance 'error.log')
try {
    $ready=$false
    for($i=0;$i -lt 90;$i++) {
        if($process.HasExited) { Get-Content -LiteralPath (Join-Path $instance 'error.log'); throw 'Packaged launcher failed to start.' }
        try { $response=Invoke-WebRequest "${baseUrl}/health" -UseBasicParsing; if($response.StatusCode -eq 200) {$ready=$true;break} } catch {}
        Start-Sleep -Seconds 1
    }
    if(-not $ready) { throw 'Packaged launcher health timeout.' }
    $beforeConfig=(Get-FileHash -LiteralPath (Join-Path $installed 'config.xml')).Hash
    $locked=$false
    try { & (Join-Path (Split-Path $startScript) 'Readiness.ps1') }
    catch { $locked=$_.Exception.Message -like 'Stop Household Finance or the other configuration operation*' }
    if(-not $locked -or (Get-FileHash -LiteralPath (Join-Path $installed 'config.xml')).Hash -ne $beforeConfig){throw 'Readiness did not reject the active instance before config access.'}
    $shell=Invoke-WebRequest "${baseUrl}/" -UseBasicParsing
    if($shell.StatusCode -ne 200 -or $shell.Content -notmatch '/assets/') { throw 'Packaged app shell missing.' }
    $callback=Invoke-WebRequest "${baseUrl}/bank/callback?code=synthetic-secret&state=synthetic-state" -UseBasicParsing
    if($callback.Headers['Referrer-Policy'] -ne 'no-referrer') { throw 'Callback referrer protection missing.' }
    try { Invoke-WebRequest "${baseUrl}/api/banking/status" -UseBasicParsing | Out-Null; throw 'Unprotected banking endpoint.' }
    catch { if ($_.Exception.Response.StatusCode.value__ -ne 401) { throw } }
    $session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
    Invoke-WebRequest "${baseUrl}/api/workspace/unlock" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body '{"password":"synthetic-install-password"}' -WebSession $session -UseBasicParsing | Out-Null
    $status=Invoke-RestMethod "${baseUrl}/api/banking/status" -WebSession $session
    if($status.configured -or $status.environment -ne 'SANDBOX') { throw 'Production was enabled by default.' }
    $boundary='gbd30-synthetic-workbook-boundary'
    $multipart="--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"synthetic.xlsx`"`r`nContent-Type: application/octet-stream`r`n`r`nsynthetic-not-a-workbook`r`n--$boundary--`r`n"
    foreach($route in @('preview','map','validate')) {
        $disabled=$false
        try {
            Invoke-WebRequest "${baseUrl}/migration/workbook/$route" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType "multipart/form-data; boundary=$boundary" -Body ([Text.Encoding]::UTF8.GetBytes($multipart)) -WebSession $session -UseBasicParsing | Out-Null
        } catch {
            if(-not $_.Exception.Response -or $_.Exception.Response.StatusCode.value__ -ne 400){throw}
            $details=if($_.ErrorDetails){[string]$_.ErrorDetails.Message}else{''}
            if($details -notlike '*Workbook imports are disabled*'){throw 'Installed workbook intake reached validation/parsing instead of the disabled policy.'}
            $disabled=$true
        }
        if(-not $disabled){throw 'Installed workbook intake was enabled by an inherited setting.'}
    }
    if(-not (Test-Path -LiteralPath (Join-Path $installed 'data\household-finance.mv.db'))) { throw 'Launcher did not use the persistent private data directory.' }
    $dublin=[TimeZoneInfo]::FindSystemTimeZoneById('GMT Standard Time')
    $today=[TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow,$dublin).ToString('yyyy-MM-dd')
    $account=@{name='Synthetic Windows recovery account';kind='CASH';openingDate=$today;openingEuro='123.45';source='Synthetic Windows opening'}
    $created=Invoke-RestMethod "${baseUrl}/api/ledger/accounts" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($account | ConvertTo-Json) -WebSession $session
    if(-not $created.id){throw 'Synthetic account creation failed.'}
    $csv="reference,date,description,amount,status,kind`nsynthetic-statement-row,$today,Synthetic statement purchase,-20.00,BOOKED,ACTIVITY`n"
    $badCsv="reference,date,description,amount,status,kind`nsynthetic-invalid-row,not-a-date,Synthetic invalid row,-1.00,BOOKED,ACTIVITY`n"
    $badPreview=$false
    try {
        Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($created.id)/preview" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body (@{csv=$badCsv;source='Synthetic malformed statement'} | ConvertTo-Json) -WebSession $session | Out-Null
    } catch { if($_.Exception.Response.StatusCode.value__ -eq 400){$badPreview=$true}else{throw} }
    if(-not $badPreview){throw 'Malformed statement CSV was not rejected during preview.'}
    $afterRejected=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    $unchangedAccount=@($afterRejected.accounts | Where-Object {$_.id -eq $created.id}) | Select-Object -First 1
    if(-not $unchangedAccount -or $unchangedAccount.entries -ne 0 -or $unchangedAccount.bookedMinor -ne 12345){throw 'Rejected statement preview partially changed the account.'}
    $wrongMappingRejected=$false
    try {
        Invoke-RestMethod "${baseUrl}/api/ledger/accounts/00000000-0000-0000-0000-000000000000/preview" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body (@{csv=$csv;source='Synthetic wrong account mapping'} | ConvertTo-Json) -WebSession $session | Out-Null
    } catch { if($_.Exception.Response.StatusCode.value__ -eq 400){$wrongMappingRejected=$true}else{throw} }
    if(-not $wrongMappingRejected){throw 'Unknown statement account mapping was not rejected.'}
    $preview=Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($created.id)/preview" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body (@{csv=$csv;source='Synthetic statement CSV'} | ConvertTo-Json) -WebSession $session
    if($preview.proposals.Count -ne 1 -or $preview.proposals[0].disposition -ne 'NEW'){throw 'Statement CSV preview/account mapping was not explicit or clean.'}
    $approval=@{revision=$preview.revision;rows=@($preview.proposals[0].row);acknowledgedOverlap=$true}
    Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($created.id)/previews/$($preview.id)/approve" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($approval | ConvertTo-Json -Depth 6) -WebSession $session | Out-Null
    $duplicate=Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($created.id)/preview" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body (@{csv=$csv;source='Synthetic repeated statement CSV'} | ConvertTo-Json) -WebSession $session
    if($duplicate.proposals.Count -ne 1 -or $duplicate.proposals[0].disposition -ne 'DUPLICATE'){throw 'Repeated statement row was not detected as a duplicate.'}
    $unused=@{name='Synthetic duplicate account';kind='DEBT';openingDate=$today;openingEuro='0.00';source='Synthetic duplicate opening'}
    $unusedAccount=Invoke-RestMethod "${baseUrl}/api/ledger/accounts" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($unused | ConvertTo-Json) -WebSession $session
    $removal=@{revision=$unusedAccount.revision;reason='Synthetic duplicate account cleanup'}
    Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($unusedAccount.id)" -Method DELETE -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($removal | ConvertTo-Json) -WebSession $session | Out-Null
    $afterRemoval=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    if(@($afterRemoval.accounts | Where-Object {$_.id -eq $unusedAccount.id}).Count -ne 0){throw 'Unused duplicate account was not removed.'}
    $closing=@{revision=$created.revision+1;statementEuro='103.45';source='Synthetic statement closing balance';commit=$true;acceptAdjustment=$false}
    Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($created.id)/reconcile?asOf=$today" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($closing | ConvertTo-Json) -WebSession $session | Out-Null
    $aibOpeningDate=([DateTime]::ParseExact($today,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)).AddDays(-2).ToString('yyyy-MM-dd')
    $aibOpening=@{name='Synthetic AIB import account';kind='DEBT';openingDate=$aibOpeningDate;openingEuro='0.00';source='Synthetic AIB opening'}
    $aibAccount=Invoke-RestMethod "${baseUrl}/api/ledger/accounts" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($aibOpening | ConvertTo-Json) -WebSession $session
    $openingCorrection=@{revision=$aibAccount.revision;openingDate=$aibOpeningDate;openingEuro='100.00';source='Synthetic corrected opening evidence'}
    $aibAccount=Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($aibAccount.id)/opening" -Method PUT -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($openingCorrection | ConvertTo-Json) -WebSession $session
    $yesterday=([DateTime]::ParseExact($today,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)).AddDays(-1).ToString('dd/MM/yy')
    $aibDate=[DateTime]::ParseExact($today,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture).ToString('dd/MM/yy')
    $aibCsv="Posted Date,Description,Debit Amount,Credit Amount,Balance,Transaction Type`n$yesterday,Synthetic AIB debit,12.34,,87.66,Debit`n$aibDate,Synthetic AIB credit,,5.00,92.66,Credit`n"
    $aibPreview=Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($aibAccount.id)/preview" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body (@{csv=$aibCsv;source='Synthetic AIB export'} | ConvertTo-Json) -WebSession $session
    if($aibPreview.format -ne 'AIB' -or $aibPreview.proposals.Count -ne 2 -or $aibPreview.statementBalanceEuro -ne '92.66'){throw 'Synthetic AIB export preview/balance was not recognised.'}
    if($aibPreview.proposals[0].row.amountEuro -ne '-12.34' -or $aibPreview.proposals[1].row.amountEuro -ne '5.00'){throw 'AIB debit/credit signs were mapped incorrectly.'}
    $aibApproval=@{revision=$aibPreview.revision;rows=@($aibPreview.proposals | ForEach-Object {$_.row});acknowledgedOverlap=$true}
    Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($aibAccount.id)/previews/$($aibPreview.id)/approve" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($aibApproval | ConvertTo-Json -Depth 6) -WebSession $session | Out-Null
    $aibDuplicate=Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($aibAccount.id)/preview" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body (@{csv=$aibCsv;source='Synthetic repeated AIB export'} | ConvertTo-Json) -WebSession $session
    if(@($aibDuplicate.proposals | Where-Object {$_.disposition -ne 'DUPLICATE'}).Count -ne 0){throw 'Repeated AIB export was not idempotent.'}
    $afterAibImport=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    $aibCurrent=@($afterAibImport.accounts | Where-Object {$_.id -eq $aibAccount.id}) | Select-Object -First 1
    $aibClosing=@{revision=$aibCurrent.revision;statementEuro=$aibPreview.statementBalanceEuro;source='Synthetic AIB closing balance';commit=$true;acceptAdjustment=$false}
    Invoke-RestMethod "${baseUrl}/api/ledger/accounts/$($aibAccount.id)/reconcile?asOf=$today" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($aibClosing | ConvertTo-Json) -WebSession $session | Out-Null
    $backupFile=Join-Path $instance 'synthetic.hfb'
    Invoke-WebRequest "${baseUrl}/api/workspace/backup" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body '{"password":"synthetic-recovery-passphrase"}' -WebSession $session -UseBasicParsing -OutFile $backupFile | Out-Null
    $bytes=[IO.File]::ReadAllBytes($backupFile)
    if($bytes.Length -lt 45 -or [Text.Encoding]::UTF8.GetString($bytes).Contains('Synthetic Windows recovery account')){throw 'Encrypted backup failed.'}
    $beforeRestart=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    $beforeAccount=@($beforeRestart.accounts | Where-Object {$_.id -eq $created.id}) | Select-Object -First 1
    if($beforeRestart.cashMinor -ne 10345 -or -not $beforeAccount -or $beforeAccount.entries -ne 1 -or -not $beforeAccount.reconciled){throw 'Imported synthetic statement, balance or reconciliation was incorrect before restart.'}
    $beforeAib=@($beforeRestart.accounts | Where-Object {$_.id -eq $aibAccount.id}) | Select-Object -First 1
    if(-not $beforeAib -or $beforeAib.bookedMinor -ne 9266 -or $beforeAib.entries -ne 2 -or -not $beforeAib.reconciled){throw 'Synthetic AIB import or balance correction was incorrect before restart.'}
    $process=Restart-SyntheticInstance $process
    $session=Unlock-SyntheticInstance
    $position=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    $afterAccount=@($position.accounts | Where-Object {$_.id -eq $created.id}) | Select-Object -First 1
    if($position.cashMinor -ne 10345 -or -not $afterAccount -or $afterAccount.entries -ne 1 -or -not $afterAccount.reconciled){throw ('Imported synthetic statement or reconciliation changed after actual Windows restart: observed '+$position.cashMinor+' minor units.')}
    $afterAib=@($position.accounts | Where-Object {$_.id -eq $aibAccount.id}) | Select-Object -First 1
    if(-not $afterAib -or $afterAib.bookedMinor -ne 9266 -or $afterAib.entries -ne 2 -or -not $afterAib.reconciled){throw 'Synthetic AIB import or opening correction changed after Windows restart.'}
    Stop-SyntheticInstance $process
    # Exercise the real installer update path after committed statement data.
    # The ZIP contents are unchanged; a distinct version identity verifies that
    # activation leaves the persistent data tree untouched.
    Stop-SyntheticInstance $process
    $upgradeOriginal=Get-Content -LiteralPath (Join-Path $Package 'manifest.json') -Raw
    $upgradeManifest=$upgradeOriginal | ConvertFrom-Json
    $upgradeManifest.commit='cccccccccccccccccccccccccccccccccccccccc'
    $upgradeManifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Package 'manifest.json') -Encoding UTF8
    function Read-Host { param([string]$Prompt,[switch]$AsSecureString) 'BACKUP VERIFIED' }
    try { & (Join-Path $Package 'Install.ps1') -ShortcutDirectory $shortcutDirectory }
    finally { $upgradeOriginal | Set-Content -LiteralPath (Join-Path $Package 'manifest.json') -Encoding UTF8; Remove-Item Function:\Read-Host }
    $activeVersion=(Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim()
    if($activeVersion -ne $upgradeManifest.commit){throw 'Post-import update did not activate the new version.'}
    $startScript=Join-Path $installed ('versions\'+$activeVersion+'\Start.ps1')
    $process=Restart-SyntheticInstance $process
    $session=Unlock-SyntheticInstance
    $position=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    $upgradeAccount=@($position.accounts | Where-Object {$_.id -eq $created.id}) | Select-Object -First 1
    if($position.cashMinor -ne 10345 -or -not $upgradeAccount -or $upgradeAccount.entries -ne 1 -or -not $upgradeAccount.reconciled){throw 'Installed update changed the reviewed synthetic statement or reconciliation.'}
    Stop-SyntheticInstance $process
    $originalData=Join-Path $installed 'data'
    $recoveryData=Join-Path $installed 'original-synthetic-data'
    Assert-FixturePath $originalData; Assert-FixturePath $recoveryData
    Move-Item -LiteralPath $originalData -Destination $recoveryData
    $process=Restart-SyntheticInstance $process
    $session=Unlock-SyntheticInstance
    $restore=@{password='synthetic-recovery-passphrase';backup=[Convert]::ToBase64String($bytes);confirmation='RESTORE INTO EMPTY WORKSPACE'}
    Invoke-RestMethod "${baseUrl}/api/workspace/restore" -Method POST -Headers @{'X-Workspace-Request'='1'} -ContentType 'application/json' -Body ($restore | ConvertTo-Json) -WebSession $session | Out-Null
    Assert-Rejected { Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session | Out-Null }
    $session=Unlock-SyntheticInstance
    $position=Invoke-RestMethod ("${baseUrl}/api/ledger?asOf="+$today) -WebSession $session
    $restoredAccount=@($position.accounts | Where-Object {$_.id -eq $created.id}) | Select-Object -First 1
    $restoredAib=@($position.accounts | Where-Object {$_.id -eq $aibAccount.id}) | Select-Object -First 1
    if($position.cashMinor -ne 10345 -or -not $restoredAccount -or $restoredAccount.entries -ne 1 -or -not $restoredAccount.reconciled -or -not $restoredAib -or $restoredAib.bookedMinor -ne 9266 -or -not $restoredAib.reconciled){throw 'Packaged Windows encrypted restore changed imported synthetic statement data.'}
    [Array]::Clear($bytes,0,$bytes.Length)
    Write-Host 'Packaged Windows runtime, AIB/interchange CSV preview/import/deduplication/reconciliation, account removal/opening correction, actual restart/encrypted restore, static app, private ACLs and DPAPI checks passed using synthetic data. Banking remained disabled.'
} finally {
    $env:WORKBOOK_IMPORT_ENABLED=$originalWorkbookImportEnabled
    $env:LOCALAPPDATA=$originalLocalAppData
    Stop-SyntheticInstance $process
    Assert-FixturePath $instance
    for($attempt=0;-not $KeepEvidence -and $attempt -lt 12;$attempt++) {
        try { Remove-Item -LiteralPath $instance -Recurse -Force -ErrorAction Stop; break }
        catch { if($attempt -eq 11){throw}; Start-Sleep -Seconds 1 }
    }
}
