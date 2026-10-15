# create_release_keystore.ps1 —— 生成发布签名密钥 + keystore.properties
#
# 生成的 keystore.properties 会被 app/build.gradle 读取(第 9 行),
# 里面有它需要的 4 个键:storeFile / storePassword / keyAlias / keyPassword。
# 该文件与 .jks 都已在 .gitignore 中,不会被提交。
#
# ⚠️ 生成前必须知道的三件事
# -------------------------
# 1. **密钥 = App 的身份**。用同一把密钥签名的包才能互相覆盖安装、
#    才能更新已上架的应用。换密钥 = 换了一个新 App。
# 2. **密钥丢了就永远无法更新已上架的应用**(Google Play 也不例外,除非启用
#    Play App Signing 且把上传密钥托管给 Google)。
#    → 生成后请把 .jks 文件 + 密码**离线备份**(至少两处:U 盘/网盘/密码管理器)。
# 3. **不要提交到 git**。虽然 .gitignore 已覆盖,也请别用 -f 强加。
#    公钥证书可以公开,私钥文件不行。
#
# 用法
# ----
#   # 交互式(推荐:密码不经过命令行,也不留在 shell 历史里)
#   powershell -ExecutionPolicy Bypass -File scripts/create_release_keystore.ps1
#
#   # 参数化(CI 用;注意密码会短暂出现在进程列表里)
#   powershell -ExecutionPolicy Bypass -File scripts/create_release_keystore.ps1 `
#       -Alias studyword -DName "CN=StudyWord,O=Personal,C=CN"
#
# 生成后
# ------
#   .\gradlew.bat :app:assembleRelease      # 这次会自动用正式密钥签名

param(
    [string]$Alias = "studyword",
    [string]$OutDir = "keystore",
    [string]$FileName = "studyword-release.jks",
    # 10950 天 = 30 年。Google Play 要求密钥有效期至少到 2033 年之后
    [int]$ValidityDays = 10950,
    [string]$DName = "CN=StudyWord, OU=Personal, O=StudyWord, L=Beijing, ST=Beijing, C=CN",
    [string]$StorePassPlain = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$outPath = Join-Path $root $OutDir
$jksPath = Join-Path $outPath $FileName
$propsPath = Join-Path $root "keystore.properties"

# --- 1) 找 keytool ---
$keytool = "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"
if (-not (Test-Path $keytool)) {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { $keytool = $cmd.Source }
    else { Write-Host "[ERROR] 找不到 keytool。请设置 JAVA_HOME 或安装 JDK。"; exit 1 }
}
Write-Host "keytool: $keytool"

# --- 2) 已有文件保护 ---
if (Test-Path $jksPath) {
    Write-Host "[ERROR] 已存在: $jksPath"
    Write-Host "        密钥不可覆盖重建(会导致已发布的 App 无法更新)。"
    Write-Host "        确实要重建请先手动备份并删除该文件。"
    exit 1
}
if (Test-Path $propsPath) {
    Write-Host "[ERROR] 已存在: $propsPath  请先备份后再删除。"
    exit 1
}

# --- 3) 取密码 ---
if ($StorePassPlain) {
    $storePass = $StorePassPlain
    $keyPass = $StorePassPlain
    Write-Host "使用 -StorePassPlain 提供的密码"
} else {
    Write-Host ""
    Write-Host "请输入密钥密码(至少 12 位;输入时不显示):"
    $s1 = Read-Host -AsSecureString "  密码"
    $s2 = Read-Host -AsSecureString "  再输一次"
    $p1 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s1))
    $p2 = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($s2))
    if ($p1 -ne $p2) { Write-Host "[ERROR] 两次输入不一致"; exit 1 }
    if ($p1.Length -lt 12) { Write-Host "[ERROR] 密码至少 12 位"; exit 1 }
    $storePass = $p1
    $keyPass = $p1        # keytool 默认 PKCS12 只支持一个密码
}

# --- 4) 生成密钥 ---
New-Item -ItemType Directory -Force -Path $outPath | Out-Null
Write-Host ""
Write-Host "生成密钥: $jksPath"
& $keytool -genkeypair -v `
    -keystore $jksPath `
    -alias $Alias `
    -keyalg RSA -keysize 2048 -validity $ValidityDays `
    -storetype PKCS12 `
    -storepass $storePass -keypass $keyPass `
    -dname $DName
if ($LASTEXITCODE -ne 0) { Write-Host "[ERROR] keytool 失败"; exit 1 }

# --- 5) 写 keystore.properties ---
# 键名必须与 app/build.gradle 里 props['...'] 一致:
#   storeFile / storePassword / keyAlias / keyPassword
# storeFile 相对**项目根目录**(build.gradle 用 rootProject.file() 解析)
$props = @"
# 由 scripts/create_release_keystore.ps1 生成
# 本文件不入库(.gitignore 已覆盖)。路径相对项目根目录。
storeFile=$OutDir/$FileName
storePassword=$storePass
keyAlias=$Alias
keyPassword=$keyPass
"@
# 用 UTF-8 无 BOM:Properties.load() 默认按 ISO-8859-1 读,
# 带 BOM 会把第一个键名读成 "\uFEFFstoreFile" 而报找不到键
[System.IO.File]::WriteAllText($propsPath, $props, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ""
Write-Host "已写入: $propsPath"
Write-Host ""
Write-Host "下一步:"
Write-Host "  .\gradlew.bat :app:assembleRelease"
Write-Host "  → 这次产出的 app-release.apk 会由正式密钥签名(不再是 unsigned)"
Write-Host ""
Write-Host "⚠️  请立即离线备份这两个东西,丢了就无法再更新已上架的 App:"
Write-Host "    1) $jksPath"
Write-Host "    2) 上面的密码(建议存密码管理器)"
Write-Host ""
Write-Host "自检:"
Write-Host "  & '$keytool' -list -v -keystore '$jksPath' -storepass <密码>"
