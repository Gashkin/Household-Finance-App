. "$PSScriptRoot\Common.ps1"
$root = Get-InstanceRoot
$config = Read-PrivateConfig $root
$manifest = Get-PackageManifest $PSScriptRoot
$mutex = New-Object Threading.Mutex($false, 'Local\HouseholdFinance-Instance')
if (-not $mutex.WaitOne(0)) { throw 'Household Finance is already running.' }
try {
    if ($config.Production) {
        Assert-ProductionReleaseCleared $manifest
        Assert-StandardRuntimeIdentity | Out-Null
        Test-EncryptedDevice $root
    }
    $data = Join-Path $root 'data'
    if (-not (Test-Path -LiteralPath $data)) { New-Item -ItemType Directory -Path $data | Out-Null; Set-PrivatePath $data $true }
    Assert-PrivatePath $data
    # Override inherited development settings; no sandbox or model/network exposure in the release.
    $env:BANK_SANDBOX_ENABLED='false'; $env:BANK_PRODUCTION_ENABLED='false'; $env:EVIDENCE_AI_ENABLED='false'
    $env:SERVER_ADDRESS='127.0.0.1'; $env:SERVER_PORT='8080'; $env:WORKSPACE_ORIGINS='http://127.0.0.1:8080'
    $env:WORKSPACE_PASSWORD=Get-PlainSecret $config.Password
    $env:SPRING_DATASOURCE_URL='jdbc:h2:file:' + (Join-Path $data 'household-finance').Replace('\','/') + ';DB_CLOSE_ON_EXIT=FALSE'
    $env:SPRING_DATASOURCE_USERNAME='sa'; $env:SPRING_DATASOURCE_PASSWORD=''
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
    Write-Host 'Open http://127.0.0.1:8080 in your browser. Keep this window open; Ctrl+C stops the instance.'
    & (Join-Path $PSScriptRoot 'runtime\bin\java.exe') -jar (Join-Path $PSScriptRoot 'app.jar')
    if ($LASTEXITCODE -ne 0) { throw 'Application stopped with an error. Keep the data directory; follow the recovery guide.' }
} finally {
    foreach ($name in @('WORKSPACE_PASSWORD','BANK_PRODUCTION_APP_ID','BANK_PRODUCTION_KEY_PATH','BANK_PRODUCTION_CALLBACK','BANK_PRODUCTION_STORE_PATH','BANK_PRODUCTION_STORAGE_KEY','BANK_PRODUCTION_ENABLED','BANK_PRODUCTION_READINESS','SPRING_DATASOURCE_URL')) { [Environment]::SetEnvironmentVariable($name,$null,'Process') }
    $mutex.ReleaseMutex(); $mutex.Dispose()
}
