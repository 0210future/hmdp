@echo off
chcp 65001 >nul
echo ========================================
echo   RabbitMQ 启动提示
echo ========================================
echo.
echo RabbitMQ 需要通过 Docker 或直接安装启动
echo.
echo 方式一：使用 Docker（推荐）
echo   docker run -d --name rabbitmq ^
echo     -p 5672:5672 -p 15672:15672 ^
echo     rabbitmq:3-management
echo.
echo 方式二：直接安装
echo   1. 下载 RabbitMQ: https://www.rabbitmq.com/download.html
echo   2. 安装 Erlang OTP
echo   3. 安装 RabbitMQ Server
echo   4. 启用管理插件: rabbitmq-plugins enable rabbitmq_management
echo.
echo 管理界面地址: http://localhost:15672
echo 默认用户名/密码: guest/guest
echo.
pause
