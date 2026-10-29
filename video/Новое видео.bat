@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "NAME="
set /p "NAME=Название ролика: "
if "%NAME%"=="" exit /b
node tools\new.mjs "%NAME%" --open
if errorlevel 1 pause & exit /b
echo.
echo Кидай материалы в открывшуюся папку inbox, заполни brief.md и напиши Claude: сделай ролик (имя папки выше).
pause
