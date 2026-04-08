@echo off
chcp 65001 >nul
echo ========================================
echo   数据库连接池状态检查
echo ========================================
echo.

echo [1/2] 检查 MySQL 服务状态...
netstat -ano | findstr ":3306" >nul 2>&1
if %errorlevel% equ 0 (
    echo ✓ MySQL 服务正在运行
) else (
    echo ✗ MySQL 服务未启动，请先启动 MySQL
    goto :end
)
echo.

echo [2/2] 检查当前 MySQL 连接数...
mysql -uroot -p2015784174 -e "SHOW STATUS LIKE 'Threads_connected'; SHOW STATUS LIKE 'Max_used_connections'; SHOW VARIABLES LIKE 'max_connections';" 2>nul
if %errorlevel% equ 0 (
    echo.
    echo ✓ 连接信息查询成功
    echo.
    echo 说明：
    echo   - Threads_connected: 当前活跃连接数
    echo   - Max_used_connections: 历史最大连接数
    echo   - max_connections: MySQL 允许的最大连接数
) else (
    echo ✗ 无法连接到 MySQL，请检查用户名密码是否正确
    echo    或者手动执行: mysql -uroot -p2015784174
)
echo.

:end
echo ========================================
echo 建议：
echo 1. 如果 Threads_connected 接近 max_connections，需要增加连接池大小
echo 2. 如果频繁出现连接超时，检查是否有连接泄漏
echo 3. 重启微服务可以释放所有连接
echo ========================================
pause
