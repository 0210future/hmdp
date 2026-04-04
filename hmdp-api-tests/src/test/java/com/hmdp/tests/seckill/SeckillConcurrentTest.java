package com.hmdp.tests.seckill;

import com.hmdp.base.BaseTest;
import com.hmdp.utils.RedisUtil;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 秒杀服务并发测试
 */
@Epic("订单服务")
@Feature("秒杀功能")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SeckillConcurrentTest extends BaseTest {

    private static final String TEST_PHONE_PREFIX = "13800138";
    private static final int CONCURRENT_THREADS = 100;
    private static final int INITIAL_STOCK = 10;
    private static Long voucherId = 999001L;

    @BeforeAll
    void setUp() {
        // 初始化秒杀券库存
        RedisUtil.setSeckillStock(voucherId, INITIAL_STOCK);
        System.out.println("初始化秒杀券库存: " + INITIAL_STOCK);
    }

    @Test
    @Story("秒杀下单")
    @Description("测试高并发秒杀场景，验证库存扣减和一人一单限制")
    @Severity(SeverityLevel.BLOCKER)
    void testConcurrentSeckill() throws InterruptedException {
        // 准备用户Token
        String[] tokens = new String[CONCURRENT_THREADS];
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            String phone = TEST_PHONE_PREFIX + String.format("%03d", i + 100);
            tokens[i] = prepareUserAndLogin(phone);
        }
        
        System.out.println("准备完成，开始并发测试...");
        
        // 并发执行秒杀
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger stockInsufficientCount = new AtomicInteger(0);
        AtomicInteger duplicateOrderCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    Response response = given()
                        .header("Authorization", "Bearer " + tokens[index])
                    .when()
                        .post("/voucher-order/seckill/" + voucherId)
                    .then()
                        .statusCode(200)
                        .extract()
                        .response();
                    
                    boolean success = response.jsonPath().getBoolean("success");
                    String errorMsg = response.jsonPath().getString("errorMsg");
                    
                    if (success) {
                        successCount.incrementAndGet();
                        System.out.println("用户" + index + " 秒杀成功，订单ID: " + response.jsonPath().getLong("data"));
                    } else if (errorMsg != null && errorMsg.contains("stock")) {
                        stockInsufficientCount.incrementAndGet();
                    } else if (errorMsg != null && errorMsg.contains("purchased")) {
                        duplicateOrderCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("用户" + index + " 请求异常: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有请求完成
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        // 验证结果
        System.out.println("========== 测试结果 ==========");
        System.out.println("并发线程数: " + CONCURRENT_THREADS);
        System.out.println("初始库存: " + INITIAL_STOCK);
        System.out.println("成功下单: " + successCount.get());
        System.out.println("库存不足: " + stockInsufficientCount.get());
        System.out.println("重复购买: " + duplicateOrderCount.get());
        System.out.println("总耗时: " + (endTime - startTime) + "ms");
        System.out.println("TPS: " + (CONCURRENT_THREADS * 1000.0 / (endTime - startTime)));
        
        // 验证库存扣减正确
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
        
        // 验证Redis库存为0
        String remainingStock = RedisUtil.getSeckillStock(voucherId);
        System.out.println("剩余库存: " + remainingStock);
        assertThat(remainingStock).isEqualTo("0");
        
        // 验证没有超卖
        assertThat(successCount.get()).isLessThanOrEqualTo(INITIAL_STOCK);
    }

    @Test
    @Story("秒杀下单")
    @Description("测试单人重复秒杀")
    @Severity(SeverityLevel.CRITICAL)
    void testDuplicateSeckill() {
        // 准备用户
        String phone = TEST_PHONE_PREFIX + "999";
        String token = prepareUserAndLogin(phone);
        
        // 第一次秒杀
        Response response1 = given()
            .header("Authorization", "Bearer " + token)
        .when()
            .post("/voucher-order/seckill/" + voucherId)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        System.out.println("第一次秒杀: " + response1.asString());
        
        // 第二次秒杀（应该失败）
        Response response2 = given()
            .header("Authorization", "Bearer " + token)
        .when()
            .post("/voucher-order/seckill/" + voucherId)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        System.out.println("第二次秒杀: " + response2.asString());
        
        // 验证第二次失败
        assertThat(response2.jsonPath().getBoolean("success")).isFalse();
        assertThat(response2.jsonPath().getString("errorMsg")).contains("purchased");
    }

    @Test
    @Story("秒杀下单")
    @Description("测试未授权访问")
    @Severity(SeverityLevel.CRITICAL)
    void testSeckill_Unauthorized() {
        given()
        .when()
            .post("/voucher-order/seckill/" + voucherId)
        .then()
            .statusCode(401);
    }

    @Test
    @Story("秒杀下单")
    @Description("测试无效优惠券ID")
    @Severity(SeverityLevel.NORMAL)
    void testSeckill_InvalidVoucher() {
        String phone = TEST_PHONE_PREFIX + "998";
        String token = prepareUserAndLogin(phone);
        
        Response response = given()
            .header("Authorization", "Bearer " + token)
        .when()
            .post("/voucher-order/seckill/999999")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
    }

    /**
     * 准备用户并登录
     */
    private String prepareUserAndLogin(String phone) {
        try {
            // 发送验证码
            given()
                .queryParam("phone", phone)
            .when()
                .post("/user/code")
            .then()
                .statusCode(200);
            
            // 获取验证码
            String code = RedisUtil.getLoginCode(phone);
            if (code == null) {
                code = "123456"; // 默认验证码
            }
            
            // 登录
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
        } catch (Exception e) {
            System.err.println("准备用户失败: " + phone + ", " + e.getMessage());
            return null;
        }
    }
}
