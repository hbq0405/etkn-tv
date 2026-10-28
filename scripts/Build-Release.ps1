param([Parameter(Mandatory=$true)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
if(-not $env:ETKN_TV_SIGNING_PROPERTIES -or -not (Test-Path -LiteralPath $env:ETKN_TV_SIGNING_PROPERTIES)){throw 'Set ETKN_TV_SIGNING_PROPERTIES to your existing private signing properties file.'}
if(-not $env:ANDROID_HOME){throw 'Set ANDROID_HOME to your Android SDK.'}
Push-Location $repo
try {
    & .\gradlew.bat :app:assembleRelease :app:lintRelease :app:testReleaseUnitTest --console=plain
    if($LASTEXITCODE -ne 0){throw 'Release checks failed.'}
    $tools=Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
    $apk=Join-Path $repo 'app/build/outputs/apk/release/app-release.apk'
    & (Join-Path $tools.FullName 'apksigner.bat') verify --verbose --print-certs $apk
    if($LASTEXITCODE -ne 0){throw 'APK signature verification failed.'}
    $config=Get-Content -LiteralPath (Join-Path $repo 'app/build.gradle') -Raw
    $version=[regex]::Match($config,"versionName '([^']+)'").Groups[1].Value
    $code=[int][regex]::Match($config,'versionCode (\d+)').Groups[1].Value
    if(-not $version -or $code -lt 1){throw 'Missing release version.'}
    $out=New-Item -ItemType Directory -Path $OutputDirectory -Force
    $name="etkn-tv-$version.apk"
    Copy-Item -LiteralPath $apk -Destination (Join-Path $out.FullName $name)
    $hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest=[ordered]@{packageName='xyz.etkn.tv';versionCode=$code;versionName=$version;size=(Get-Item -LiteralPath $apk).Length;apkUrl="https://github.com/hbq0405/etkn-tv/releases/download/v$version/$name";sha256=$hash;releaseNotes="ETKN TV $version"}
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out.FullName 'update.json') -Encoding utf8NoBOM
    $manifestHash=(Get-FileHash -LiteralPath (Join-Path $out.FullName 'update.json') -Algorithm SHA256).Hash.ToLowerInvariant()
    @("$hash  $name","$manifestHash  update.json") | Set-Content -LiteralPath (Join-Path $out.FullName 'SHA256SUMS.txt') -Encoding utf8NoBOM
    Write-Output "Verified release assets saved to $($out.FullName). Review release notes before publishing."
} finally {Pop-Location}
