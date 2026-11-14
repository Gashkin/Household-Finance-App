# Run from your private local checkout. No key content is printed or placed in arguments.
$ErrorActionPreference = 'Stop'
$applicationId = Read-Host 'SANDBOX application UUID (from Enable Banking control panel)'
$parsedApplicationId = [Guid]::Empty
if (-not [Guid]::TryParse($applicationId, [ref]$parsedApplicationId)) { throw 'Invalid application UUID' }
$sourcePath = Read-Host 'Full path to your locally downloaded PKCS#8 private PEM file'
$sourceFile = Get-Item -LiteralPath $sourcePath
if (($sourceFile.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $sourceFile.Length -gt 16384) { throw 'Unsupported key file' }
$privateFolder = Join-Path $env:LOCALAPPDATA 'HouseholdFinance\secrets'
New-Item -ItemType Directory -Force -Path $privateFolder | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$folderAcl = New-Object Security.AccessControl.DirectorySecurity
$folderAcl.SetOwner((New-Object Security.Principal.NTAccount($identity)))
$folderAcl.SetAccessRuleProtection($true, $false)
$folderAcl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')))
Set-Acl -LiteralPath $privateFolder -AclObject $folderAcl
$targetPath = Join-Path $privateFolder 'enable-banking-sandbox.pem'
if (Test-Path -LiteralPath $targetPath) { throw 'A signing key already exists. Rotate/revoke it deliberately before replacing it.' }
Copy-Item -LiteralPath $sourceFile.FullName -Destination $targetPath
$fileAcl = New-Object Security.AccessControl.FileSecurity
$fileAcl.SetOwner((New-Object Security.Principal.NTAccount($identity)))
$fileAcl.SetAccessRuleProtection($true, $false)
$fileAcl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity, 'Read,Write', 'Allow')))
Set-Acl -LiteralPath $targetPath -AclObject $fileAcl
$env:BANK_SANDBOX_ENABLED = 'true'
$env:BANK_SANDBOX_APP_ID = $parsedApplicationId.ToString()
$env:BANK_SANDBOX_KEY_PATH = $targetPath
$env:BANK_SANDBOX_CALLBACK = 'http://127.0.0.1:5173/bank/callback'
Write-Host 'Private sandbox setup complete for this PowerShell session. Start the backend in this shell; then use Money position to check metadata and connect Mock ASPSP. Review and securely remove the original downloaded copy separately.'
