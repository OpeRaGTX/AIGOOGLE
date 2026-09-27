# Подключение Claude Code к Roblox Studio

Claude управляет Studio через **встроенный MCP-сервер Roblox Studio**. Он работает только на
вашем компьютере, поэтому всё ниже делается локально, не в облачной сессии claude.ai/code.

## 1. Установить Claude Code

Нужен Claude Code (CLI или десктоп-приложение). Документация: https://code.claude.com/docs

- macOS / Linux / WSL: `curl -fsSL https://claude.ai/install.sh | bash`
- Windows (PowerShell): `irm https://claude.ai/install.ps1 | iex`

Проверка: `claude --version`.

## 2. Скачать этот репозиторий со скиллами

```bash
git clone https://github.com/OpeRaGTX/AIGOOGLE.git
cd AIGOOGLE
git checkout claude/msayib-roblox-dev-skill-setup-xhh2j6   # не нужно после слияния в основную ветку
```

Скиллы лежат в `.claude/skills/`, правила — в `CLAUDE.md`. Claude Code подхватывает их сам,
когда запущен **из этой папки**.

## 3. Включить MCP-сервер в Studio

1. Откройте Roblox Studio и любое место (place).
2. Откройте **Assistant** → **…** → **Manage MCP Servers**.
3. Включите **Enable Studio as MCP server**.

## 4. Подключить Studio к Claude Code

**Вариант А — Quick connect (проще всего).** В том же окне: **MCP Servers → Quick connect →
Claude Code**. Если Claude Code нет в списке — установите его (шаг 1) и перезапустите Studio.

**Вариант Б — командой.** В терминале, в папке репозитория:

- macOS:
  ```bash
  claude mcp add Roblox_Studio -- /Applications/RobloxStudio.app/Contents/MacOS/StudioMCP
  ```
- Windows, CMD:
  ```bat
  claude mcp add Roblox_Studio -- cmd.exe /c "%LOCALAPPDATA%\Roblox\mcp.bat"
  ```
- Windows, PowerShell:
  ```powershell
  claude mcp add Roblox_Studio -- cmd.exe /c "$env:LOCALAPPDATA\Roblox\mcp.bat"
  ```

Имя сервера должно быть именно `Roblox_Studio` — на него ссылаются скиллы.

## 5. Проверить

1. Studio открыт, сервер включён.
2. В папке репозитория запустите `claude`, затем команду `/mcp` — `Roblox_Studio` должен быть
   `connected`.
3. В Studio (Manage MCP Servers) зелёный индикатор показывает число подключённых клиентов.
4. Попросите: *«Покажи, какие студии подключены, и перечисли сервисы в моём месте»*.
   Claude вызовет `list_roblox_studios`, потом `search_game_tree`.
5. Команда `/skills` покажет установленные скиллы.

## Как работать

- Начинайте задачу обычным текстом: *«Сделай монстра, который патрулирует коридор и
  преследует игрока по звуку»*. Скилл `plan-first` задаст вопросы, покажет план, затем Claude
  напишет скрипты и сам вставит их в Studio.
- 3D-постройки: *«Построй заброшенную больницу: коридор, 4 палаты, морг»* — скиллы
  `building-3d-objects` / `building-maps` строят через `execute_luau`.
- Claude сам запускает плейтест (`start_stop_play`) и читает вывод (`get_console_output`).

## Безопасность

- Roblox предупреждает: MCP-клиент может читать и **изменять** открытое место. Перед большими
  изменениями сохраните место (File → Save / публикация версии) — к версии можно откатиться.
- Claude Code по умолчанию спрашивает разрешение на каждый вызов инструментов Studio.
  Разрешайте только то, что понимаете.

## Если не работает

- Перезапустите Studio и Claude Code.
- Проверьте, что файл существует: `/Applications/RobloxStudio.app/Contents/MacOS/StudioMCP`
  (macOS) или `%LOCALAPPDATA%\Roblox\mcp.bat` (Windows).
- `claude mcp list` — сервер должен быть в списке; удалить и добавить заново:
  `claude mcp remove Roblox_Studio`.
- Не устанавливайте старый `Roblox/studio-rust-mcp-server` — он в архиве, встроенный сервер
  его заменил.
