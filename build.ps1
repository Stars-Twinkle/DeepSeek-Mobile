# =========================================================
#  build.ps1 - zero-Gradle APK build
#  aapt2 -> javac -> d8 -> package -> zipalign -> apksigner
#  Usage: powershell -ExecutionPolicy Bypass -File build.ps1 [-NoInstall]
# =========================================================
param(
    [switch]$NoInstall
)

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot

# ---------- toolchain ----------
$sdk        = 'D:\.tools\android-sdk'
$androidJar = Join-Path $sdk 'platforms\android-36\android.jar'
$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Select-Object -First 1 -ExpandProperty FullName
$aapt2      = Join-Path $buildTools 'aapt2.exe'
$zipalign   = Join-Path $buildTools 'zipalign.exe'
$apksigner  = Join-Path $buildTools 'apksigner.bat'
$d8         = Join-Path $buildTools 'd8.bat'

$jdk18 = 'C:\Program Files\BellSoft\LibericaJDK-18-Full\bin'
if (Test-Path (Join-Path $jdk18 'javac.exe')) {
    $javac   = Join-Path $jdk18 'javac.exe'
    $keytool = Join-Path $jdk18 'keytool.exe'
} else {
    $javac   = 'javac'
    $keytool = 'keytool'
}

foreach ($t in @($androidJar, $aapt2, $zipalign, $apksigner, $d8)) {
    if (-not (Test-Path $t)) { throw "missing tool: $t" }
}

$srcDir    = Join-Path $root 'app\src\main'
$outDir    = Join-Path $root 'build'
$genDir    = Join-Path $outDir 'gen'
$objDir    = Join-Path $outDir 'obj'
$dexDir    = Join-Path $outDir 'dex'
$resDir    = Join-Path $srcDir 'res'
$assetsDir = Join-Path $srcDir 'assets'
$manifest  = Join-Path $srcDir 'AndroidManifest.xml'
$appName   = 'deepseek-mobile'

Write-Host "== clean" -ForegroundColor Cyan
foreach ($d in @($genDir, $objDir, $dexDir)) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

# ---------- 1. compile resources ----------
Write-Host "== aapt2 compile" -ForegroundColor Cyan
$flat = Join-Path $outDir 'res.flata'
if (Test-Path $flat) { Remove-Item $flat -Force }
& $aapt2 compile --dir $resDir -o $flat
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# ---------- 2. link resources + R.java ----------
Write-Host "== aapt2 link" -ForegroundColor Cyan
$unsigned = Join-Path $outDir 'unsigned.apk'
if (Test-Path $unsigned) { Remove-Item $unsigned -Force }
$linkArgs = @(
    'link', '-o', $unsigned,
    '-I', $androidJar,
    '--manifest', $manifest,
    '--java', $genDir,
    '--min-sdk-version', '24',
    '--target-sdk-version', '36',
    '--version-code', '1',
    '--version-name', '1.0.0',
    '--auto-add-overlay'
)
if (Test-Path $assetsDir) { $linkArgs += @('-A', $assetsDir) }
$linkArgs += $flat
& $aapt2 @linkArgs
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ---------- 3. javac ----------
Write-Host "== javac" -ForegroundColor Cyan
$javaFiles = @()
$javaFiles += Get-ChildItem (Join-Path $srcDir 'java') -Recurse -Filter *.java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
$javaFiles += Get-ChildItem $genDir -Recurse -Filter *.java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName
if ($javaFiles.Count -eq 0) { throw "no java sources found" }

$argFile = Join-Path $outDir 'sources.txt'
$javaFiles | ForEach-Object { '"' + ($_ -replace '\\', '\\\\') + '"' } | Set-Content $argFile -Encoding ASCII

$javacArgs = @(
    '-encoding', 'UTF-8',
    '-source', '8', '-target', '8',
    '-classpath', $androidJar,
    '-d', $objDir,
    '-nowarn',
    "@$argFile"
)
& $javac @javacArgs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# ---------- 4. dex ----------
Write-Host "== d8" -ForegroundColor Cyan
$classFiles = Get-ChildItem $objDir -Recurse -Filter *.class | Select-Object -ExpandProperty FullName
& $d8 --release --lib $androidJar --min-api 24 --output $dexDir @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

# ---------- 5. add dex into apk ----------
Write-Host "== package dex" -ForegroundColor Cyan
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($unsigned, 'Update')
Get-ChildItem $dexDir -Filter *.dex | ForEach-Object {
    $entry = $zip.GetEntry($_.Name)
    if ($entry) { $entry.Delete() }
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $zip, $_.FullName, $_.Name, [System.IO.Compression.CompressionLevel]::Optimal)
}
$zip.Dispose()

# ---------- 6. zipalign ----------
Write-Host "== zipalign" -ForegroundColor Cyan
$aligned = Join-Path $outDir 'aligned.apk'
if (Test-Path $aligned) { Remove-Item $aligned -Force }
& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ---------- 7. sign ----------
Write-Host "== sign" -ForegroundColor Cyan
$ks = Join-Path $root 'keystore\debug.jks'
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $ks) | Out-Null
    & $keytool -genkeypair -v -keystore $ks -alias dsm -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass android -keypass android `
        -dname "CN=DeepSeek Mobile, OU=Dev, O=Starstwinkle, L=CN, C=CN" 2>&1 | Out-Null
}

$final = Join-Path $outDir "$appName.apk"
if (Test-Path $final) { Remove-Item $final -Force }
& $apksigner sign --ks $ks --ks-key-alias dsm --ks-pass pass:android --key-pass pass:android `
    --min-sdk-version 24 --out $final $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

& $apksigner verify --min-sdk-version 24 $final
if ($LASTEXITCODE -ne 0) { throw "verify failed" }

$size = [math]::Round((Get-Item $final).Length / 1KB, 1)
Write-Host "== built: $final ($size KB)" -ForegroundColor Green

# ---------- 8. install ----------
if (-not $NoInstall) {
    $adb = 'D:\leidian\LDPlayer14\adb.exe'
    if (Test-Path $adb) {
        $dev = (& $adb devices) | Select-String 'device$' | Select-Object -First 1
        if ($dev) {
            $serial = ($dev -split '\s+')[0]
            Write-Host "== install to $serial" -ForegroundColor Cyan
            & $adb -s $serial install -r $final
        } else {
            Write-Host "== no emulator attached, skip install" -ForegroundColor Yellow
        }
    }
}
