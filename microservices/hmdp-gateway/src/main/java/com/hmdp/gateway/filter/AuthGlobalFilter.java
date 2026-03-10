package com.hmdp.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.hmdp.utils.JwtUtils;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> WHITE_PATH_PREFIX = Arrays.asList(
            "/user/code",
            "/user/login",
            "/user/logout",
            "/user/info/",
            "/shop/",
            "/shop-type/",
            "/blog/hot",
            "/blog/of/user",
            "/blog/likes/",
            "/blog-comments/of/blog",
            "/voucher/list/"
    );

    private static final Pattern BLOG_DETAIL_PATTERN = Pattern.compile("^/blog/\\d+$");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${auth.mode:jwt}")
    private String authMode;

    @Value("${auth.jwt.secret:hmdp-secret-2026}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (isWhitePath(path)) {
            return chain.filter(exchange);
        }

        String token = resolveToken(exchange);
        if (StrUtil.isBlank(token)) {
            return unauthorized(exchange);
        }

        if (isJwtMode()) {
            Map<String, Object> claims = JwtUtils.parseToken(token, jwtSecret);
            if (claims == null || claims.get("id") == null) {
                return unauthorized(exchange);
            }

            ServerHttpRequest newRequest = exchange.getRequest()
                    .mutate()
                    .header("X-User-Id", String.valueOf(claims.getOrDefault("id", "")))
                    .header("X-User-NickName", String.valueOf(claims.getOrDefault("nickName", "")))
                    .header("X-User-Icon", String.valueOf(claims.getOrDefault("icon", "")))
                    .build();
            return chain.filter(exchange.mutate().request(newRequest).build());
        }

        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userMap == null || userMap.isEmpty()) {
            return unauthorized(exchange);
        }

        ServerHttpRequest newRequest = exchange.getRequest()
                .mutate()
                .header("X-User-Id", String.valueOf(userMap.getOrDefault("id", "")))
                .header("X-User-NickName", String.valueOf(userMap.getOrDefault("nickName", "")))
                .header("X-User-Icon", String.valueOf(userMap.getOrDefault("icon", "")))
                .build();

        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return chain.filter(exchange.mutate().request(newRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isWhitePath(String path) {
        if (BLOG_DETAIL_PATTERN.matcher(path).matches()) {
            return true;
        }
        for (String prefix : WHITE_PATH_PREFIX) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String resolveToken(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (StrUtil.isBlank(token)) {
            token = exchange.getRequest().getHeaders().getFirst("authorization");
        }
        if (StrUtil.isBlank(token)) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return token;
    }

    private boolean isJwtMode() {
        return "jwt".equalsIgnoreCase(authMode);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
