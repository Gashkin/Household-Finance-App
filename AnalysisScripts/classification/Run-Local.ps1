param([Parameter(Mandatory)][string]$RuntimeDirectory)
$ErrorActionPreference='Stop'
$server=Join-Path $RuntimeDirectory 'llama-server.exe'
$model=Join-Path $RuntimeDirectory 'qwen2.5-1.5b-instruct-q4_k_m.gguf'
if ((Get-FileHash -LiteralPath $model).Hash -ne '6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e'){throw 'Unexpected model checksum'}
if(Get-NetTCPConnection -LocalPort 18737 -State Listen -ErrorAction SilentlyContinue){throw 'Inference port is occupied'}
$env:LLAMA_API_KEY=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:FINANCE_INFERENCE_KEY=$env:LLAMA_API_KEY
$timer=[Diagnostics.Stopwatch]::StartNew()
$p=Start-Process -FilePath $server -WorkingDirectory $RuntimeDirectory -ArgumentList @('-m','qwen2.5-1.5b-instruct-q4_k_m.gguf','--alias','qwen2.5-1.5b-instruct-q4_k_m','--host','127.0.0.1','--port','18737','--parallel','1','-c','4096','-t','4','--offline','--no-ui','--no-ui-mcp-proxy','--log-disable') -WindowStyle Hidden -PassThru
try {
  do {
    if($p.HasExited -or $timer.Elapsed.TotalSeconds -gt 60){throw 'Model startup failed'}
    Start-Sleep -Milliseconds 200
    try {$ready=(Invoke-RestMethod 'http://127.0.0.1:18737/health' -TimeoutSec 1).status -eq 'ok'} catch {$ready=$false}
  } until ($ready)
  $startup=$timer.ElapsedMilliseconds
  $challenge=Invoke-WebRequest -Uri 'http://127.0.0.1:18737/v1/chat/completions' -Method Post -Body '{}' -ContentType 'application/json' -SkipHttpErrorCheck
  if($challenge.StatusCode -ne 401){throw 'Inference authentication check failed'}
  $python=Get-Command python -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
  if($env:GBD37_PYTHON){$exe=$env:GBD37_PYTHON}else{$exe=$python.Source}
  & $exe (Join-Path $PSScriptRoot 'benchmark.py') local
  if($LASTEXITCODE -ne 0){throw 'Local benchmark failed'}
  $p.Refresh()
  Write-Output "StartupMs=$startup PeakWorkingSetBytes=$($p.PeakWorkingSet64) UnauthenticatedStatus=$($challenge.StatusCode)"
} finally {
  if(!$p.HasExited){$p.Kill();$p.WaitForExit()}
  Remove-Item Env:LLAMA_API_KEY,Env:FINANCE_INFERENCE_KEY
}
