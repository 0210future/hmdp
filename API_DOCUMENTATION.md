# 黑马点评微服务接口文档

## 1. 项目架构概览

### 1.1 微服务架构

```
┌─────────────────────────────────────────────────────────────┐
│                        客户端 (Web/App)                      │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│                  API Gateway (hmdp-gateway)                  │
│              端口: 8080 | 职责: 路由、认证、限流               │
└───────────────────────┬─────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
   ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
   │User服务 │    │Shop服务 │    │Blog服务 │
   │ :8082   │    │ :8083   │    │ :8084   │
   └────┬────┘    └────┬────┘    └────┬────┘
        │               │               │
   ┌────▼────┐    ┌────▼────┐    ┌────▼────┐
   │Follow服务│    │Voucher服务│   │Order服务 │
   │ :8085   │    │ :8086   │    │ :8087   │
   └─────────┘    └─────────┘    └─────────┘
```

### 1.2 技术栈

| 组件 | 版本/说明 |
|------|-----------|
| 后端框架 | Spring Boot 2.7 |
| API网关 | Spring Cloud Gateway |
| 服务调用 | OpenFeign |
| 缓存 | Redis 6.0+ |
| 数据库 | MySQL 5.7/8.0 |
| 认证 | JWT/Redis Token |
| 消息队列 | Redis Stream (用于秒杀) |

## 2. 认证与权限

### 2.1 认证方式

- **Token认证**：使用JWT或Redis Token
- **认证流程**：
  1. 用户登录获取Token
  2. 后续请求在请求头中携带 `Authorization: Bearer {token}`
  3. 网关验证Token有效性

### 2.2 权限控制

| 接口类型 | 权限要求 |
|---------|----------|
| 公开接口 | 无需认证 |
| 用户接口 | 需要登录 |
| 管理接口 | 需要管理员权限 |

### 2.3 白名单接口

- `/user/code`, `/user/login`, `/user/logout`
- `/shop/**`, `/shop-type/**`
- `/blog/hot`, `/blog/of/user`, `/blog/likes/**`, `/blog/{id}`
- `/blog-comments/of/blog`
- `/voucher/list/**`

## 3. 接口文档

### 3.1 User服务接口

| 接口名称 | 请求方法 | URL路径 | 权限 | 功能描述 |
|---------|---------|---------|------|----------|
| 发送验证码 | POST | `/user/code` | 公开 | 发送手机验证码 |
| 手机登录 | POST | `/user/login` | 公开 | 手机号验证码登录 |
| 用户登出 | POST | `/user/logout` | 公开 | 用户登出 |
| 获取当前用户 | GET | `/user/me` | 需要登录 | 获取当前登录用户信息 |
| 用户签到 | POST | `/user/sign` | 需要登录 | 用户每日签到 |
| 签到统计 | GET | `/user/sign/count` | 需要登录 | 获取连续签到天数 |
| 用户信息 | GET | `/user/info/{id}` | 公开 | 获取用户详细信息 |
| 批量查询用户 | POST | `/user/basic/list` | 内部 | 批量查询用户基本信息 |

#### 3.1.1 发送验证码

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| phone | String | 是 | 手机号码 |

**响应数据**：
```json
{
  "success": true,
  "data": "123456",
  "errorMsg": null
}
```

**错误码**：
| 错误码 | 说明 |
|-------|------|
| 400 | 手机号格式错误 |

#### 3.1.2 手机登录

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| phone | String | 是 | 手机号码 |
| code | String | 是 | 验证码 |

**响应数据**：
```json
{
  "success": true,
  "data": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "errorMsg": null
}
```

**错误码**：
| 错误码 | 说明 |
|-------|------|
| 400 | 验证码错误 |
| 400 | 手机号格式错误 |

#### 3.1.3 获取当前用户

**请求头**：
| 参数名 | 说明 |
|-------|------|
| Authorization | Bearer {token} |

**响应数据**：
```json
{
  "success": true,
  "data": {
    "id": 1,
    "nickName": "张三",
    "icon": "https://example.com/avatar.jpg"
  },
  "errorMsg": null
}
```

**错误码**：
| 错误码 | 说明 |
|-------|------|
| 401 | 未授权 |
| 401 | Token无效 |

### 3.2 Shop服务接口

| 接口名称 | 请求方法 | URL路径 | 权限 | 功能描述 |
|---------|---------|---------|------|----------|
| 查询商户详情 | GET | `/shop/{id}` | 公开 | 查询商户详细信息 |
| 新增商户 | POST | `/shop` | 需要登录 | 新增商户信息 |
| 更新商户 | PUT | `/shop` | 需要登录 | 更新商户信息 |
| 按类型查询 | GET | `/shop/of/type` | 公开 | 按类型查询商户 |
| 按名称搜索 | GET | `/shop/of/name` | 公开 | 按名称搜索商户 |
| 商户类型列表 | GET | `/shop-type/list` | 公开 | 获取商户类型列表 |

