@echo off
chcp 65001 >nul
title 学旅圈 - 后端服务 (http://localhost:8080)
cd /d "%~dp0backend"

rem 优先使用本机已缓存的 Maven，其次使用 PATH 中的 mvn
set "MVN_CMD=mvn"
set "CACHED_MVN=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd"
if exist "%CACHED_MVN%" set "MVN_CMD=%CACHED_MVN%"

echo ============================================
echo   学旅圈后端启动中...  http://localhost:8080
echo   接口前缀 /api  H2 控制台 /h2-console
echo   首次启动会自动建表并写入演示数据
echo ============================================
call "%MVN_CMD%" spring-boot:run
pause
