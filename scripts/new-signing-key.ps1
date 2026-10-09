<#
.SYNOPSIS
  Creates Dayloom's release signing key locally and (optionally) stores it as GitHub Actions secrets.
  在本机生成 Dayloom 的发版签名证书，并可选地写入 GitHub Actions Secrets。

.DESCRIPTION
  The key is generated on this machine and never leaves it except as the four repository secrets.
  Run it once; it refuses to overwrite an existing keystore, because losing or replacing the key
  means installed apps can no longer be updated.
  证书只在本机生成，除了写入仓库的四个 Secrets 之外不会离开本机。只需运行一次；已有证书时拒绝覆盖，
  因为证书丢失或被替换后，已安装的 App 将无法再升级。

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\new-signing-key.ps1 -SetSecrets
#>
param(
    [string]$OutDir = (Join-Path $HOME "dayloom-signing"),
    [string]$Alias = "dayloom",
    [string]$Repo = "lonemoonspace/dayloom",
    # Also write the four secrets with the GitHub CLI (needs `gh auth login` first).
    # 同时用 GitHub CLI 写入四个 Secrets（需要先 `gh auth login`）。
    [switch]$SetSecrets
)

$ErrorActionPreference = "Stop"

function Find-Keytool {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    throw "keytool not found: install JDK 17 or set JAVA_HOME. / 找不到 keytool：请安装 JDK 17 或设置 JAVA_HOME。"
}

function New-RandomPassword([int]$Length = 32) {
    # Letters and digits only, so the value survives copy/paste into any secret field unchanged.
    # 只用字母和数字，复制粘贴到任何 Secret 输入框都不会被转义或截断。
    $chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".ToCharArray()
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $bytes = New-Object byte[] $Length
    $rng.GetBytes($bytes)
    $rng.Dispose()
    -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

$keytool = Find-Keytool
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$keystore = Join-Path $OutDir "dayloom-release.jks"
$notes = Join-Path $OutDir "dayloom-signing-PASSWORD.txt"

if (Test-Path $keystore) {
    throw "Keystore already exists, refusing to overwrite: $keystore / 证书已存在，拒绝覆盖：$keystore"
}

$password = New-RandomPassword
# Passed through an environment variable (keytool -storepass:env) so it never appears on a command line.
# 经环境变量传给 keytool（-storepass:env），密码不会出现在命令行参数里。
$env:DAYLOOM_KEYSTORE_PASSWORD = $password
try {
    # PKCS12 uses one password for both the store and the key, hence KEY_PASSWORD = KEYSTORE_PASSWORD.
    # PKCS12 格式的 store 密码与 key 密码是同一个，所以 KEY_PASSWORD = KEYSTORE_PASSWORD。
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias $Alias `
        -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Dayloom" `
        -storepass:env DAYLOOM_KEYSTORE_PASSWORD -keypass:env DAYLOOM_KEYSTORE_PASSWORD -noprompt
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE). / keytool 执行失败（$LASTEXITCODE）。" }

    # Save the password before anything else can fail: a keystore without its password is useless
    # and would also block a rerun, since the script refuses to overwrite it.
    # 先把密码落盘再做其他事：没有密码的证书毫无用处，而且脚本拒绝覆盖，会挡住重新运行。
    Set-Content -Path $notes -Encoding UTF8 -Value "Password / 密码: $password"

    $listing = & $keytool -list -v -keystore $keystore -alias $Alias -storepass:env DAYLOOM_KEYSTORE_PASSWORD
    $match = $listing | Select-String -Pattern "SHA256:\s*(\S+)" | Select-Object -First 1
    $sha256 = if ($match) { $match.Matches[0].Groups[1].Value } else { "(run keytool -list -v / 请运行 keytool -list -v 查看)" }
} finally {
    Remove-Item Env:\DAYLOOM_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
}

$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))

@"
Dayloom release signing key / Dayloom 发版签名证书
=================================================
Keystore / 证书文件 : $keystore
Alias              : $Alias
Password / 密码     : $password
SHA-256 fingerprint / 证书指纹 :
$sha256

GitHub secrets / 仓库 Secrets ($Repo):
  KEYSTORE_BASE64   = base64 of the keystore file / 证书文件的 base64
  KEYSTORE_PASSWORD = the password above / 上面的密码
  KEY_ALIAS         = $Alias
  KEY_PASSWORD      = the password above / 上面的密码

Move the password and the .jks file into a password manager or other safe backup,
then delete this text file. Never commit either of them.
请把密码和 .jks 文件存进密码管理器或其他安全的备份位置，然后删除这个文本文件。两者都绝不能提交进仓库。
"@ | Set-Content -Path $notes -Encoding UTF8

$secrets = [ordered]@{
    KEYSTORE_BASE64   = $base64
    KEYSTORE_PASSWORD = $password
    KEY_ALIAS         = $Alias
    KEY_PASSWORD      = $password
}

if ($SetSecrets) {
    if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
        throw "GitHub CLI (gh) not found; rerun without -SetSecrets and add the secrets in the web UI. / 找不到 GitHub CLI（gh）；请去掉 -SetSecrets 重新运行，并在网页上添加 Secrets。"
    }
    foreach ($name in $secrets.Keys) {
        # Piped via stdin so the values are not passed as command-line arguments.
        # 经标准输入传值，避免把值放进命令行参数。
        $secrets[$name] | gh secret set $name --repo $Repo
        if ($LASTEXITCODE -ne 0) { throw "gh secret set $name failed. / 写入 $name 失败。" }
    }
    Write-Host "Secrets written to $Repo. / 已写入 $Repo 的 Secrets。" -ForegroundColor Green
} else {
    Set-Clipboard -Value $base64
    Write-Host "KEYSTORE_BASE64 copied to clipboard; add the four secrets listed in:" -ForegroundColor Yellow
    Write-Host "KEYSTORE_BASE64 已复制到剪贴板；请按下面文件里的说明添加四个 Secrets：" -ForegroundColor Yellow
    Write-Host "  https://github.com/$Repo/settings/secrets/actions"
}

Write-Host ""
Write-Host "Keystore / 证书 : $keystore"
Write-Host "Password file / 密码文件 : $notes  (back it up, then delete / 备份后删除)"
Write-Host "SHA-256 : $sha256"
