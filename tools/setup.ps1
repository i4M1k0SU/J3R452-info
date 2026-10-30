# J3R452 開発ツール一式を tools/ 配下に取得する (冪等。再実行すると不足分のみ取得)
#   pwsh -File tools/setup.ps1
# 前提: JDK 17+ / git / Python 3.13 (pyscard の Windows wheel が 3.13 までのため)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$Tools = $PSScriptRoot
$Root = Split-Path $Tools -Parent

$AntVersion = '1.10.18'
$AntSha512 = 'f86d7b263bc7c6903a91532943f1c8ecccc17dd999851a796617e79795908d7a666cbe999b9ecf06735cc7e830f50dca9f49b37bb5f6f02a0a3803cd22b558b0'
$AntJavacardVersion = 'v26.05.15'
$AntJavacardSha256 = '14f5e25c07b184e4ec02ee148892c2ea7ad5d7e9db8b91109524df8f7d000589'
$GpVersion = 'v25.10.20'
$GpSha256 = 'c88e0c5093032ec4571571f5397b6174e56bf632667950fa5bb716338534b122'
$SdksCommit = '700ec80afdda210a0e62fb6a151a9cddc1acd244'
$SdkKits = @('jc320v26.0_kit', 'jc305u4_kit')

function Get-Verified($Url, $Out, $Algorithm, $Hash) {
    if (Test-Path $Out) {
        if ((Get-FileHash $Out -Algorithm $Algorithm).Hash -eq $Hash) { return }
        Remove-Item $Out
    }
    Write-Host "download: $Url"
    $tmp = "$Out.part"
    Invoke-WebRequest $Url -OutFile $tmp
    $actual = (Get-FileHash $tmp -Algorithm $Algorithm).Hash
    if ($actual -ne $Hash) {
        Remove-Item $tmp
        throw "hash mismatch: $Url`n expected $Hash`n actual   $actual"
    }
    Move-Item $tmp $Out
}

# Apache Ant
$antHome = Join-Path $Tools "apache-ant-$AntVersion"
if (-not (Test-Path (Join-Path $antHome 'bin\ant.bat'))) {
    $zip = Join-Path $Tools "apache-ant-$AntVersion-bin.zip"
    Get-Verified "https://dlcdn.apache.org/ant/binaries/apache-ant-$AntVersion-bin.zip" $zip SHA512 $AntSha512
    Expand-Archive $zip -DestinationPath $Tools -Force
    Remove-Item $zip
}

# ant-javacard (CAP ビルド用 Ant タスク)
Get-Verified "https://github.com/martinpaljak/ant-javacard/releases/download/$AntJavacardVersion/ant-javacard.jar" `
    (Join-Path $Tools 'ant-javacard.jar') SHA256 $AntJavacardSha256

# GlobalPlatformPro (カードへのインストール/管理)
Get-Verified "https://github.com/martinpaljak/GlobalPlatformPro/releases/download/$GpVersion/gp.jar" `
    (Join-Path $Tools 'gp.jar') SHA256 $GpSha256

# Oracle Java Card SDK (必要な kit のみ sparse checkout)
$sdks = Join-Path $Tools 'sdks'
if (-not (Test-Path (Join-Path $sdks '.git'))) {
    git clone --filter=blob:none --no-checkout https://github.com/martinpaljak/oracle_javacard_sdks.git $sdks
}
git -C $sdks sparse-checkout set --cone @SdkKits
git -C $sdks -c advice.detachedHead=false checkout --quiet $SdksCommit
if ($LASTEXITCODE -ne 0) { throw 'sdk checkout failed' }

# Python venv (pyscard / cryptography)
$venvPy = Join-Path $Root '.venv\Scripts\python.exe'
if (-not (Test-Path $venvPy)) {
    $py313 = Join-Path $env:LOCALAPPDATA 'Python\pythoncore-3.13-64\python.exe'
    if (-not (Test-Path $py313)) { pymanager install 3.13; if ($LASTEXITCODE -ne 0) { throw 'python 3.13 install failed' } }
    & $py313 -m venv (Join-Path $Root '.venv')
}
& $venvPy -m pip install -q -r (Join-Path $Tools 'requirements.txt')
if ($LASTEXITCODE -ne 0) { throw 'pip install failed' }

Write-Host 'setup complete. 次に: . tools/env.ps1'
