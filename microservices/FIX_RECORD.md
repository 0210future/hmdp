# 微服务配置修复记录

## 🐛 发现的问题

### 问题 1：所有微服务的 `spring.application.name` 配置错误

**现象：** 只能访问用户服务，其他服务无法通过网关访问

**根本原因：** 
- shop-service、blog-service、follow-service、order-service 的 `spring.application.name` 都被错误地配置为 `hmdp-user-service`
- 这导致：
  1. 日志中所有服务都显示为 "hmdp-user-service"，无法区分
  2. 如果使用 Nacos 服务发现，会导致服务注册冲突
  3. 监控和链路追踪无法正确识别服务

### 问题 2：网关路由 Path 断言配置错误

**现象：** 多个 Path 写在同一个路由中

**根本原因：**
- Spring Cloud Gateway 中，多个 Path 断言是 **AND 关系**，不是 OR 关系
- 例如：
  ```yaml
  predicates:
    - Path=/shop/**
    - Path=/shop-type/**
  ```
  这意味着请求必须**同时匹配**两个路径（这是不可能的）

## ✅ 修复方案

### 修复 1：修正所有微服务的应用名称

| 微服务 | 修复前 | 修复后 |
|--------|--------|--------|
| hmdp-shop-service | `hmdp-user-service` ❌ | `hmdp-shop-service` ✅ |
| hmdp-blog-service | `hmdp-user-service` ❌ | `hmdp-blog-service` ✅ |
| hmdp-follow-service | `hmdp-user-service` ❌ | `hmdp-follow-service` ✅ |
| hmdp-order-service | `hmdp-user-service` ❌ | `hmdp-order-service` ✅ |

### 修复 2：拆分网关路由配置

将多个 Path 的路由拆分为独立的路由规则：

**修复前：**
```yaml
- id: shop-service
  uri: http://127.0.0.1:8083
  predicates:
    - Path=/shop/**, /shop-type/**  # 错误写法
```

**修复后：**
```yaml
- id: shop-detail
  uri: http://127.0.0.1:8083
  predicates:
    - Path=/shop/**

- id: shop-type
  uri: http://127.0.0.1:8083
  predicates:
    - Path=/shop-type/**
```

## 📋 完整的微服务配置清单

| 服务名称 | 应用名称 | 端口 | 路由路径 | 状态 |
|---------|---------|------|---------|------|
| hmdp-user-service | `hmdp-user-service` | 8082 | `/user/**` | ✅ |
| hmdp-shop-service | `hmdp-shop-service` | 8083 | `/shop/**`, `/shop-type/**` | ✅ |
| hmdp-blog-service | `hmdp-blog-service` | 8084 | `/blog/**` | ✅ |
| hmdp-follow-service | `hmdp-follow-service` | 8085 | `/follow/**` | ✅ |
| hmdp-voucher-service | `hmdp-voucher-service` | 8086 | `/voucher/**`, `/voucher-order/**`, `/seckill/**` | ✅ |
| hmdp-order-service | `hmdp-order-service` | 8087 | `/order/**` | ✅ |

## 🚀 重启步骤

**重要：修改配置后必须重启所有微服务！**

```bash
# 1. 停止所有正在运行的微服务

# 2. 按顺序重新启动
# 启动 MySQL
# 启动 Redis

# 启动微服务（顺序不重要，但建议按以下顺序）
hmdp-user-service      (8082)
hmdp-shop-service      (8083)
hmdp-blog-service      (8084)
hmdp-follow-service    (8085)
hmdp-voucher-service   (8086)
hmdp-order-service     (8087)

# 最后启动网关
hmdp-gateway           (8080)
```

## 🧪 验证方法

### 方法 1：检查服务启动日志

每个服务启动时应该显示正确的应用名称：
```
Starting HmdpShopServiceApplication on ... with PID ...
The following profiles are active: ...
Started HmdpShopServiceApplication in X.XXX seconds
```

### 方法 2：测试各个路由

```bash
# 用户服务
curl http://localhost:8080/user/code?phone=13800138001

# 商户服务
curl http://localhost:8080/shop/1
curl http://localhost:8080/shop-type/list

# 博客服务
curl http://localhost:8080/blog/hot

# 关注服务（需要登录）
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/follow/or/not

# 优惠券服务
curl http://localhost:8080/voucher/list/1

# 订单服务（需要登录）
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/order/my-orders
```

### 方法 3：运行诊断脚本

```cmd
cd D:\code\project\hm-dianping\hm-dianping\microservices
check-services.bat
```

## ⚠️ 注意事项

1. **必须重启所有服务**才能使配置生效
2. 如果使用 Nacos 服务发现，需要先清理 Nacos 中的旧服务注册信息
3. 检查日志确认每个服务都使用正确的应用名称启动
4. 如果仍然有问题，检查防火墙是否允许相应端口通信

## 📝 相关文件

- 网关路由配置：`microservices/hmdp-gateway/src/main/resources/application.yml`
- 网关跨域配置：`microservices/hmdp-gateway/src/main/java/com/hmdp/gateway/config/CorsConfig.java`
- 网关认证过滤：`microservices/hmdp-gateway/src/main/java/com/hmdp/gateway/filter/AuthGlobalFilter.java`
- 各微服务配置：`microservices/hmdp-*/src/main/resources/application.yml`

## 🎯 总结

这次问题的根本原因是**复制粘贴配置文件时忘记修改应用名称**，这是一个非常常见但容易被忽视的错误。建议：

1. 创建新项目时，仔细检查所有配置项
2. 使用模板或脚手架工具生成项目结构
3. 添加自动化测试验证服务配置
4. 定期检查服务注册中心的配置一致性
