$ErrorActionPreference='Stop'
$task=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$lab=Join-Path $task 'lab-runtime'
$classes=Join-Path $task 'verification/classes'
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$cp=@(Join-Path $task 'repo/build/libs/EcoEnchants-2026.27-1-all.jar')
$cp+=@(Get-ChildItem -LiteralPath (Join-Path $lab 'libraries') -Recurse -Filter '*.jar' | ForEach-Object FullName)
$cp+=@(Get-ChildItem -LiteralPath (Join-Path $lab 'plugins') -Filter '*.jar' | ForEach-Object FullName)
$cp+=@(Get-ChildItem -LiteralPath (Join-Path $lab 'versions') -Recurse -Filter '*.jar' | ForEach-Object FullName)
& 'D:/java/bin/javac.exe' -encoding UTF-8 -cp ($cp -join ';') -d $classes (Join-Path $PSScriptRoot 'EnchantProbe.java')
if($LASTEXITCODE -ne 0){throw 'Probe compilation failed'}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'plugin.yml') -Destination (Join-Path $classes 'plugin.yml')
& 'D:/java/bin/jar.exe' --create --file (Join-Path $task 'verification/EnchantProbe.jar') -C $classes .
if($LASTEXITCODE -ne 0){throw 'Probe archive failed'}
Copy-Item -LiteralPath (Join-Path $task 'verification/EnchantProbe.jar') -Destination (Join-Path $lab 'plugins/EnchantProbe.jar')
