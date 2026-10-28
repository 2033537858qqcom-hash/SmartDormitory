@echo off
chcp 65001 >nul
echo =====================================================================
echo   《基于Java的智慧宿舍管理系统》 一键启动脚本 (后端 + Web管理端 + 学生小程序端)
echo =====================================================================

echo [1/3] 正在启动 Spring Boot 3 后端服务 (端口: 8080)...
start "SmartDorm-Backend (8080)" cmd /k "cd /d d:\SmartDormitory\backend && mvn spring-boot:run"

echo [2/3] 正在启动 Vue 3 Web 管理端 (端口: 5173)...
start "SmartDorm-Web-Admin (5173)" cmd /k "cd /d d:\SmartDormitory\frontend-admin && npm run dev"

echo [3/3] 正在启动 Uni-app 微信小程序/H5 学生端 (端口: 5174)...
start "SmartDorm-Student-MP (5174)" cmd /k "cd /d d:\SmartDormitory\frontend-student && npm run dev"

echo.
echo 全部服务启动窗口已拉起！
echo - Web 管理端访问地址:      http://localhost:5173
echo - 学生端小程序/H5访问地址: http://localhost:5174
echo - 后端 API 接口根地址:     http://localhost:8080
pause
