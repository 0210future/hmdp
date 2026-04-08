@echo off
chcp 65001 >nul
echo ========================================
echo   微服务端口检查工具
echo ========================================
echo.

echo 检查各个微服务端口是否监听...
echo.

echo [1/7] 网关服务 (8080)...
netstat -ano | findstr ":8080 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8080 正在监听
) else (
    echo   ✗ 端口 8080 未监听 - 网关未启动
)

echo [2/7] 用户服务 (8082)...
netstat -ano | findstr ":8082 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8082 正在监听
) else (
    echo   ✗ 端口 8082 未监听 - 用户服务未启动
)

echo [3/7] 商户服务 (8083)...
netstat -ano | findstr ":8083 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8083 正在监听
) else (
    echo   ✗ 端口 8083 未监听 - 商户服务未启动
)

echo [4/7] 博客服务 (8084)...
netstat -ano | findstr ":8084 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8084 正在监听
) else (
    echo   ✗ 端口 8084 未监听 - 博客服务未启动
)

echo [5/7] 关注服务 (8085)...
netstat -ano | findstr ":8085 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8085 正在监听
) else (
    echo   ✗ 端口 8085 未监听 - 关注服务未启动
)

echo [6/7] 优惠券服务 (8086)...
netstat -ano | findstr ":8086 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8086 正在监听
) else (
    echo   ✗ 端口 8086 未监听 - 优惠券服务未启动
)

echo [7/7] 订单服务 (8087)...
netstat -ano | findstr ":8087 " >nul 2>&1
if %errorlevel% equ 0 (
    echo   ✓ 端口 8087 正在监听
) else (
    echo   ✗ 端口 8087 未监听 - 订单服务未启动
)

echo.
echo ========================================
echo 测试路由转发...
echo ========================================
echo.

echo 测试用户服务路由...
curl -s -o nul -w "HTTP状态码: %%{http_code}\n" http://localhost:8080/user/code?phone=13800138001

echo 测试商户服务路由...
curl -s -o nul -w "HTTP状态码: %%{http_code}\n" http://localhost:8080/shop/1

echo 测试博客服务路由...
curl -s -o nul -w "HTTP状态码: %%{http_code}\n" http://localhost:8080/blog/hot

echo.
echo ========================================
echo 说明：
echo - 如果显示 200 或 401，说明路由正常
echo - 如果显示 000 或连接失败，说明服务未启动或路由配置错误
echo - 如果显示 404，说明路由配置有问题
echo ========================================
pause
