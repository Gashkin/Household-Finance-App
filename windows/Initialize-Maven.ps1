param()
$ErrorActionPreference='Stop'
$workspace=[IO.Path]::GetFullPath((Split-Path $PSScriptRoot))
$pins=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'build-toolchain.json') -Raw | ConvertFrom-Json
$destination=[IO.Path]::GetFullPath((Join-Path $workspace '.tools/release-maven'))
if (-not $destination.StartsWith($workspace+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Tool destination escapes checkout.' }
foreach($path in @($workspace,(Join-Path $workspace '.tools'))) {
    if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Build tools cannot traverse reparse points.' }
}
if(Test-Path -LiteralPath $destination) { throw 'Choose a clean checkout; Maven tool destination already exists.' }
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$archive=Join-Path $destination 'maven.zip'
Invoke-WebRequest -Uri $pins.mavenArchive -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash.ToLowerInvariant() -cne $pins.mavenSha512) { throw 'Maven checksum mismatch; extraction refused.' }
Expand-Archive -LiteralPath $archive -DestinationPath $destination
$bin=Join-Path $destination ('apache-maven-'+$pins.mavenVersion+'/bin')
if($env:GITHUB_PATH) { $bin | Add-Content -LiteralPath $env:GITHUB_PATH }
Write-Output $bin