#### 3.2.1 查询商户详情

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| id | Long | 是 | 商户ID |

**响应数据**：
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "星巴克",
    "typeId": 1,
    "address": "北京市朝阳区",
    "latitude": 39.9042,
    "longitude": 116.4074,
    "avgScore": 4.5,
    "sold": 1000,
    "openHours": "08:00-22:00"
  },
  "errorMsg": null
}
```

#### 3.2.2 按类型查询商户

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| typeId | Integer | 是 | 商户类型ID |
| current | Integer | 是 | 当前页码 |
| x | Double | 否 | 经度（用于附近搜索） |
| y | Double | 否 | 纬度（用于附近搜索） |

**响应数据**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "星巴克",
      "distance": 100.5 // 当提供坐标时返回
    }
  ],
  "errorMsg": null
}
```

### 3.3 Blog服务接口

| 接口名称 | 请求方法 | URL路径 | 权限 | 功能描述 |
|---------|---------|---------|------|----------|
| 发布博客 | POST | `/blog` | 需要登录 | 发布探店博客 |
| 查询博客 | GET | `/blog/{id}` | 公开 | 查询博客详情 |
| 点赞/取消 | PUT | `/blog/like/{id}` | 需要登录 | 点赞或取消点赞 |
| 点赞列表 | GET | `/blog/likes/{id}` | 公开 | 获取博客点赞用户 |
| 用户博客 | GET | `/blog/of/user` | 公开 | 查询用户发布的博客 |
| 我的博客 | GET | `/blog/of/me` | 需要登录 | 查询当前用户的博客 |
| 热门博客 | GET | `/blog/hot` | 公开 | 查询热门博客 |
| 关注流 | GET | `/blog/of/follow` | 需要登录 | 查询关注用户的博客 |
| 发表评论 | POST | `/blog-comments` | 需要登录 | 发表博客评论 |
| 评论列表 | GET | `/blog-comments/of/blog` | 公开 | 获取博客评论 |

#### 3.3.1 发布博客

**请求头**：
| 参数名 | 说明 |
|-------|------|
| Authorization | Bearer {token} |

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| title | String | 是 | 博客标题 |
| content | String | 是 | 博客内容 |
| shopId | Long | 是 | 商户ID |
| images | String | 否 | 图片URL，多个用逗号分隔 |

**响应数据**：
```json
{
  "success": true,
  "data": 1, // 博客ID
  "errorMsg": null
}
```

#### 3.3.2 点赞/取消

**请求头**：
| 参数名 | 说明 |
|-------|------|
| Authorization | Bearer {token} |

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| id | Long | 是 | 博客ID |

**响应数据**：
```json
{
  "success": true,
  "data": {
    "id": 1,
    "liked": true, // true: 点赞, false: 取消点赞
    "likeCount": 10
  },
  "errorMsg": null
}
```

### 3.4 Follow服务接口

| 接口名称 | 请求方法 | URL路径 | 权限 | 功能描述 |
|---------|---------|---------|------|----------|
| 关注/取消 | PUT | `/follow/{id}/{isFollow}` | 需要登录 | 关注或取消关注用户 |
| 是否关注 | GET | `/follow/or/not/{id}` | 需要登录 | 检查是否关注用户 |
| 共同关注 | GET | `/follow/common/{id}` | 需要登录 | 获取共同关注用户 |
| 粉丝列表 | GET | `/follow/of/user/{id}` | 内部 | 获取用户的粉丝列表 |

#### 3.4.1 关注/取消

**请求头**：
| 参数名 | 说明 |
|-------|------|
| Authorization | Bearer {token} |

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| id | Long | 是 | 目标用户ID |
| isFollow | Boolean | 是 | true: 关注, false: 取消 |

**响应数据**：
```json
{
  "success": true,
  "data": null,
  "errorMsg": null
}
```

### 3.5 Voucher服务接口

| 接口名称 | 请求方法 | URL路径 | 权限 | 功能描述 |
|---------|---------|---------|------|----------|
| 新增优惠券 | POST | `/voucher` | 需要登录 | 新增普通优惠券 |
| 新增秒杀券 | POST | `/voucher/seckill` | 需要登录 | 新增秒杀优惠券 |
| 查询商户券 | GET | `/voucher/list/{shopId}` | 公开 | 查询商户优惠券 |
| 查询秒杀券 | GET | `/voucher/seckill/{id}` | 公开 | 查询秒杀券详情 |
| 扣减库存 | POST | `/voucher/seckill/{id}/stock/decrease` | 内部 | 扣减秒杀券库存 |

