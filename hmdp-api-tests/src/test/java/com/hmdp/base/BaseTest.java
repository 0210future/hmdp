package com.hmdp.base;

import com.hmdp.utils.RedisUtil;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;

/**
 * 测试基础类
 */
public abstract class BaseTest {

    // 网关地址
    protected static final String GATEWAY_URL = "http://localhost:8080";
    
    // 直连服务地址（用于绕过网关测试）
    protected static final String USER_SERVICE_URL = "http://localhost:8082";
    protected static final String SHOP_SERVICE_URL = "http://localhost:8083";
    protected static final String BLOG_SERVICE_URL = "http://localhost:8084";
    protected static final String FOLLOW_SERVICE_URL = "http://localhost:8085";
    protected static final String VOUCHER_SERVICE_URL = "http://localhost:8086";
    protected static final String ORDER_SERVICE_URL = "http://localhost:8087";

    // 测试用户Token
    protected static String testToken;
    protected static Long testUserId;

    @BeforeAll
    static void setUp() {
        // 配置RestAssured
        RestAssured.config = RestAssuredConfig.config()
            .httpClient(HttpClientConfig.httpClientConfig()
                .setParam("http.connection.timeout", 10000)
                .setParam("http.socket.timeout", 10000));
        
        RestAssured.baseURI = GATEWAY_URL;
        
        // 测试Redis连接
        System.out.println("========== 环境检查 ==========");
        boolean redisConnected = RedisUtil.testConnection();
        System.out.println("Redis连接状态: " + (redisConnected ? "✓ 正常" : "✗ 失败"));
        if (!redisConnected) {
            System.err.println("警告: Redis连接失败，请检查:");
            System.err.println("  1. Redis服务是否启动");
            System.err.println("  2. Redis地址是否正确 (42.193.185.40:6379)");
            System.err.println("  3. 防火墙是否允许连接");
        }
        System.out.println("网关地址: " + GATEWAY_URL);
        System.out.println("=============================");
    }

    /**
     * 登录并获取Token
     */
    protected static String loginAndGetToken(String phone, String code) {
        Response response = given()
            .contentType("application/json")
            .body("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}")
        .when()
            .post("/user/login")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        return response.jsonPath().getString("data");
    }

    /**
     * 发送验证码
     */
    protected static void sendCode(String phone) {
        given()
            .queryParam("phone", phone)
        .when()
            .post("/user/code")
        .then()
            .statusCode(200);
    }

    /**
     * 获取当前登录用户
     */
    protected static Response getCurrentUser(String token) {
        return given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/user/me");
    }

    /**
     * 等待指定毫秒
     */
    protected void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
