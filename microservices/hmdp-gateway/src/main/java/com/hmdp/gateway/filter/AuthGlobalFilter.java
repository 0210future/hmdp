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
            // 用户服务 - 公开接口
            "/user/code",           // 发送验证码
            "/user/login",          // 登录
            "/user/logout",         // 登出
            "/user/info/",          // 查询用户信息
            
            // 商户服务 - 公开接口
            "/shop/",               // 商户详情 /shop/{id}
            "/shop-type/",          // 商户类型 /shop-type/list
            "/shop/of/type",        // 根据类型查询商户
            "/shop/name/",          // 根据名称搜索商户
            
            // 博客服务 - 公开接口
            "/blog/hot",            // 热门博客
            "/blog/of/user",        // 用户的博客
            "/blog/likes/",         // 博客点赞列表
            "/blog/",               // 博客详情 /blog/{id}
            
            // 博客评论 - 公开接口
            "/blog-comments/of/blog",  // 博客评论列表
            
            // 优惠券服务 - 公开接口
            "/voucher/list/",       // 优惠券列表 /voucher/list/{shopId}
            "/voucher/"             // 优惠券详情 /voucher/{id}
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
        // 1. 博客详情路径匹配 /blog/{id}
        if (BLOG_DETAIL_PATTERN.matcher(path).matches()) {
            return true;
        }
        
        // 2. 精确匹配或前缀匹配
        for (String prefix : WHITE_PATH_PREFIX) {
            // 如果白名单项以 / 结尾，使用前缀匹配
            if (prefix.endsWith("/")) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            } else {
                // 否则使用精确匹配或前缀匹配
                if (path.equals(prefix) || path.startsWith(prefix + "/") || path.startsWith(prefix + "?")) {
                    return true;
                }
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