#### 3.5.1 查询商户券

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| shopId | Long | 是 | 商户ID |

**响应数据**：
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "shopId": 1,
      "title": "满100减20",
      "subTitle": "限时优惠",
      "rules": "满100元可用",
      "payValue": 80,
      "actualValue": 100,
      "type": 1, // 1: 普通券, 2: 秒杀券
      "stock": 100,
      "beginTime": "2026-04-01 00:00:00",
      "endTime": "2026-04-30 23:59:59"
    }
  ],
  "errorMsg": null
}
```

### 3.6 Order服务接口

| 接口名称 | 请求方法 | URL路径 | 权限 | 功能描述 |
|---------|---------|---------|------|----------|
| 秒杀下单 | POST | `/voucher-order/seckill/{id}` | 需要登录 | 秒杀优惠券下单 |

#### 3.6.1 秒杀下单

**请求头**：
| 参数名 | 说明 |
|-------|------|
| Authorization | Bearer {token} |

**请求参数**：
| 参数名 | 数据类型 | 是否必填 | 说明 |
|-------|---------|---------|------|
| id | Long | 是 | 秒杀券ID |

**响应数据**：
```json
{
  "success": true,
  "data": 1, // 订单ID
  "errorMsg": null
}
```

**错误码**：
| 错误码 | 说明 |
|-------|------|
| 400 | 库存不足 |
| 400 | 重复购买 |
| 400 | 秒杀未开始 |
| 400 | 秒杀已结束 |

## 4. 服务间交互流程

### 4.1 用户登录流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Gateway as API网关
    participant UserService as User服务
    participant Redis as Redis
    participant MySQL as MySQL

    Client->>Gateway: POST /user/code?phone=13800138000
    Gateway->>UserService: 转发请求
    UserService->>Redis: 生成验证码并存储
    Redis-->>UserService: 存储成功
    UserService-->>Gateway: 返回验证码
    Gateway-->>Client: 200 OK {"data": "123456"}
    
    Client->>Gateway: POST /user/login {"phone": "13800138000", "code": "123456"}
    Gateway->>UserService: 转发请求
    UserService->>Redis: 验证验证码
    Redis-->>UserService: 验证码正确
    UserService->>MySQL: 查询/创建用户
    MySQL-->>UserService: 返回用户信息
    UserService->>Redis: 生成并存储Token
    Redis-->>UserService: 存储成功
    UserService-->>Gateway: 返回Token
    Gateway-->>Client: 200 OK {"data": "token"}
```

### 4.2 博客发布与Feed推送流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Gateway as API网关
    participant BlogService as Blog服务
    participant FollowService as Follow服务
    participant Redis as Redis
    participant MySQL as MySQL

    Client->>Gateway: POST /blog {"title": "测试", "content": "内容", "shopId": 1}
    Gateway->>BlogService: 转发请求（带Token）
    BlogService->>MySQL: 保存博客
    MySQL-->>BlogService: 保存成功
    BlogService->>FollowService: Feign调用 /follow/of/user/{userId}
    FollowService->>MySQL: 查询粉丝列表
    MySQL-->>FollowService: 返回粉丝ID列表
    FollowService-->>BlogService: 返回粉丝列表
    BlogService->>Redis: 推送到粉丝收件箱
    Redis-->>BlogService: 推送成功
    BlogService-->>Gateway: 返回博客ID
    Gateway-->>Client: 200 OK {"data": 1}
```

### 4.3 秒杀下单流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Gateway as API网关
    participant OrderService as Order服务
    participant Redis as Redis
    participant VoucherService as Voucher服务
    participant MySQL as MySQL

    Client->>Gateway: POST /voucher-order/seckill/1
    Gateway->>OrderService: 转发请求（带Token）
    OrderService->>Redis: 执行Lua脚本
    Note over Redis: 1. 检查库存
    Note over Redis: 2. 检查重复购买
    Note over Redis: 3. 扣减库存
    Note over Redis: 4. 写入Stream
    Redis-->>OrderService: 执行结果
    OrderService-->>Gateway: 返回订单ID
    Gateway-->>Client: 200 OK {"data": 1}
    
    Note over OrderService,MySQL: 异步处理
    OrderService->>Redis: 消费Stream消息
    Redis-->>OrderService: 订单消息
    OrderService->>VoucherService: Feign调用 扣减库存
    VoucherService->>MySQL: 更新库存
    MySQL-->>VoucherService: 更新成功
    VoucherService-->>OrderService: 扣减成功
    OrderService->>MySQL: 创建订单
    MySQL-->>OrderService: 创建成功
```

