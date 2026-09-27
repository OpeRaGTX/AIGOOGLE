@echo off
chcp 65001 >nul
rem Запуск рабочего места: Roblox Studio с местом + Claude Code со скиллами.
rem Двойной клик по этому файлу (или ярлыку на рабочем столе).

set "REPO=D:\Roblox\AIGOOGLE"
set "PLACE=D:\Roblox\Places\Horror.rbxl"
set "PATH=%PATH%;%USERPROFILE%\.local\bin"

cd /d "%REPO%"
echo Обновляю скиллы...
git pull --quiet

if exist "%PLACE%" (
    echo Открываю %PLACE% в Roblox Studio...
    start "" "%PLACE%"
) else (
    echo Файл %PLACE% не найден - откройте место в Studio вручную.
)

echo.
echo Дождитесь, пока Studio полностью откроет место, затем нажмите любую клавишу.
pause >nul

rem --continue продолжает последний разговор в этой папке
claude --continue || claude
