package com.hmdp.tests.user;

import com.hmdp.base.BaseTest;
import com.hmdp.utils.RedisUtil;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户签到相关测试
 */
@Epic("用户服务")
@Feature("签到功能")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserSignTest extends BaseTest {

    private static final String TEST_PHONE = "13800138003";
    private static String userToken;
    private static Long userId;

    @BeforeAll
    static void setUpUser() {
        // 发送验证码并登录
        given().queryParam("phone", TEST_PHONE).when().post("/user/code");
        String code = RedisUtil.getLoginCode(TEST_PHONE);
        
        Response response = given()
            .contentType("application/json")
            .body("{\"phone\":\"" + TEST_PHONE + "\",\"code\":\"" + code + "\"}")
        .when()
            .post("/user/login")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        userToken = response.jsonPath().getString("data");
        
        // 获取用户ID
        Response userResponse = given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .get("/user/me")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        userId = userResponse.jsonPath().getLong("data.id");
    }

    @Test
    @Order(1)
    @Story("用户签到")
    @Description("测试正常签到功能")
    @Severity(SeverityLevel.BLOCKER)
    void testSign_Success() {
        Response response = given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .post("/user/sign")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        // 验证Redis Bitmap
        String key = "sign:" + userId + ":" + LocalDateTime.now().format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 注意：这里需要通过Jedis直接验证Bitmap，实际测试中可能需要额外的验证方法
    }

    @Test
    @Order(2)
    @Story("用户签到")
    @Description("测试重复签到")
    @Severity(SeverityLevel.NORMAL)
    void testSign_Duplicate() {
        // 再次签到
        Response response = given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .post("/user/sign")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 重复签到应该也返回成功（幂等）
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }

    @Test
    @Order(3)
    @Story("用户签到")
    @Description("测试未授权签到")
    @Severity(SeverityLevel.CRITICAL)
    void testSign_Unauthorized() {
        given()
        .when()
            .post("/user/sign")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(4)
    @Story("签到统计")
    @Description("测试获取连续签到天数")
    @Severity(SeverityLevel.BLOCKER)
    void testSignCount_Success() {
        Response response = given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .get("/user/sign/count")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        // 验证返回的连续签到天数
        Integer count = response.jsonPath().getInt("data");
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    @Order(5)
    @Story("签到统计")
    @Description("测试未授权获取签到统计")
    @Severity(SeverityLevel.CRITICAL)
    void testSignCount_Unauthorized() {
        given()
        .when()
            .get("/user/sign/count")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(6)
    @Story("获取用户信息")
    @Description("测试获取用户详细信息")
    @Severity(SeverityLevel.NORMAL)
    void testGetUserInfo_Success() {
        Response response = given()
            .header("Authorization", "Bearer " + userToken)
        .when()
            .get("/user/info/" + userId)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }
}
