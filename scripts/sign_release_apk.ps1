# ⚠️ 什么时候**不需要**这个脚本
# -------------------------------
# 如果项目根目录已有 keystore.properties + 对应 .jks,app/build.gradle 会**自动签名**,
# assembleRelease 直接产出 **app-release.apk**(已签名),本脚本就多余了。
# 本脚本只在「没有正式密钥、但需要一份可安装的包来自测」时使用。
#
# sign_release_apk.ps1 —— 把 assembleRelease 产出的 unsigned 包装成可安装的 APK
#
# 背景
# ----
# app/build.gradle 里的发布签名是"条件式"的:只有 keystore.properties 存在时才用正式密钥。
# 该文件不入库(已在 .gitignore),所以在没有它的机器上 assembleRelease 产出的是
# **unsigned 包,装不上手机**。
#
# 本脚本做两件事:
#   1. zipalign 4 字节对齐(Android 要求)
#   2. 用指定密钥签名
#
# ⚠️ 关于默认的 debug 密钥
# ------------------------
# 不传 -Keystore 时,脚本用 `~/.android/debug.keystore` 签名 ——
# 这和 Android Studio 的调试签名是同一张证书,好处是**能覆盖安装已有的 debug 包、
# 保留用户进度**,适合自测。
# 但它**绝对不能用于正式发布**:
#   - 该证书是公开的(密码就是 android),任何人都能伪造你的更新包
#   - 上架应用商店必须用自己的密钥,且**密钥丢失后永远无法更新已发布的 App**
# 正式发布请先生成自己的 keystore,再用 -Keystore / -StorePass / -KeyAlias 传入,
# 并把 keystore 文件与密码**离线备份**。
#
# 用法
# ----
#   # 自测(debug 密钥)
#   powershell -ExecutionPolicy Bypass -File scripts/sign_release_apk.ps1
#
#   # 正式(自己的密钥)
#   powershell -ExecutionPolicy Bypass -File scripts/sign_release_apk.ps1 `
#       -Keystore D:\keys\studyword.jks -StorePass '***' -KeyAlias studyword -KeyPass '***'

param(
    [string]$Keystore = "$env:USERPROFILE\.android\debug.keystore",
    [string]$StorePass = "android",
    [string]$KeyAlias = "androiddebugkey",
    [string]$KeyPass = "android",
    [string]$ApkDir = "app\build\outputs\apk\release",
    [string]$OutName = "app-release-signed.apk"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$unsigned = Join-Path $ApkDir "app-release-unsigned.apk"
$aligned = Join-Path $ApkDir "app-release-aligned.apk"
$signed = Join-Path $ApkDir $OutName

if (-not (Test-Path $unsigned)) {
    Write-Host "[ERROR] 找不到 $unsigned —— 请先运行 .\gradlew.bat :app:assembleRelease"
    exit 1
}
if (-not (Test-Path $Keystore)) {
    Write-Host "[ERROR] 找不到密钥: $Keystore"
    exit 1
}

# 找最新的 build-tools
$btRoot = "$env:LOCALAPPDATA\Android\Sdk\build-tools"
$bt = Get-ChildItem $btRoot -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $bt) { Write-Host "[ERROR] 找不到 Android SDK build-tools"; exit 1 }
Write-Host "build-tools: $($bt.Name)"

Write-Host "1/3 zipalign..."
& (Join-Path $bt.FullName "zipalign.exe") -f -p 4 $unsigned $aligned

Write-Host "2/3 apksigner..."
& (Join-Path $bt.FullName "apksigner.bat") sign `
    --ks $Keystore --ks-pass "pass:$StorePass" `
    --key-pass "pass:$KeyPass" --ks-key-alias $KeyAlias `
    --out $signed $aligned

Write-Host "3/3 验证..."
& (Join-Path $bt.FullName "apksigner.bat") verify --print-certs $signed

Write-Host ""
Write-Host "完成: $signed  ($([math]::Round((Get-Item $signed).Length / 1MB, 2)) MB)"
Write-Host "安装: adb install -r `"$signed`""
if ($Keystore -like "*debug.keystore") {
    Write-Host ""
    Write-Host "⚠️  本次使用 debug 密钥,仅适合自测,不可用于发布。"
}
