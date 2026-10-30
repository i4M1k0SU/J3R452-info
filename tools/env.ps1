# 現在の PowerShell セッションに J3R452 開発環境を読み込む (システム設定は変更しない)
#   . tools/env.ps1
# JDK を固定したい場合は事前に $env:J3R452_JDK を設定する

$Tools = $PSScriptRoot
$Root = Split-Path $Tools -Parent

function Find-Jdk17Plus {
    $candidates = @($env:J3R452_JDK) + (Get-ChildItem 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java',
        'C:\Program Files\Microsoft', 'C:\Program Files\Zulu', 'C:\Program Files\Amazon Corretto' -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | ForEach-Object FullName)
    foreach ($c in $candidates) {
        if (-not $c -or -not (Test-Path "$c\bin\javac.exe")) { continue }
        $v = & "$c\bin\java.exe" -XshowSettings:properties -version 2>&1 | Select-String 'java\.specification\.version = (\d+)'
        if ($v -and [int]$v.Matches[0].Groups[1].Value -ge 17) { return $c }
    }
    throw 'JDK 17 以上が見つかりません。$env:J3R452_JDK に JDK のパスを設定してください'
}

$env:JAVA_HOME = Find-Jdk17Plus
$env:ANT_HOME = (Get-ChildItem $Tools -Directory -Filter 'apache-ant-*' | Select-Object -First 1).FullName
$env:J3R452_ROOT = $Root
$env:PATH = "$env:JAVA_HOME\bin;$env:ANT_HOME\bin;$Root\.venv\Scripts;$env:PATH"

# PowerShell 組み込みの gp (= Get-ItemProperty) エイリアスはこのセッションでのみ外す
Remove-Item Alias:gp -Force -ErrorAction SilentlyContinue
function global:gp { & "$env:JAVA_HOME\bin\java.exe" -jar "$env:J3R452_ROOT\tools\gp.jar" @args }

Write-Host "JAVA_HOME = $env:JAVA_HOME"
Write-Host "ANT_HOME  = $env:ANT_HOME"
Write-Host "python    = $Root\.venv\Scripts\python.exe"
Write-Host "gp        = java -jar tools\gp.jar"
