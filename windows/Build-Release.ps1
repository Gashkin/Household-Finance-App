param([string]$Output = (Join-Path $PSScriptRoot '..\release'))
$ErrorActionPreference = 'Stop'
$source = Split-Path $PSScriptRoot
Push-Location $source
try {
    # A release must represent exactly the reviewed commit, not local source edits.
    # Run before npm/Maven generates build outputs; untracked source is rejected too.
    $dirty = @(git status --porcelain=v1 --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify release checkout cleanliness.' }
    if ($dirty.Count -ne 0) { throw 'Refusing to package an uncommitted or untracked checkout; build from a clean exact commit.' }
    $commit = (git rev-parse --verify HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}
    Push-Location frontend
    try { npm ci; if ($LASTEXITCODE) { throw 'npm ci failed' }; npm run build; if ($LASTEXITCODE) { throw 'Frontend build failed' } }
    finally { Pop-Location }
    & mvn -B -ntp -f backend/pom.xml test
    if ($LASTEXITCODE) { throw 'Backend tests failed' }
    $static = Join-Path $source 'backend\target\classes\static'
    if (Test-Path -LiteralPath $static) { Remove-Item -LiteralPath $static -Recurse -Force }
    New-Item -ItemType Directory -Path $static | Out-Null
    Copy-Item -Path 'frontend\dist\*' -Destination $static -Recurse
    & mvn -B -ntp -f backend/pom.xml package org.springframework.boot:spring-boot-maven-plugin:3.2.5:repackage -DskipTests
    if ($LASTEXITCODE) { throw 'Backend packaging failed' }
    if (Test-Path -LiteralPath $Output) { throw 'Choose an empty release destination.' }
    New-Item -ItemType Directory -Path $Output | Out-Null
    Copy-Item -LiteralPath 'backend\target\household-finance-backend-0.0.1-SNAPSHOT.jar' -Destination (Join-Path $Output 'app.jar')
    foreach($file in @('Common.ps1','Install.ps1','Start.ps1','Readiness.ps1','Test-IdentityBoundary.ps1','Remove-LocalData.ps1','Disable-Production.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $Output }
    Copy-Item -LiteralPath 'product\stories\persistent-aib-instance.md' -Destination (Join-Path $Output 'Operating-guide.md')
    & (Join-Path $env:JAVA_HOME 'bin\jlink.exe') --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.charsets --strip-debug --no-header-files --no-man-pages --output (Join-Path $Output 'runtime')
    if ($LASTEXITCODE) { throw 'Windows runtime packaging failed' }
    $absolute = (Get-Item -LiteralPath $Output).FullName
    $files = @(Get-ChildItem -LiteralPath $Output -Recurse -File | Sort-Object FullName | ForEach-Object { @{path=$_.FullName.Substring($absolute.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    # Do not remove the hold until actual-device/entitlement/containment evidence
    # and the owner-requested independent review sequence have been completed.
    @{version=1;commit=$commit;productionEligible=$false;files=$files} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Output 'manifest.json') -Encoding UTF8
    Compress-Archive -Path (Join-Path $Output '*') -DestinationPath ($absolute+'.zip')
    Write-Host 'Windows release packaged with a private local runtime. Installation and real-device readiness remain separate checks.'
} finally { Pop-Location }
) { throw 'Could not determine the reviewed release commit.' }
    Push-Location frontend
    try { npm ci; if ($LASTEXITCODE) { throw 'npm ci failed' }; npm run build; if ($LASTEXITCODE) { throw 'Frontend build failed' } }
    finally { Pop-Location }
    & mvn -B -ntp -f backend/pom.xml test
    if ($LASTEXITCODE) { throw 'Backend tests failed' }
    $static = Join-Path $source 'backend\target\classes\static'
    if (Test-Path -LiteralPath $static) { Remove-Item -LiteralPath $static -Recurse -Force }
    New-Item -ItemType Directory -Path $static | Out-Null
    Copy-Item -Path 'frontend\dist\*' -Destination $static -Recurse
    & mvn -B -ntp -f backend/pom.xml package org.springframework.boot:spring-boot-maven-plugin:3.2.5:repackage -DskipTests
    if ($LASTEXITCODE) { throw 'Backend packaging failed' }
    if (Test-Path -LiteralPath $Output) { throw 'Choose an empty release destination.' }
    New-Item -ItemType Directory -Path $Output | Out-Null
    Copy-Item -LiteralPath 'backend\target\household-finance-backend-0.0.1-SNAPSHOT.jar' -Destination (Join-Path $Output 'app.jar')
    foreach($file in @('Common.ps1','Install.ps1','Start.ps1','Readiness.ps1','Test-IdentityBoundary.ps1','Remove-LocalData.ps1','Disable-Production.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $Output }
    Copy-Item -LiteralPath 'product\stories\persistent-aib-instance.md' -Destination (Join-Path $Output 'Operating-guide.md')
    & (Join-Path $env:JAVA_HOME 'bin\jlink.exe') --add-modules java.se,jdk.crypto.ec,jdk.unsupported,jdk.charsets --strip-debug --no-header-files --no-man-pages --output (Join-Path $Output 'runtime')
    if ($LASTEXITCODE) { throw 'Windows runtime packaging failed' }
    $absolute = (Get-Item -LiteralPath $Output).FullName
    $files = @(Get-ChildItem -LiteralPath $Output -Recurse -File | Sort-Object FullName | ForEach-Object { @{path=$_.FullName.Substring($absolute.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    # Do not remove the hold until actual-device/entitlement/containment evidence
    # and the owner-requested independent review sequence have been completed.
    @{version=1;commit=$commit;productionEligible=$false;files=$files} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Output 'manifest.json') -Encoding UTF8
    Compress-Archive -Path (Join-Path $Output '*') -DestinationPath ($absolute+'.zip')
    Write-Host 'Windows release packaged with a private local runtime. Installation and real-device readiness remain separate checks.'
} finally { Pop-Location }
