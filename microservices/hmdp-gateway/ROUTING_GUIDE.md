# HMDP 网关路由配置说明

## 📋 微服务端口映射

| 微服务 | 端口 | 路由前缀 | 说明 |
|--------|------|----------|------|
| hmdp-user-service | 8082 | `/user/**` | 用户登录、签到、信息查询 |
| hmdp-shop-service | 8083 | `/shop/**`, `/shop-type/**` | 商户查询、商户类型 |
| hmdp-blog-service | 8084 | `/blog/**` | 探店笔记、点赞、评论 |
| hmdp-follow-service | 8085 | `/follow/**` | 关注、取关、共同关注 |
| hmdp-voucher-service | 8086 | `/voucher/**`, `/voucher-order/**`, `/seckill/**` | 优惠券、秒杀、订单 |
| hmdp-order-service | 8087 | `/order/**` | 订单管理 |

## 🔀 路由规则详解

### 1. 用户服务 (8082)
```yaml
- id: user-service
  uri: http://127.0.0.1:8082
  predicates:
    - Path=/user/**
```
**接口示例：**
- `POST /user/code` - 发送验证码
- `POST /user/login` - 用户登录
- `GET /user/me` - 获取当前用户信息
- `POST /user/logout` - 用户登出
- `POST /user/sign` - 用户签到

### 2. 商户服务 (8083)
```yaml
- id: shop-service
  uri: http://127.0.0.1:8083
  predicates:
    - Path=/shop/**, /shop-type/**
```
**接口示例：**
- `GET /shop/{id}` - 查询商户详情
- `GET /shop/type/list` - 查询商户类型列表
- `GET /shop/of/type` - 根据类型查询商户

### 3. 博客服务 (8084)
```yaml
- id: blog-service
  uri: http://127.0.0.1:8084
  predicates:
    - Path=/blog/**
```
**接口示例：**
- `GET /blog/hot` - 热门博客
- `GET /blog/of/me` - 我的博客
- `GET /blog/of/user` - 用户的博客
- `POST /blog/like` - 点赞博客
- `GET /blog/likes/{id}` - 查询点赞用户

### 4. 关注服务 (8085)
```yaml
- id: follow-service
  uri: http://127.0.0.1:8085
  predicates:
    - Path=/follow/**
```
**接口示例：**
- `PUT /follow/{id}/{isFollow}` - 关注/取关
- `GET /follow/or/not` - 关注的人或未关注的人
- `GET /follow/common/{id}` - 共同关注

### 5. 优惠券服务 (8086)
```yaml
- id: voucher-service
  uri: http://127.0.0.1:8086
  predicates:
    - Path=/voucher/**, /voucher-order/**, /seckill/**
```
**接口示例：**
- `GET /voucher/list` - 优惠券列表
- `GET /voucher/{id}` - 优惠券详情
- `POST /voucher-order/seckill/{id}` - 秒杀下单
- `GET /voucher-order/my-orders` - 我的订单

### 6. 订单服务 (8087)
```yaml
- id: order-service
  uri: http://127.0.0.1:8087
  predicates:
    - Path=/order/**
```
**接口示例：**
- `GET /order/{id}` - 订单详情
- `GET /order/list` - 订单列表

## ⚙️ 配置说明

### StripPrefix 过滤器
```yaml
filters:
  - StripPrefix=0
```
- `StripPrefix=0` 表示不剥离路径前缀
- 请求 `/user/login` 会原样转发到后端 `/user/login`
- 如果设置为 `1`，则 `/user/login` 会变成 `/login` 转发

### Predicates 断言
- 支持多个路径匹配，用逗号分隔
- 例如：`Path=/voucher/**, /voucher-order/**, /seckill/**`

## 🔒 认证过滤

所有需要认证的接口都会在 `AuthGlobalFilter` 中进行 Token 验证：
- 从请求头获取 `Authorization: Bearer {token}`
- 从 Redis 中验证 Token 有效性
- OPTIONS 请求（跨域预检）直接放行

## 🌐 跨域配置

跨域已在 `CorsConfig.java` 中统一配置：
- 允许所有域名（开发环境）
- 允许所有 HTTP 方法
- 允许携带认证信息

## 📝 添加新路由

如果需要添加新的微服务，按以下格式配置：

```yaml
- id: {service-name}
  uri: http://127.0.0.1:{port}
  predicates:
    - Path=/{path}/**
  filters:
    - StripPrefix=0
```

## 🚀 启动顺序建议

1. MySQL 数据库
2. Redis 缓存
3. hmdp-gateway (8080)
4. hmdp-user-service (8082)
5. hmdp-shop-service (8083)
6. hmdp-blog-service (8084)
7. hmdp-follow-service (8085)
8. hmdp-voucher-service (8086)
9. hmdp-order-service (8087)

## 🔍 调试技巧

### 查看路由匹配情况
在 application.yml 中添加日志配置：
```yaml
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
```

### 测试路由是否生效
```bash
# 测试用户服务
curl http://localhost:8080/user/code?phone=13800138001

# 测试商户服务
curl http://localhost:8080/shop/1

# 测试博客服务
curl http://localhost:8080/blog/hot
```

## ⚠️ 注意事项

1. **端口冲突**：确保每个微服务使用不同的端口
2. **路径匹配**：注意路径前缀的大小写
3. **服务依赖**：某些服务可能依赖其他服务，注意启动顺序
4. **负载均衡**：当前使用直连方式，生产环境建议使用 Nacos 服务发现
