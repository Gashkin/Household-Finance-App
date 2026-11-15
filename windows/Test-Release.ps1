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
} finally {
    $env:LOCALAPPDATA=$originalLocalAppData
    $originalManifest | Set-Content -LiteralPath (Join-Path $Package 'manifest.json') -Encoding UTF8
    Remove-Item Function:\Read-Host
}
# Launch the real installed Start.ps1 under Windows PowerShell, including DPAPI configuration.
$env:LOCALAPPDATA=Join-Path $instance 'profile'
$activeVersion=(Get-Content -LiteralPath (Join-Path $installed 'active.txt') -Raw).Trim()
$startScript=Join-Path $installed ('versions\'+$activeVersion+'\Start.ps1')
$process=Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-File',('"'+$startScript+'"')) -WorkingDirectory $instance -PassThru -RedirectStandardOutput (Join-Path $instance 'app.log') -RedirectStandardError (Join-Path $instance 'error.log')
try {
    $ready=$false
    for($i=0;$i -lt 90;$i++) {
        if($process.HasExited) { Get-Content -LiteralPath (Join-Path $instance 'error.log'); throw 'Packaged launcher failed to start.' }
        try { $response=Invoke-WebRequest 'http://127.0.0.1:8080/health' -UseBasicParsing; if($response.StatusCode -eq 200) {$ready=$true;break} } catch {}
        Start-Sleep -Seconds 1
    }
    if(-not $ready) { throw 'Packaged launcher health timeout.' }
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
    Write-Host 'Packaged Windows runtime, actual launcher, static app, callback, private ACLs, DPAPI and authenticated banking checks passed using synthetic data.'
} finally {
    $env:LOCALAPPDATA=$originalLocalAppData
    if(-not $process.HasExited){ & taskkill.exe /PID $process.Id /T /F | Out-Null; $process.WaitForExit() }
    Remove-Item -LiteralPath $instance -Recurse -Force
}
