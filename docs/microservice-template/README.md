# HM-DianPing Microservice Templates

This folder provides copy-ready templates for splitting the monolith into microservices.
These files are templates and do not change current runtime behavior.

## Included templates

- Parent `pom.xml` for multi-module setup
- Module `pom.xml` templates
- `application.yml` templates for all services
- Gateway auth filter template
- User context propagation templates (gateway -> service)
- Feign interface convention example

## Suggested module names

- hmdp-common
- hmdp-gateway
- hmdp-user-service
- hmdp-shop-service
- hmdp-social-service
- hmdp-marketing-service
- hmdp-trade-service
- hmdp-file-service

## Usage

1. Create a new root project for microservices.
2. Copy `parent/pom.xml` to root `pom.xml`.
3. Create modules and copy each module `pom.xml`.
4. Copy each service `application.yml` and adjust ports, DB, Redis, Nacos.
5. Copy Java templates from `code/` and adapt package names if needed.
6. Start order: Nacos -> Redis -> MySQL -> Gateway + services.