@echo off
chcp 65001 >nul
set "ROOT=%~dp0"
node "%ROOT%scripts\configure-miniprogram.js"
if errorlevel 1 exit /b 1
cd /d "%ROOT%backend"
call mvn spring-boot:run %*
exit /b %errorlevel%
