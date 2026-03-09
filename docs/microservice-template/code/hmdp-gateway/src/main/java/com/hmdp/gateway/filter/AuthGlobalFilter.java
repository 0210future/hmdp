package com.hmdp.gateway.filter;

import cn.hutool.core.util.StrUtil;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String LOGIN_USER_KEY = "login:token:";
    private static final long LOGIN_USER_TTL_MINUTES = 30L;

    private static final List<String> WHITE_LIST = Arrays.asList(
            "/shop/**",
            "/shop-type/**",
            "/voucher/**",
            "/upload/**",
            "/blog/hot",
            "/blog/{id}",
            "/blog/of/user",
            "/blog/likes/{id}",
            "/blog-comments/of/blog",
            "/user/code",
            "/user/login"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final StringRedisTemplate stringRedisTemplate;

    public AuthGlobalFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("authorization");
        if (StrUtil.isBlank(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String tokenKey = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userMap == null || userMap.isEmpty()) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header("X-User-Id", String.valueOf(userMap.get("id")))
                .header("X-User-NickName", String.valueOf(userMap.get("nickName")))
                .header("X-User-Icon", String.valueOf(userMap.get("icon")))
                .build();

        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL_MINUTES, TimeUnit.MINUTES);
        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isWhitePath(String path) {
        for (String pattern : WHITE_LIST) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}