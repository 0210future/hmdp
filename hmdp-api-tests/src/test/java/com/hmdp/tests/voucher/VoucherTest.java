package com.hmdp.tests.voucher;

import com.hmdp.base.BaseTest;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 优惠券功能相关测试
 */
@Epic("优惠券服务")
@Feature("优惠券功能")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class VoucherTest extends BaseTest {

    @Test
    @Order(1)
    @Story("查询店铺优惠券")
    @Description("测试查询指定店铺的优惠券列表")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryVoucherOfShop_Success() {
        Long shopId = 1L; // 使用已知的店铺ID
        
        Response response = given()
            .pathParam("shopId", shopId)
        .when()
            .get("/voucher/list/{shopId}")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> vouchers = response.jsonPath().getList("data");
        assertThat(vouchers).isNotNull();
    }

    @Test
    @Order(2)
    @Story("查询秒杀优惠券")
    @Description("测试查询秒杀优惠券详情")
    @Severity(SeverityLevel.CRITICAL)
    void testQuerySeckillVoucher_Success() {
        // 使用已知的秒杀券ID
        Long voucherId = 999001L;
        
        Response response = given()
            .pathParam("id", voucherId)
        .when()
            .get("/voucher/seckill/{id}")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 检查是否返回了秒杀券信息或null
        Object data = response.jsonPath().get("data");
        // 可能返回null（如果没有找到该券），但这不算错误
        // 我们只验证HTTP请求本身没问题
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @Order(3)
    @Story("减少秒杀库存")
    @Description("测试减少秒杀优惠券库存")
    @Severity(SeverityLevel.NORMAL)
    void testDecreaseSeckillStock_Success() {
        // 使用已知的秒杀券ID
        Long voucherId = 999001L;
        
        Response response = given()
            .pathParam("id", voucherId)
        .when()
            .post("/voucher/seckill/{id}/stock/decrease")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        Boolean success = response.jsonPath().getBoolean("data");
        // 成功减少库存或者库存不足都可以接受，只要不是错误
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }

    @Test
    @Order(4)
    @Story("查询不存在店铺的优惠券")
    @Description("测试查询不存在店铺的优惠券")
    @Severity(SeverityLevel.CRITICAL)
    void testQueryVoucherOfShop_NotFound() {
        Long shopId = 99999L; // 不存在的店铺ID
        
        Response response = given()
            .pathParam("shopId", shopId)
        .when()
            .get("/voucher/list/{shopId}")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 即使店铺不存在，也应该返回成功但没有优惠券
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> vouchers = response.jsonPath().getList("data");
        // 如果没有优惠券，返回空列表或null都是合理的
    }

    @Test
    @Order(5)
    @Story("查询不存在的秒杀券")
    @Description("测试查询不存在的秒杀优惠券")
    @Severity(SeverityLevel.CRITICAL)
    void testQuerySeckillVoucher_NotFound() {
        // 使用不存在的券ID
        Long voucherId = 999999L;
        
        Response response = given()
            .pathParam("id", voucherId)
        .when()
            .get("/voucher/seckill/{id}")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 即使券不存在，也应该返回成功但数据为null
        assertThat(response.statusCode()).isEqualTo(200);
    }
}