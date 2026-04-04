package com.hmdp.utils;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;

/**
 * Redis工具类
 */
public class RedisUtil {

    private static final String REDIS_HOST = "42.193.185.40";
    private static final int REDIS_PORT = 6379;
    private static final int REDIS_DB = 0;

    private static JedisPool jedisPool;

    static {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(5);
        config.setMinIdle(1);
        jedisPool = new JedisPool(config, REDIS_HOST, REDIS_PORT, 5000);
    }

    public static Jedis getJedis() {
        Jedis jedis = jedisPool.getResource();
        jedis.select(REDIS_DB);
        return jedis;
    }

    /**
     * 获取验证码
     */
    public static String getLoginCode(String phone) {
        try (Jedis jedis = getJedis()) {
            String key = "login:code:" + phone;
            String value = jedis.get(key);
            System.out.println("[RedisUtil] 尝试读取 key: " + key);
            System.out.println("[RedisUtil] 读取结果: " + (value == null ? "null" : value));
            
            // 如果没找到，列出所有 login:code:* 的 key
            if (value == null) {
                System.out.println("[RedisUtil] 未找到指定key，查找所有 login:code:* ...");
                Set<String> keys = jedis.keys("login:code:*");
                System.out.println("[RedisUtil] 找到的keys: " + keys);
                
                // 尝试读取第一个匹配的key
                if (keys != null && !keys.isEmpty()) {
                    for (String k : keys) {
                        String v = jedis.get(k);
                        System.out.println("[RedisUtil]   " + k + " = " + v);
                    }
                }
            }
            
            return value;
        } catch (Exception e) {
            System.err.println("[RedisUtil] 读取验证码异常: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 删除验证码
     */
    public static void deleteLoginCode(String phone) {
        try (Jedis jedis = getJedis()) {
            jedis.del("login:code:" + phone);
        }
    }

    /**
     * 获取Token信息
     */
    public static String getLoginToken(String token) {
        try (Jedis jedis = getJedis()) {
            return jedis.hget("login:token:" + token, "id");
        }
    }

    /**
     * 删除Token
     */
    public static void deleteLoginToken(String token) {
        try (Jedis jedis = getJedis()) {
            jedis.del("login:token:" + token);
        }
    }

    /**
     * 获取商户缓存
     */
    public static String getShopCache(Long shopId) {
        try (Jedis jedis = getJedis()) {
            return jedis.get("cache:shop:" + shopId);
        }
    }

    /**
     * 删除商户缓存
     */
    public static void deleteShopCache(Long shopId) {
        try (Jedis jedis = getJedis()) {
            jedis.del("cache:shop:" + shopId);
        }
    }

    /**
     * 获取秒杀库存
     */
    public static String getSeckillStock(Long voucherId) {
        try (Jedis jedis = getJedis()) {
            return jedis.get("seckill:stock:" + voucherId);
        }
    }

    /**
     * 设置秒杀库存
     */
    public static void setSeckillStock(Long voucherId, int stock) {
        try (Jedis jedis = getJedis()) {
            jedis.set("seckill:stock:" + voucherId, String.valueOf(stock));
        }
    }

    /**
     * 测试Redis连接
     */
    public static boolean testConnection() {
        try (Jedis jedis = getJedis()) {
            String pong = jedis.ping();
            return "PONG".equals(pong);
        } catch (Exception e) {
            System.err.println("Redis连接失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 清空所有测试数据
     */
    public static void clearTestData() {
        try (Jedis jedis = getJedis()) {
            // 删除测试相关的key
            jedis.keys("test:*").forEach(jedis::del);
        }
    }
}