### 4.4 商户查询流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant Gateway as API网关
    participant ShopService as Shop服务
    participant Redis as Redis
    participant MySQL as MySQL

    Client->>Gateway: GET /shop/1
    Gateway->>ShopService: 转发请求
    ShopService->>Redis: 查询缓存
    alt 缓存命中
        Redis-->>ShopService: 返回商户信息
    else 缓存未命中
        ShopService->>MySQL: 查询数据库
        MySQL-->>ShopService: 返回商户信息
        ShopService->>Redis: 写入缓存
    end
    ShopService-->>Gateway: 返回商户信息
    Gateway-->>Client: 200 OK {"data": {...}}
```

## 5. 接口调用示例

### 5.1 CURL命令示例

#### 5.1.1 发送验证码
```bash
curl -X POST "http://localhost:8080/user/code?phone=13800138000"
```

#### 5.1.2 用户登录
```bash
curl -X POST "http://localhost:8080/user/login" \
  -H "Content-Type: application/json" \
  -d '{"phone": "13800138000", "code": "123456"}'
```

#### 5.1.3 获取当前用户
```bash
curl -X GET "http://localhost:8080/user/me" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

#### 5.1.4 发布博客
```bash
curl -X POST "http://localhost:8080/blog" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -d '{"title": "测试博客", "content": "这是一篇测试博客", "shopId": 1, "images": "https://example.com/1.jpg,https://example.com/2.jpg"}'
```

#### 5.1.5 秒杀下单
```bash
curl -X POST "http://localhost:8080/voucher-order/seckill/1" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### 5.2 JavaScript/TypeScript调用示例

#### 5.2.1 登录功能
```javascript
// 发送验证码
async function sendCode(phone) {
  const response = await fetch(`http://localhost:8080/user/code?phone=${phone}`, {
    method: 'POST'
  });
  const result = await response.json();
  return result.data; // 返回验证码
}

// 用户登录
async function login(phone, code) {
  const response = await fetch('http://localhost:8080/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ phone, code })
  });
  const result = await response.json();
  return result.data; // 返回token
}

