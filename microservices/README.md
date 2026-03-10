# HM-DianPing Microservices

This directory contains a non-breaking microservice split skeleton. The original monolith under `/src` is untouched.

## Modules

- `hmdp-common`: shared DTOs and utility constants.
- `hmdp-gateway`: API gateway, route forwarding, token auth and user header propagation.
- `hmdp-user-service`: user/login/sign related APIs.
- `hmdp-shop-service`: shop/shop-type APIs, cache and GEO query logic.
- `hmdp-blog-service`: blog + blog comments APIs (uses Feign for user/follow).
- `hmdp-follow-service`: follow APIs (uses Feign for user).
- `hmdp-voucher-service`: voucher + seckill voucher APIs.
- `hmdp-order-service`: voucher order APIs (uses Feign for voucher).

## Scope in this phase

- Included: gateway + user + shop + blog + follow + voucher + order core APIs.
- Excluded intentionally: monitoring stack, log platform, tracing platform.
- Database schema: reuse original `hmdp` schema with minimal/no changes.

## Runtime dependencies

- Java 8
- Maven 3.8+
- MySQL 5.7/8.0 (database `hmdp`)
- Redis 6+
- Nacos 2.x (optional; defaults to disabled)

## Environment variables

You can override defaults in each module `application.yml`.

- `NACOS_ADDR` (default `127.0.0.1:8848`)
- `NACOS_DISCOVERY_ENABLED` (default `false`)
- `NACOS_REGISTER_ENABLED` (default `false`)
- `MYSQL_HOST` (default `127.0.0.1`)
- `MYSQL_PORT` (default `3306`)
- `MYSQL_USERNAME` (default `root`)
- `MYSQL_PASSWORD` (default `root`)
- `REDIS_HOST` (default `127.0.0.1`)
- `REDIS_PORT` (default `6379`)
- `REDIS_PASSWORD` (default empty)
- `REDIS_DB` (default `0`)
- `GATEWAY_PORT` (default `8080`)
- `USER_SERVICE_PORT` (default `8082`)
- `SHOP_SERVICE_PORT` (default `8083`)
- `BLOG_SERVICE_PORT` (default `8084`)
- `FOLLOW_SERVICE_PORT` (default `8085`)
- `VOUCHER_SERVICE_PORT` (default `8086`)
- `ORDER_SERVICE_PORT` (default `8087`)

## Startup order

1. Start MySQL and Redis
2. Start `hmdp-user-service`
3. Start `hmdp-shop-service`
4. Start `hmdp-blog-service`
5. Start `hmdp-follow-service`
6. Start `hmdp-voucher-service`
7. Start `hmdp-order-service`
8. Start `hmdp-gateway`

If you enable Nacos, start it before the services.

## Build

```bash
mvn -f microservices/pom.xml clean package -DskipTests
```

## Routed APIs (through gateway)

- User service:
  - `POST /user/code`
  - `POST /user/login`
  - `POST /user/logout`
  - `GET /user/me`
  - `POST /user/sign`
  - `GET /user/sign/count`
  - `GET /user/info/{id}`

- Shop service:
  - `GET /shop/{id}`
  - `GET /shop/of/type`
  - `GET /shop/of/name`
  - `POST /shop`
  - `PUT /shop`
  - `GET /shop-type/list`

- Blog service:
  - `POST /blog`
  - `GET /blog/{id}`
  - `PUT /blog/like/{id}`
  - `GET /blog/likes/{id}`
  - `GET /blog/of/user`
  - `GET /blog/of/me`
  - `GET /blog/hot`
  - `GET /blog/of/follow`
  - `POST /blog-comments`
  - `GET /blog-comments/of/blog`

- Follow service:
  - `PUT /follow/{id}/{isFollow}`
  - `GET /follow/or/not/{id}`
  - `GET /follow/common/{id}`
  - `GET /follow/of/user/{id}`

- Voucher service:
  - `POST /voucher`
  - `POST /voucher/seckill`
  - `GET /voucher/list/{shopId}`
  - `GET /voucher/seckill/{id}`
  - `POST /voucher/seckill/{id}/stock/decrease`

- Order service:
  - `POST /voucher-order/seckill/{id}`

## Auth behavior

- Gateway whitelist:
  - `/user/code`, `/user/login`, `/user/logout`, `/user/info/**`
  - `/shop/**`, `/shop-type/**`
  - `/blog/hot`, `/blog/of/user`, `/blog/likes/**`, `/blog/{id}`
  - `/blog-comments/of/blog`
  - `/voucher/list/**`
- Non-whitelisted paths require `Authorization` token.
- Gateway validates Redis key `login:token:{token}` and injects:
  - `X-User-Id`
  - `X-User-NickName`
  - `X-User-Icon`

## IDEA Import

- Open Maven tool window and ensure both `pom.xml` and `microservices/pom.xml` are imported.
- Use the provided Run Configurations: `HmdpGateway`, `HmdpUserService`, `HmdpShopService`, `HmdpBlogService`, `HmdpFollowService`, `HmdpVoucherService`, `HmdpOrderService`.
- If you want to enable Nacos registration, set `NACOS_DISCOVERY_ENABLED=true` and `NACOS_REGISTER_ENABLED=true`.
