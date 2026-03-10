# HM-DianPing Microservices (Phase 1)

This directory contains a non-breaking microservice split skeleton. The original monolith under `/src` is untouched.

## Modules

- `hmdp-common`: shared DTOs and utility constants.
- `hmdp-gateway`: API gateway, route forwarding, token auth and user header propagation.
- `hmdp-user-service`: user/login/sign related APIs.
- `hmdp-shop-service`: shop/shop-type APIs, cache and GEO query logic.

## Scope in this phase

- Included: gateway + user + shop core APIs.
- Excluded intentionally: monitoring stack, log platform, tracing platform.
- Database schema: reuse original `hmdp` schema with minimal/no changes.

## Runtime dependencies

- Java 8
- Maven 3.8+
- MySQL 5.7/8.0 (database `hmdp`)
- Redis 6+
- Nacos 2.x

## Environment variables

You can override defaults in each module `application.yml`.

- `NACOS_ADDR` (default `127.0.0.1:8848`)
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

## Startup order

1. Start Nacos
2. Start MySQL and Redis
3. Start `hmdp-user-service`
4. Start `hmdp-shop-service`
5. Start `hmdp-gateway`

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

## Auth behavior

- Gateway whitelist: `/user/code`, `/user/login`, `/user/logout`, `/user/info/**`, `/shop/**`, `/shop-type/**`
- Non-whitelisted paths require `Authorization` token.
- Gateway validates Redis key `login:token:{token}` and injects:
  - `X-User-Id`
  - `X-User-NickName`
  - `X-User-Icon`

## Notes

- In this environment Maven executable is missing, so I could not run a full compile here.
- The implementation is structured to be runnable in a standard local Java+Maven setup.


## IDEA Import

- Open Maven tool window and ensure both `pom.xml` and `microservices/pom.xml` are imported.
- Use the provided Run Configurations: `HmdpGateway`, `HmdpUserService`, `HmdpShopService`.
- If you want to enable Nacos registration, set `NACOS_DISCOVERY_ENABLED=true` and `NACOS_REGISTER_ENABLED=true`.
