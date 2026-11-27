# =========================================================
#  test-inject.ps1 - 一键验证「响应注入」
#  构建模块 -> LSPatch 打补丁 -> 装机 -> 清缓存 -> 发消息 -> 抓日志/截图
#  用法: powershell -ExecutionPolicy Bypass -File test-inject.ps1
# =========================================================
param(
    [string]$Text = "hi"
)

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$adb = 'D:\leidian\LDPlayer14\adb.exe'
$dev = '127.0.0.1:5555'
$lspatch = 'D:\.tools\lspatch\lspatch.jar'
$apk = Join-Path $root 'tools\deepseek.apk'
$out = Join-Path $root 'build-module\patched'

# 0. 模拟器
& $adb connect $dev | Out-Null
$probe = (& $adb -s $dev shell "echo dsm-ok") -join ""
if ($probe -notmatch 'dsm-ok') {
    throw "模拟器未连接，请先启动雷电"
}
& $adb -s $dev shell "wm size 1080x2400; wm density 420" | Out-Null

# 1. 构建模块
Write-Host "== build module" -ForegroundColor Cyan
& (Join-Path $root 'build-module.ps1')
if (-not (Test-Path (Join-Path $root 'build-module\dsm-hook.apk'))) { throw "模块构建失败" }

# 2. 打补丁
Write-Host "== lspatch" -ForegroundColor Cyan
New-Item -ItemType Directory -Force -Path $out | Out-Null
java -jar $lspatch $apk -m (Join-Path $root 'build-module\dsm-hook.apk') -o $out -f `
    -k (Join-Path $root 'keystore\debug.jks') android dsm android

# 3. 安装
$patched = Join-Path $out 'deepseek-398-lspatched.apk'
Write-Host "== install" -ForegroundColor Cyan
& $adb -s $dev install -r --no-incremental $patched | Select-Object -Last 1

# 4. 清 LSPatch 模块缓存（避免跑旧模块）
& $adb -s $dev shell "su -c 'rm -rf /data/user/0/com.deepseek.chat/cache/lspatch'" | Out-Null
& $adb -s $dev shell "am force-stop com.deepseek.chat" | Out-Null
& $adb -s $dev logcat -c | Out-Null

# 5. 启动并发消息
Write-Host "== send message" -ForegroundColor Cyan
& $adb -s $dev shell "am start -n com.deepseek.chat/.MainActivity" | Out-Null
Start-Sleep -Seconds 10
& $adb -s $dev shell "input tap 537 2142" | Out-Null
Start-Sleep -Milliseconds 1500
& $adb -s $dev shell "input text `"$Text`"" | Out-Null
Start-Sleep -Milliseconds 1200
& $adb -s $dev shell "input tap 977 2267" | Out-Null
Start-Sleep -Seconds 18

# 6. 收集结果
Write-Host "== collect" -ForegroundColor Cyan
& $adb -s $dev shell "logcat -d" | Out-File (Join-Path $root 'tools\last-run.log') -Encoding utf8
& $adb -s $dev shell "screencap -p /sdcard/last.png" | Out-Null
& $adb -s $dev pull /sdcard/last.png (Join-Path $root 'tools\last.png') | Out-Null

Write-Host ""
Write-Host "关键日志：" -ForegroundColor Yellow
Select-String -Path (Join-Path $root 'tools\last-run.log') `
    -Pattern 'DSM-HOOK:.*(CHAT_REQ|BRIDGE (captured|calling|done|offline|payload)|H1 inject|H2 inject)' |
    Select-Object -Last 12 | ForEach-Object { $_.Line.Trim() }

Write-Host ""
Write-Host "截图: tools\last.png" -ForegroundColor Green
Write-Host "若界面显示「注入测试：如果你看到这段话…」，则注入成功。" -ForegroundColor Green
