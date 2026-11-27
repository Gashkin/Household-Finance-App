param([ValidateRange(1024,65535)][int]$Port=8080, [switch]$DisableLocalSuggestions)
. "$PSScriptRoot\Common.ps1"
$root = Get-InstanceRoot
$manifest = Get-PackageManifest $PSScriptRoot
$mutex = New-Object Threading.Mutex($false, 'Local\HouseholdFinance-Instance')
if (-not $mutex.WaitOne(0)) { throw 'Household Finance is already running.' }
$modelProcess = $null
try {
    # Optional inference is a sibling process with its own ephemeral credential.
    [Environment]::SetEnvironmentVariable('FINANCE_INFERENCE_KEY',$null,'Process')
    $modelDirectory = Join-Path $root 'inference'
    if (!$DisableLocalSuggestions -and [Environment]::UserName -eq 'Dev Server One' -and (Test-Path -LiteralPath $modelDirectory -PathType Container)) {
      try {
        Assert-StandardRuntimeIdentity | Out-Null
        Assert-PrivatePath $modelDirectory
        $modelFile = Join-Path $modelDirectory 'qwen2.5-1.5b-instruct-q4_k_m.gguf'
        if ((Get-FileHash -LiteralPath $modelFile).Hash -ne '6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e') { throw 'Unexpected model checksum.' }
        if (Get-NetTCPConnection -State Listen -LocalPort 18737 -ErrorAction SilentlyContinue) { throw 'Inference port 18737 is occupied.' }
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $bytes = New-Object byte[] 32; $random.GetBytes($bytes); $env:LLAMA_API_KEY = [Convert]::ToBase64String($bytes) } finally { $random.Dispose() }
        $env:FINANCE_INFERENCE_KEY = $env:LLAMA_API_KEY
        $env:FINANCE_INFERENCE_PORT = '18737'
        $modelProcess = Start-Process -FilePath (Join-Path $modelDirectory 'llama-server.exe') -WorkingDirectory $modelDirectory -WindowStyle Hidden -PassThru -ArgumentList @('-m','qwen2.5-1.5b-instruct-q4_k_m.gguf','--alias','qwen2.5-1.5b-instruct-q4_k_m','--host','127.0.0.1','--port','18737','--parallel','1','-c','4096','-t','4','--offline','--no-ui','--no-ui-mcp-proxy','--log-disable')
        $timer=[Diagnostics.Stopwatch]::StartNew()
        do {
            if ($modelProcess.HasExited -or $timer.Elapsed.TotalSeconds -gt 60) { throw 'Local inference startup failed.' }
            Start-Sleep -Milliseconds 250
            $listener = Get-NetTCPConnection -State Listen -LocalPort 18737 -ErrorAction SilentlyContinue
        } until ($listener)
        if ($listener.OwningProcess -ne $modelProcess.Id -or $listener.LocalAddress -ne '127.0.0.1') { throw 'Unexpected inference listener.' }
      } catch {
        if ($modelProcess -and !$modelProcess.HasExited) { $modelProcess.Kill(); $modelProcess.WaitForExit() }
        [Environment]::SetEnvironmentVariable('FINANCE_INFERENCE_KEY',$null,'Process')
        [Environment]::SetEnvironmentVariable('LLAMA_API_KEY',$null,'Process')
        Write-Warning 'Local inference could not start. Manual review remains available. Check the installed inference files and port 18737.'
      }
    }
    # Read after the lock: readiness/disable must not race the launch decision.
    $config = Read-PrivateConfig $root
    if ($config.Production) {
        Assert-ProductionReleaseCleared $manifest
        if ($Port -ne 8080) { throw 'Production callback requires port 8080.' }
        Assert-StandardRuntimeIdentity | Out-Null
        Test-EncryptedDevice $root
    }
    $data = Join-Path $root 'data'
    if (-not (Test-Path -LiteralPath $data)) { New-Item -ItemType Directory -Path $data | Out-Null; Set-PrivatePath $data $true }
    Assert-PrivatePath $data
    # Override inherited development settings; no sandbox or model/network exposure in the release.
    $env:BANK_SANDBOX_ENABLED='false'; $env:BANK_PRODUCTION_ENABLED='false'; $env:BANK_PRODUCTION_READINESS='false'; $env:EVIDENCE_AI_ENABLED='false'
    $env:SERVER_ADDRESS='127.0.0.1'; $env:SERVER_PORT=[string]$Port; $env:WORKSPACE_ORIGINS="http://127.0.0.1:$Port"
    $env:WORKSPACE_PASSWORD=Get-PlainSecret $config.Password
    $env:SPRING_DATASOURCE_URL='jdbc:h2:file:' + (Join-Path $data 'household-finance').Replace('\','/') + ';WRITE_DELAY=0'
    $env:SPRING_DATASOURCE_USERNAME='sa'; $env:SPRING_DATASOURCE_PASSWORD=''
    $env:SPRING_JPA_HIBERNATE_DDL_AUTO='validate'
    $env:SPRING_FLYWAY_ENABLED='true'; $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE='false'
    $env:SPRING_FLYWAY_CLEAN_DISABLED='true'; $env:SPRING_FLYWAY_VALIDATE_ON_MIGRATE='true'
    if ($config.Production) {
        if ($config.ReadinessCommit -ne $manifest.commit -or [DateTimeOffset]::Parse($config.ReadinessAt).AddDays(7) -lt [DateTimeOffset]::UtcNow) { throw 'Production readiness is missing, expired, or belongs to a different release. Run Readiness.ps1 before using bank data.' }
        Assert-PrivatePath $config.KeyPath
        $env:BANK_PRODUCTION_APP_ID=$config.AppId; $env:BANK_PRODUCTION_KEY_PATH=$config.KeyPath
        $env:BANK_PRODUCTION_STORAGE_KEY=Get-PlainSecret $config.StorageKey
        $env:BANK_PRODUCTION_STORE_PATH=Join-Path $data 'bank-connection.bin'
        $env:BANK_PRODUCTION_CALLBACK='http://127.0.0.1:8080/bank/callback'
        $env:BANK_PRODUCTION_READINESS='true'; $env:BANK_PRODUCTION_ENABLED='true'
    }
    Set-Location $root
    # Foreground process: closing this window stops the local instance. No background sync/service.
    Write-Host "Open http://127.0.0.1:$Port in your browser. Keep this window open; Ctrl+C stops the instance."
    & (Join-Path $PSScriptRoot 'runtime\bin\java.exe') -jar (Join-Path $PSScriptRoot 'app.jar') '--spring.flyway.ignore-migration-patterns=' '--workbook.import.enabled=false'
    if ($LASTEXITCODE -ne 0) { throw 'Application stopped with an error. Keep the data directory; follow the recovery guide.' }
} finally {
    if ($modelProcess -and !$modelProcess.HasExited) { $modelProcess.Kill(); $modelProcess.WaitForExit() }
    foreach ($name in @('LLAMA_API_KEY','FINANCE_INFERENCE_KEY','FINANCE_INFERENCE_PORT','WORKSPACE_PASSWORD','BANK_PRODUCTION_APP_ID','BANK_PRODUCTION_KEY_PATH','BANK_PRODUCTION_CALLBACK','BANK_PRODUCTION_STORE_PATH','BANK_PRODUCTION_STORAGE_KEY','BANK_PRODUCTION_ENABLED','BANK_PRODUCTION_READINESS','SPRING_DATASOURCE_URL')) { [Environment]::SetEnvironmentVariable($name,$null,'Process') }
    $mutex.ReleaseMutex(); $mutex.Dispose()
}
