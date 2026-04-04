@echo off
echo ========================================
echo   HMDP API Tests - 环境诊断工具
echo ========================================
echo.

echo [1/3] 检查 Redis 连接...
echo 尝试连接到 42.193.185.40:6379
powershell -Command "Test-NetConnection -ComputerName 42.193.185.40 -Port 6379 -InformationLevel Quiet"
if %errorlevel% equ 0 (
    echo ✓ Redis 端口可访问
) else (
    echo ✗ Redis 端口无法访问，请检查：
    echo   1. Redis 服务是否启动
    echo   2. 防火墙设置
    echo   3. 网络连接
)
echo.

echo [2/3] 检查后端服务...
echo 尝试连接到 http://localhost:8080
powershell -Command "try { $response = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 3 -UseBasicParsing; Write-Host '✓ 网关服务正常运行' } catch { Write-Host '✗ 网关服务未启动或无响应' }"
echo.

echo [3/3] Maven 依赖检查...
cd /d "%~dp0"
call mvn dependency:resolve -q
if %errorlevel% equ 0 (
    echo ✓ Maven 依赖已正确下载
) else (
    echo ✗ Maven 依赖下载失败，请运行: mvn clean install -U
)
echo.

echo ========================================
echo 诊断完成！
echo ========================================
echo.
echo 如果仍有问题，请检查：
echo 1. Redis 中验证码的 key 格式是否为: login:code:{phone}
echo 2. 后端是否正确将验证码写入 Redis
echo 3. 查看后端日志确认验证码发送逻辑
echo.
pause
