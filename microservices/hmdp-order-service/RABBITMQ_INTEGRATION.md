# RabbitMQ 集成说明

本文档说明如何将秒杀系统的消息队列从 Redis Stream 迁移到 RabbitMQ。

## 一、改造内容

### 1. 依赖添加
在 `hmdp-order-service/pom.xml` 中添加了 RabbitMQ Spring Boot Starter 依赖：
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 2. 配置文件
在 `application.yml` 中添加了 RabbitMQ 配置：
```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
    virtual-host: /
    listener:
      simple:
        acknowledge-mode: manual  # 手动确认
        concurrency: 3            # 初始消费者数量
        max-concurrency: 10       # 最大消费者数量
        prefetch: 1               # 每次预取1条消息
    template:
      retry:
        enabled: true             # 启用发送重试
        initial-interval: 1000
        max-attempts: 3
        max-interval: 10000
        multiplier: 2
```

### 3. 核心改动

#### 3.1 移除 Redis Stream 相关代码
- 删除了 `VoucherOrderHandler` 内部类（后台消费线程）
- 删除了 `handlePendingList()` 方法（pending-list 补偿机制）
- 删除了 `initStreamGroup()` 方法（消费组初始化）
- 删除了 `mapToVoucherOrder()` 和 `isValidVoucherOrder()` 辅助方法

#### 3.2 新增 RabbitMQ 相关代码
- 创建了 `RabbitMQConstants` 常量类（交换机、队列、路由键）
- 创建了 `RabbitMQConfig` 配置类（定义交换机、队列、绑定关系）
- 创建了 `SeckillOrderMessage` 消息 DTO
- 创建了 `SeckillOrderConsumer` 消费者监听器
  - 主队列消费者：处理正常订单消息
  - 死信队列消费者：处理多次重试失败的消息
- 修改了 `VoucherOrderServiceImpl.seckillVoucher()` 方法，使用 RabbitTemplate 发送消息
- 新增了 `saveVoucherOrder()` 公开方法供消费者调用

#### 3.3 Lua 脚本修改
移除了 `xadd` 命令，Lua 脚本现在只负责：
- 库存校验
- 一人一单校验
- 扣减 Redis 库存
- 记录用户购买集合

订单消息的发送由 Java 代码在 Lua 脚本执行成功后通过 RabbitMQ 完成。

## 二、启动 RabbitMQ

### 方式一：使用 Docker（推荐）
```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3-management
```

### 方式二：直接安装
1. 下载并安装 Erlang OTP
2. 下载并安装 RabbitMQ Server
3. 启用管理插件：
   ```bash
   rabbitmq-plugins enable rabbitmq_management
   ```
4. 启动 RabbitMQ 服务

### 验证 RabbitMQ 是否启动成功
- 访问管理界面：http://localhost:15672
- 默认用户名/密码：guest/guest
- 检查端口 5672（AMQP）和 15672（管理界面）是否正常

## 三、运行项目

### 1. 启动顺序
1. 启动 MySQL
2. 启动 Redis
3. **启动 RabbitMQ**
4. 启动 hmdp-order-service

### 2. 验证消息队列工作正常

#### 方式一：查看日志
启动订单服务后，发起秒杀请求，观察日志：
```
秒杀订单消息发送成功: orderId=xxx, userId=xxx, voucherId=xxx
收到秒杀订单消息: SeckillOrderMessage(orderId=xxx, userId=xxx, voucherId=xxx)
秒杀订单处理成功: orderId=xxx
```

#### 方式二：使用 RabbitMQ Management Console
1. 访问 http://localhost:15672
2. 登录后查看 Queues 标签页
3. 查看 `seckill.order.queue` 队列的消息情况
4. 查看 Consumers 标签页，确认消费者已连接

## 四、RabbitMQ 架构设计

### 1. 交换机和队列
```
┌─────────────────────┐
│ Direct Exchange     │
│ seckill.order.      │
│ exchange            │
└──────────┬──────────┘
           │ routing key: seckill.order
           ▼
┌─────────────────────┐
│ Queue               │
│ seckill.order.queue │
│ (DLX configured)    │
└──────────┬──────────┘
           │
           │ 处理失败
           ▼
┌─────────────────────┐
│ DLX Exchange        │
│ seckill.order.      │
│ dlx.exchange        │
└──────────┬──────────┘
           │ routing key: seckill.order
           ▼
┌─────────────────────┐
│ DLQ Queue           │
│ seckill.order.      │
│ dlx.queue           │
└─────────────────────┘
```

### 2. 消息流转
1. **生产者**：Lua 校验成功后，通过 `RabbitTemplate` 发送消息到交换机
2. **路由**：交换机根据 routing key 将消息路由到队列
3. **消费者**：监听队列，手动确认消息
4. **重试**：处理失败的消息重新入队（可配置重试次数）
5. **死信**：多次重试失败的消息进入死信队列，需要人工处理

## 五、RabbitMQ 优势说明

### 1. 完善的重试和死信机制
- 消息处理失败可重新入队重试
- 多次重试失败自动进入死信队列
- 死信队列可用于人工干预和问题排查

### 2. 优秀的管理界面
- RabbitMQ Management Console 功能强大
- 实时监控队列、交换机、连接、通道等
- 支持消息追踪、流量控制、权限管理

### 3. 灵活的消息路由
- 支持 Direct、Fanout、Topic、Headers 等多种交换机类型
- 可根据业务需求灵活设计路由规则

### 4. 成熟的生态系统
- 丰富的客户端库支持
- 完善的监控和告警插件
- 广泛的社区支持和文档

### 5. 手动消息确认
- 提供更精细的消息处理控制
- 确保消息被正确处理后才确认
- 避免消息丢失

## 六、注意事项

1. **确保 RabbitMQ 已启动**：在启动订单服务前，必须先启动 RabbitMQ
2. **防火墙配置**：确保 5672（AMQP）和 15672（管理界面）端口可访问
3. **内存配置**：RabbitMQ 默认占用一定内存，生产环境需要合理配置
4. **消息确认模式**：当前配置为手动确认（manual），确保消息不丢失
5. **死信处理**：建议定期监控死信队列，及时处理异常消息

## 七、常见问题

### Q1: 消息一直未被消费？
A: 检查消费者是否正常启动，查看 RabbitMQ 管理界面的 Consumers 标签页。

### Q2: 消息重复消费？
A: 数据库层已有幂等校验（user_id + voucher_id 唯一性），即使重复消费也不会创建重复订单。

### Q3: 如何调整消费者并发度？
A: 修改 `application.yml` 中的 `concurrency` 和 `max-concurrency` 配置。

### Q4: 死信队列满了怎么办？
A: 定期检查死信队列，分析失败原因，修复问题后可重新发送消息或人工处理。

## 八、测试验证

运行并发测试验证 RabbitMQ 集成是否正常：
```bash
cd hmdp-api-tests
mvn test -Dtest="com.hmdp.tests.seckill.SeckillConcurrentTest"
```

预期结果：
- ✅ 成功下单数等于初始库存
- ✅ 无超卖现象
- ✅ 一人一单限制生效
- ✅ RabbitMQ 消息正常发送和消费
- ✅ 死信队列正常工作（如有失败消息）


