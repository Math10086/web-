@echo off
chcp 65001 >nul
title 学旅圈 - 前端服务 (http://localhost:5173)
cd /d "%~dp0frontend"

rem 优先使用项目自带的便携版 Node（.tools 目录），其次使用 PATH 中的 node
set "NODE_DIR=%~dp0.tools\node-v22.23.3-win-x64"
if exist "%NODE_DIR%\node.exe" set "PATH=%NODE_DIR%;%PATH%"

where node >nul 2>nul
if errorlevel 1 (
  echo [错误] 未找到 Node.js，请先安装 Node 18+ 或把 node.exe 加入 PATH
  pause
  exit /b 1
)

if not exist "node_modules" (
  echo 首次运行，正在安装前端依赖（使用 npmmirror 镜像）...
  call npm.cmd install --registry=https://registry.npmmirror.com --no-audit --no-fund
  if errorlevel 1 (
    echo [错误] 依赖安装失败，请检查网络后重试
    pause
    exit /b 1
  )
)

echo ============================================
echo   学旅圈前端启动中...  http://localhost:5173
echo   请确认后端已通过「启动后端.cmd」启动
echo ============================================
call npm.cmd run dev
pause
