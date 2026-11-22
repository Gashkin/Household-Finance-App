param([string]$Output = (Join-Path $PSScriptRoot '..\release'))
$ErrorActionPreference = 'Stop'
$source = Split-Path $PSScriptRoot
function Get-SafeBuildOutput([string]$Relative) {
    $workspace=[IO.Path]::GetFullPath($source)
    $target=[IO.Path]::GetFullPath((Join-Path $workspace $Relative))
    if (-not $target.StartsWith($workspace+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Build output escapes the checkout.' }
    $ancestor=$target
    while ($ancestor -and $ancestor.StartsWith($workspace,[StringComparison]::OrdinalIgnoreCase)) {
        if (Test-Path -LiteralPath $ancestor) {
            if ((Get-Item -LiteralPath $ancestor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Build output must not traverse reparse points.' }
        }
        if ($ancestor -eq $workspace) { break }
        $ancestor=Split-Path $ancestor
    }
    $target
}
Push-Location $source
try {
    # Reject a dirty checkout before packaging so the manifest's commit matches
    # the actual reviewed source; generated build files are ignored by Git.
    $dirty = @(git status --porcelain=v1 --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw 'Could not verify release checkout cleanliness.' }
    if ($dirty.Count -ne 0) { throw 'Refusing to package an uncommitted or untracked checkout; use a clean exact commit.' }
    $commit = (git rev-parse --verify HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $commit -notmatch '^[0-9a-f]{40}$') { throw 'Could not determine the reviewed release commit.' }
    $pins=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'build-toolchain.json') -Raw | ConvertFrom-Json
    $javaRelease=Get-Content -LiteralPath (Join-Path $env:JAVA_HOME 'release') -Raw
    if ($javaRelease -notmatch ('(?m)^JAVA_RUNTIME_VERSION="'+[regex]::Escape($pins.javaRuntimeVersion)+'"\r?$') -or
        $javaRelease -notmatch ('(?m)^IMPLEMENTOR="'+[regex]::Escape($pins.javaVendor)+'"\r?$')) { throw 'Use the reviewed Java runtime from build-toolchain.json.' }
    $nodeVersion=(& node --version).Trim()
    if ($LASTEXITCODE -ne 0 -or $nodeVersion -cne $pins.nodeVersion) { throw 'Use the reviewed Node runtime from build-toolchain.json.' }
    $mavenInfo=(& mvn -v | Out-String)
    if ($LASTEXITCODE -ne 0 -or $mavenInfo -notmatch ('Apache Maven '+[regex]::Escape($pins.mavenVersion)+'\b')) { throw 'Use the reviewed Maven runtime from build-toolchain.json.' }
    # Git cleanliness does not clear ignored output. Remove only checked build
    # paths before any compiler or Vite configuration can consume stale files.
    foreach ($relative in @('frontend/dist','frontend/tsconfig.app.tsbuildinfo','frontend/tsconfig.node.tsbuildinfo','frontend/vite.config.js','frontend/vite.config.d.ts')) {
        $generated=Get-SafeBuildOutput $relative
        if (Test-Path -LiteralPath $generated) { Remove-Item -LiteralPath $generated -Recurse -Force }
    }
    Get-SafeBuildOutput 'frontend/node_modules' | Out-Null
    Get-SafeBuildOutput 'backend/target' | Out-Null
    Push-Location frontend
    try { npm ci; if ($LASTEXITCODE) { throw 'npm ci failed' }; npm run build; if ($LASTEXITCODE) { throw 'Frontend build failed' } }
    finally { Pop-Location }
    # Ignored classes/resources must not survive an earlier local build and
    # masquerade as output from the manifest's exact source commit (audit F7).
    & mvn -s .mvn/settings.xml -B -ntp -f backend/pom.xml clean test
    if ($LASTEXITCODE) { throw 'Backend tests failed' }
    $static = Get-SafeBuildOutput 'backend/target/classes/static'
    if (Test-Path -LiteralPath $static) { Remove-Item -LiteralPath $static -Recurse -Force }
    New-Item -ItemType Directory -Path $static | Out-Null
    Copy-Item -Path 'frontend\dist\*' -Destination $static -Recurse
    & mvn -s .mvn/settings.xml -B -ntp -f backend/pom.xml package org.springframework.boot:spring-boot-maven-plugin:4.1.2:repackage -DskipTests
    if ($LASTEXITCODE) { throw 'Backend packaging failed' }
    if (Test-Path -LiteralPath $Output) { throw 'Choose an empty release destination.' }
    New-Item -ItemType Directory -Path $Output | Out-Null
    Copy-Item -LiteralPath 'backend\target\household-finance-backend-0.0.1-SNAPSHOT.jar' -Destination (Join-Path $Output 'app.jar')
    foreach($file in @('Common.ps1','Install.ps1','Start.ps1','Readiness.ps1','Test-IdentityBoundary.ps1','Remove-LocalData.ps1','Disable-Production.ps1')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination $Output }
    Copy-Item -LiteralPath 'product\stories\persistent-aib-instance.md' -Destination (Join-Path $Output 'Operating-guide.md')
    # The dedicated MCP listener uses the JDK HTTP server, including when Spring
    # inspects its disabled-by-default bean. Keep it in the bundled runtime.
    & (Join-Path $env:JAVA_HOME 'bin\jlink.exe') --add-modules java.se,jdk.httpserver,jdk.crypto.ec,jdk.unsupported,jdk.charsets --strip-debug --no-header-files --no-man-pages --output (Join-Path $Output 'runtime')
    if ($LASTEXITCODE) { throw 'Windows runtime packaging failed' }
    $absolute = (Get-Item -LiteralPath $Output).FullName
    $files = @(Get-ChildItem -LiteralPath $Output -Recurse -File | Sort-Object FullName | ForEach-Object { @{path=$_.FullName.Substring($absolute.Length+1).Replace('\','/'); sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash} })
    # Do not remove the hold until actual-device/entitlement/containment evidence
    # and the owner-requested independent review sequence have been completed.
    @{version=1;commit=$commit;productionEligible=$false;toolchain=@{java=$pins.javaRuntimeVersion;node=$nodeVersion;maven=$pins.mavenVersion};files=$files} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $Output 'manifest.json') -Encoding UTF8
    Compress-Archive -Path (Join-Path $Output '*') -DestinationPath ($absolute+'.zip')
    Write-Host 'Windows release packaged with a private local runtime. Installation and real-device readiness remain separate checks.'
} finally { Pop-Location }