// 获取当前用户
async function getCurrentUser(token) {
  const response = await fetch('http://localhost:8080/user/me', {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  const result = await response.json();
  return result.data;
}
```

#### 5.2.2 博客功能
```javascript
// 发布博客
async function createBlog(token, blogData) {
  const response = await fetch('http://localhost:8080/blog', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify(blogData)
  });
  const result = await response.json();
  return result.data; // 返回博客ID
}

// 点赞博客
async function likeBlog(token, blogId) {
  const response = await fetch(`http://localhost:8080/blog/like/${blogId}`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`
    }
  });
  const result = await response.json();
  return result.data;
}

// 获取热门博客
async function getHotBlogs(page = 1) {
  const response = await fetch(`http://localhost:8080/blog/hot?current=${page}`);
  const result = await response.json();
  return result.data;
}
```

#### 5.2.3 商户功能
```javascript
// 查询商户详情
async function getShopDetail(shopId) {
  const response = await fetch(`http://localhost:8080/shop/${shopId}`);
  const result = await response.json();
  return result.data;
}

// 按类型查询商户
async function getShopsByType(typeId, page = 1, x, y) {
  let url = `http://localhost:8080/shop/of/type?typeId=${typeId}&current=${page}`;
  if (x && y) {
    url += `&x=${x}&y=${y}`;
  }
  const response = await fetch(url);
  const result = await response.json();
  return result.data;
}
```

## 6. 异常处理与错误码

### 6.1 通用错误码

| 错误码 | HTTP状态码 | 说明 |
|-------|-----------|------|
| 400 | 400 | 请求参数错误 |
| 401 | 401 | 未授权，Token无效或过期 |
| 403 | 403 | 权限不足 |
| 404 | 404 | 资源不存在 |
| 500 | 500 | 服务器内部错误 |
| 503 | 503 | 服务不可用 |

### 6.2 业务错误码

| 错误码 | 说明 |
|-------|------|
| 1001 | 验证码错误 |
| 1002 | 手机号格式错误 |
| 1003 | 库存不足 |
| 1004 | 重复购买 |
| 1005 | 秒杀未开始 |
| 1006 | 秒杀已结束 |
| 1007 | 商户不存在 |
| 1008 | 优惠券不存在 |

### 6.3 前端错误处理建议

```javascript
// 通用错误处理函数
function handleError(error) {
  if (error.response) {
    // 服务器返回错误状态码
    switch (error.response.status) {
      case 401:
        // 未授权，跳转到登录页
        window.location.href = '/login';
        break;
      case 403:
        // 权限不足
        alert('您没有权限执行此操作');
        break;
      case 404:
        // 资源不存在
        alert('请求的资源不存在');
        break;
      case 500:
        // 服务器错误
        alert('服务器内部错误，请稍后重试');
        break;
      default:
        // 其他错误
        const errorMsg = error.response.data?.errorMsg || '操作失败';
        alert(errorMsg);
    }
  } else if (error.request) {
    // 网络错误
    alert('网络连接失败，请检查网络');
  } else {
    // 其他错误
    alert('操作失败，请稍后重试');
  }
}

// 使用示例
try {
  const result = await login(phone, code);
  localStorage.setItem('token', result);
  // 登录成功
} catch (error) {
  handleError(error);
}
```

## 7. 性能优化建议

### 7.1 前端优化

1. **缓存策略**：
   - 缓存公开接口数据（如商户列表、热门博客）
   - 使用localStorage缓存用户信息
   - 实现请求缓存，避免重复请求

2. **网络优化**：
   - 使用HTTP/2或HTTP/3
   - 实现请求合并和批量查询
   - 使用CDN加速静态资源

3. **UI优化**：
   - 实现骨架屏减少感知等待时间
   - 使用虚拟滚动处理长列表
   - 图片懒加载

### 7.2 接口调用建议

1. **批量查询**：
   - 使用 `/user/basic/list` 批量获取用户信息
   - 减少多次单独请求

2. **合理使用缓存**：
   - 缓存商户详情、类型列表等不经常变化的数据
   - 对频繁访问的数据实现本地缓存

3. **限流与重试**：
   - 对秒杀等高并发接口实现前端限流
   - 实现网络错误自动重试机制

## 8. 部署与环境配置

### 8.1 环境配置

| 环境 | API基础URL |
|------|------------|
| 开发环境 | http://localhost:8080 |
| 测试环境 | http://test-api.hmdp.com |
| 生产环境 | http://api.hmdp.com |

### 8.2 跨域配置

前端项目需要配置跨域：

```javascript
// Vue.js 配置示例
module.exports = {
  devServer: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        pathRewrite: {
          '^/api': ''
        }
      }
    }
  }
}

// React.js 配置示例
// 在package.json中添加
"proxy": "http://localhost:8080"
```

## 9. 附录

### 9.1 数据模型

#### User模型
```typescript
interface User {
  id: number;
  phone: string;
  nickName: string;
  icon: string;
  createTime: string;
}

interface UserInfo {
  userId: number;
  gender: number;
  birthday: string;
  introduce: string;
  tags: string;
}
```

#### Shop模型
```typescript
interface Shop {
  id: number;
  name: string;
  typeId: number;
  address: string;
  latitude: number;
  longitude: number;
  avgScore: number;
  sold: number;
  openHours: string;
}

interface ShopType {
  id: number;
  name: string;
  icon: string;
  sort: number;
}
```

#### Blog模型
```typescript
interface Blog {
  id: number;
  title: string;
  content: string;
  shopId: number;
  userId: number;
  images: string;
  liked: number;
  comments: number;
  createTime: string;
  user?: User;
  shop?: Shop;
}

interface BlogComment {
  id: number;
  blogId: number;
  userId: number;
  content: string;
  createTime: string;
  user?: User;
}
```

#### Voucher模型
```typescript
interface Voucher {
  id: number;
  shopId: number;
  title: string;
  subTitle: string;
  rules: string;
  payValue: number;
  actualValue: number;
  type: number; // 1: 普通券, 2: 秒杀券
  createTime: string;
  beginTime?: string;
  endTime?: string;
  stock?: number;
}

interface VoucherOrder {
  id: number;
  userId: number;
  voucherId: number;
  payTime: string;
  status: number;
}
```

### 9.2 工具函数

#### 时间格式化
```javascript
function formatDate(date: Date | string): string {
  const d = new Date(date);
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
}
```

#### 距离格式化
```javascript
function formatDistance(distance: number): string {
  if (distance < 1000) {
    return `${Math.round(distance)}m`;
  } else {
    return `${(distance / 1000).toFixed(1)}km`;
  }
}
```

#### 价格格式化
```javascript
function formatPrice(price: number): string {
  return `¥${price.toFixed(2)}`;
}
```

---

**文档版本**: 1.0.0  
**更新时间**: 2026-04-04  
**适用范围**: 黑马点评微服务项目前端开发
