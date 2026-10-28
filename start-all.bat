@echo off
chcp 65001 >nul
set "ROOT=%~dp0"
echo =====================================================================
echo   《基于Java的智慧宿舍管理系统》 一键启动 (后端 + Web管理端 + 学生H5)
echo =====================================================================

echo [1/3] 正在启动 Spring Boot 后端 (端口: 7000)...
start "SmartDorm-Backend (7000)" cmd /k "cd /d "%ROOT%backend" && mvn spring-boot:run"

echo [2/3] 正在启动 Vue 3 Web 管理端 (端口: 5173)...
start "SmartDorm-Web-Admin (5173)" cmd /k "cd /d "%ROOT%frontend-admin" && npm run dev"

echo [3/3] 正在启动学生端 Vite H5 (端口: 5174)...
start "SmartDorm-Student-H5 (5174)" cmd /k "cd /d "%ROOT%frontend-student" && npm run dev"

echo.
echo 全部服务启动窗口已拉起！
echo - Web 管理端:     http://localhost:5173
echo - 学生端 H5:      http://localhost:5174
echo - 后端 API:       http://localhost:7000
pause
