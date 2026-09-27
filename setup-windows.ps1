# Установка Claude Code + скиллов + подключения к Roblox Studio на диск D (Windows).
# Использование: откройте PowerShell, вставьте весь этот файл целиком и нажмите Enter.
# Скрипт можно запускать повторно — уже сделанные шаги он пропускает.

$ErrorActionPreference = 'Continue'   # git/claude пишут прогресс в stderr — это не ошибки
$Root   = 'D:\Roblox'
$Repo   = Join-Path $Root 'AIGOOGLE'
$Branch = 'claude/msayib-roblox-dev-skill-setup-xhh2j6'
$RepoUrl = 'https://github.com/OpeRaGTX/AIGOOGLE.git'

function Step($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan }
function Need-Ok($what) { if ($LASTEXITCODE -ne 0) { throw "$what завершился с ошибкой (код $LASTEXITCODE)." } }
function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' +
                [Environment]::GetEnvironmentVariable('Path', 'User') + ';' +
                (Join-Path $env:USERPROFILE '.local\bin')
}

try {
    if (-not (Test-Path 'D:\')) { throw 'Диск D не найден.' }

    Step '1/5 Git'
    Refresh-Path
    if (Get-Command git -ErrorAction SilentlyContinue) {
        Write-Host 'Git уже установлен.'
    } else {
        if (Get-Command winget -ErrorAction SilentlyContinue) {
            winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements
        } else {
            # Нет winget — качаем официальный установщик Git for Windows с GitHub
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $rel = Invoke-RestMethod 'https://api.github.com/repos/git-for-windows/git/releases/latest'
            $asset = $rel.assets | Where-Object { $_.name -match '^Git-[\d.]+-64-bit\.exe$' } | Select-Object -First 1
            if (-not $asset) { throw 'Не нашёл установщик Git. Поставьте вручную: https://git-scm.com/download/win' }
            $exe = Join-Path $env:TEMP $asset.name
            Write-Host "Скачиваю $($asset.name)..."
            Invoke-WebRequest $asset.browser_download_url -OutFile $exe -UseBasicParsing
            Write-Host 'Устанавливаю Git (Windows может спросить разрешение — нажмите "Да")...'
            Start-Process $exe -ArgumentList '/VERYSILENT', '/NORESTART' -Wait
        }
        Refresh-Path
        if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git не установился. Поставьте вручную: https://git-scm.com/download/win' }
    }

    Step '2/5 Claude Code'
    if (Get-Command claude -ErrorAction SilentlyContinue) {
        Write-Host 'Claude Code уже установлен.'
    } else {
        Invoke-RestMethod https://claude.ai/install.ps1 | Invoke-Expression
        $ErrorActionPreference = 'Continue'   # установщик Claude переключает на 'Stop'
        Refresh-Path
        if (-not (Get-Command claude -ErrorAction SilentlyContinue)) { throw 'Claude Code не найден после установки. Перезапустите PowerShell и вставьте скрипт снова.' }
    }

    Step '3/5 Проект на D:'
    New-Item -ItemType Directory -Force -Path $Root, (Join-Path $Root 'Places') | Out-Null
    if (Test-Path (Join-Path $Repo '.git')) {
        git -C $Repo fetch origin $Branch; Need-Ok 'git fetch'
    } else {
        git clone $RepoUrl $Repo; Need-Ok 'git clone'
    }
    git -C $Repo checkout $Branch; Need-Ok 'git checkout'
    git -C $Repo pull origin $Branch; Need-Ok 'git pull'
    $n = (Get-ChildItem (Join-Path $Repo '.claude\skills') -Directory).Count
    Write-Host "Скиллов найдено: $n"

    Step '4/5 Подключение Roblox Studio (MCP)'
    $bat = Join-Path $env:LOCALAPPDATA 'Roblox\mcp.bat'
    if (-not (Test-Path $bat)) {
        Write-Warning "Не найден $bat. Установите/обновите Roblox Studio и запустите скрипт ещё раз."
    } else {
        Push-Location $Repo
        # через cmd, чтобы сообщение "No MCP server" в stderr не считалось ошибкой PowerShell
        cmd.exe /c "claude mcp get Roblox_Studio >nul 2>&1"
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'Сервер Roblox_Studio уже добавлен.'
        } else {
            claude mcp add Roblox_Studio -- cmd.exe /c $bat; Need-Ok 'claude mcp add'
        }
        Pop-Location
    }

    Step '5/5 Готово'
    Write-Host @"
Осталось сделать руками:
  1. Откройте Roblox Studio и место (сохраните его в D:\Roblox\Places).
  2. Assistant -> ... -> Manage MCP Servers -> включите 'Enable Studio as MCP server'.
  3. В PowerShell:
       cd $Repo
       claude
     При первом запуске войдите в аккаунт Claude в браузере.
  4. В Claude: /mcp  (Roblox_Studio = connected),  /skills  (список скиллов).
"@ -ForegroundColor Green
}
catch {
    Write-Host "`nОШИБКА: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'Скопируйте этот текст и пришлите его в чат.'
}
