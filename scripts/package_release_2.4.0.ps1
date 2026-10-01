$ErrorActionPreference = "Stop"

$releaseDir = "build\release_assets_2.4.0"
if (-not (Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
}

Write-Host "Copying installer files..."
Copy-Item "build\compose\binaries\main\exe\LegadoDesktop-2.4.0.exe" -Destination "$releaseDir\LegadoDesktop-2.4.0-Setup.exe" -Force
Copy-Item "build\compose\binaries\main\msi\LegadoDesktop-2.4.0.msi" -Destination "$releaseDir\LegadoDesktop-2.4.0-Setup.msi" -Force
Copy-Item "build\compose\jars\LegadoDesktop-windows-x64-2.4.0.jar" -Destination "$releaseDir\LegadoDesktop-2.4.0-all.jar" -Force

$appFolder = "build\compose\binaries\main\app\LegadoDesktop"

Write-Host "Compressing portable zip..."
$portableZip = "$releaseDir\LegadoDesktop-2.4.0-Windows-x64.portable.zip"
if (Test-Path $portableZip) { Remove-Item -Force $portableZip }
Compress-Archive -Path "$appFolder\*" -DestinationPath $portableZip -Force

Write-Host "Creating ultralight package..."
$ultralightDir = "build\ultralight_tmp_2.4.0"
if (Test-Path $ultralightDir) { Remove-Item -Recurse -Force $ultralightDir }
New-Item -ItemType Directory -Force -Path "$ultralightDir\app" | Out-Null
Copy-Item "$appFolder\app\*" -Destination "$ultralightDir\app" -Recurse -Force
Copy-Item "$appFolder\LegadoDesktop.exe" -Destination "$ultralightDir\" -Force

$ultralightZip = "$releaseDir\LegadoDesktop-2.4.0-Windows-x64.ultralight.zip"
if (Test-Path $ultralightZip) { Remove-Item -Force $ultralightZip }
Compress-Archive -Path "$ultralightDir\*" -DestinationPath $ultralightZip -Force
Remove-Item -Recurse -Force $ultralightDir

Write-Host "Release assets assembled successfully:"
Get-ChildItem $releaseDir | Select-Object Name, Length
