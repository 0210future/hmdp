@echo off
chcp 65001 >nul
echo ========================================
echo   网关路由测试工具
echo ========================================
echo.

echo 请确保所有微服务已启动，然后按任意键开始测试...
pause >nul
echo.

echo [1/8] 测试用户服务路由 (/user/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" http://localhost:8080/user/code?phone=13800138001
echo.

echo [2/8] 测试商户详情路由 (/shop/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" http://localhost:8080/shop/1
echo.

echo [3/8] 测试商户类型路由 (/shop-type/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" http://localhost:8080/shop-type/list
echo.

echo [4/8] 测试博客服务路由 (/blog/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" http://localhost:8080/blog/hot
echo.

echo [5/8] 测试关注服务路由 (/follow/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" -H "Authorization: Bearer test_token" http://localhost:8080/follow/or/not
echo.

echo [6/8] 测试优惠券路由 (/voucher/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" http://localhost:8080/voucher/list/1
echo.

echo [7/8] 测试秒杀路由 (/seckill/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" -H "Authorization: Bearer test_token" http://localhost:8080/seckill/1
echo.

echo [8/8] 测试订单路由 (/order/**)...
curl -s -w "\nHTTP状态码: %%{http_code}\n" -H "Authorization: Bearer test_token" http://localhost:8080/order/my-orders
echo.

echo ========================================
echo 测试结果说明：
echo ========================================
echo ✓ 200 OK - 路由正常，请求成功
echo ✓ 401 Unauthorized - 路由正常，需要登录
echo ✓ 404 Not Found - 路由正常，但资源不存在
echo ✗ 000 或连接失败 - 服务未启动或路由配置错误
echo ✗ 502 Bad Gateway - 服务未启动或地址错误
echo ✗ 504 Gateway Timeout - 服务响应超时
echo.
echo 如果所有路由都返回 200/401/404，说明配置正确！
echo ========================================
pause
