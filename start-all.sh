#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
echo "====================================================================="
echo "  《基于Java的智慧宿舍管理系统》 启动脚本 (需本机已装 JDK17/Maven/Node/MySQL)"
echo "====================================================================="

echo "[1/3] 启动后端 :7000 ..."
(cd "$ROOT/backend" && mvn spring-boot:run) &
BACKEND_PID=$!

echo "[2/3] 启动管理端 :5173 ..."
(cd "$ROOT/frontend-admin" && npm run dev) &
ADMIN_PID=$!

echo "[3/3] 启动学生 H5 :5174 ..."
(cd "$ROOT/frontend-student" && npm run dev) &
STUDENT_PID=$!

cleanup() {
  kill "$BACKEND_PID" "$ADMIN_PID" "$STUDENT_PID" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo
echo "服务已在后台拉起（Ctrl+C 结束本脚本并尝试停止子进程）："
echo "  - Web 管理端: http://localhost:5173"
echo "  - 学生端 H5:  http://localhost:5174"
echo "  - 后端 API:   http://localhost:7000"
echo "也可分别在三个终端手动启动，参见 README。"
wait
