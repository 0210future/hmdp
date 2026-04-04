package com.hmdp.tests.user;

import com.hmdp.base.BaseTest;
import com.hmdp.utils.RedisUtil;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户登录相关测试
 */
@Epic("用户服务")
@Feature("登录功能")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserLoginTest extends BaseTest {

    private static final String TEST_PHONE = "13800138001";
    private static String verificationCode;

    @Test
    @Order(1)
    @Story("发送验证码")
    @Description("测试正常发送验证码功能")
    @Severity(SeverityLevel.BLOCKER)
    void testSendCode_Success() {
        // 先删除已存在的验证码
        RedisUtil.deleteLoginCode(TEST_PHONE);
        
        Response response = given()
            .queryParam("phone", TEST_PHONE)
        .when()
            .post("/user/code")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 验证响应
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        // 验证Redis中存在验证码
        String code = RedisUtil.getLoginCode(TEST_PHONE);
        assertThat(code).isNotNull();
        assertThat(code).hasSize(6);
        assertThat(code).matches("\\d{6}");
        
        verificationCode = code;
    }

    @Test
    @Order(2)
    @Story("发送验证码")
    @Description("测试手机号格式校验")
    @Severity(SeverityLevel.CRITICAL)
    void testSendCode_InvalidPhone() {
        Response response = given()
            .queryParam("phone", "123456")
        .when()
            .post("/user/code")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
        assertThat(response.jsonPath().getString("errorMsg")).contains("Invalid");
    }

    @Test
    @Order(3)
    @Story("发送验证码")
    @Description("测试空手机号")
    @Severity(SeverityLevel.NORMAL)
    void testSendCode_EmptyPhone() {
        Response response = given()
            .queryParam("phone", "")
        .when()
            .post("/user/code")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
    }

    @Test
    @Order(4)
    @Story("用户登录")
    @Description("测试正常登录流程")
    @Severity(SeverityLevel.BLOCKER)
    void testLogin_Success() {
        // 确保验证码已发送
        if (verificationCode == null) {
            testSendCode_Success();
        }
        
        Response response = given()
            .contentType("application/json")
            .body("{\"phone\":\"" + TEST_PHONE + "\",\"code\":\"" + verificationCode + "\"}")
        .when()
            .post("/user/login")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 验证响应
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        // 验证返回了Token
        String token = response.jsonPath().getString("data");
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        
        // 保存Token供后续测试使用
        testToken = token;
        
        // 验证Token已存入Redis
        String userId = RedisUtil.getLoginToken(token);
        assertThat(userId).isNotNull();
    }

    @Test
    @Order(5)
    @Story("用户登录")
    @Description("测试错误验证码")
    @Severity(SeverityLevel.CRITICAL)
    void testLogin_WrongCode() {
        Response response = given()
            .contentType("application/json")
            .body("{\"phone\":\"" + TEST_PHONE + "\",\"code\":\"999999\"}")
        .when()
            .post("/user/login")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
        assertThat(response.jsonPath().getString("errorMsg")).contains("Invalid");
    }

    @Test
    @Order(6)
    @Story("用户登录")
    @Description("测试无效手机号登录")
    @Severity(SeverityLevel.CRITICAL)
    void testLogin_InvalidPhone() {
        Response response = given()
            .contentType("application/json")
            .body("{\"phone\":\"123456\",\"code\":\"123456\"}")
        .when()
            .post("/user/login")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
    }

    @Test
    @Order(7)
    @Story("获取当前用户")
    @Description("测试获取当前登录用户信息")
    @Severity(SeverityLevel.BLOCKER)
    void testGetCurrentUser_Success() {
        // 确保已登录
        if (testToken == null) {
            testLogin_Success();
        }
        
        Response response = given()
            .header("Authorization", "Bearer " + testToken)
        .when()
            .get("/user/me")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        assertThat(response.jsonPath().getString("data.id")).isNotNull();
        assertThat(response.jsonPath().getString("data.nickName")).isNotNull();
        
        // 保存用户ID
        testUserId = response.jsonPath().getLong("data.id");
    }

    @Test
    @Order(8)
    @Story("获取当前用户")
    @Description("测试未授权访问")
    @Severity(SeverityLevel.CRITICAL)
    void testGetCurrentUser_Unauthorized() {
        given()
        .when()
            .get("/user/me")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(9)
    @Story("获取当前用户")
    @Description("测试无效Token")
    @Severity(SeverityLevel.CRITICAL)
    void testGetCurrentUser_InvalidToken() {
        given()
            .header("Authorization", "Bearer invalid_token_12345")
        .when()
            .get("/user/me")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(10)
    @Story("用户登出")
    @Description("测试正常登出")
    @Severity(SeverityLevel.NORMAL)
    void testLogout_Success() {
        // 确保已登录
        if (testToken == null) {
            testLogin_Success();
        }
        
        Response response = given()
            .header("Authorization", "Bearer " + testToken)
        .when()
            .post("/user/logout")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        // 验证Token已被删除
        String userId = RedisUtil.getLoginToken(testToken);
        assertThat(userId).isNull();
    }

    @ParameterizedTest
    @Story("用户登录")
    @Description("参数化测试多种登录场景")
    @CsvSource({
        "13800138002, 123456, true, 正常登录",
        "13800138002, 999999, false, 错误验证码",
        "12345678901, 123456, false, 无效手机号"
    })
    void testLogin_Parameterized(String phone, String code, boolean expectSuccess, String description) {
        // 如果是正常登录场景，先发送验证码
        if (expectSuccess) {
            given().queryParam("phone", phone).when().post("/user/code");
            String realCode = RedisUtil.getLoginCode(phone);
            code = realCode != null ? realCode : code;
        }
        
        Response response = given()
            .contentType("application/json")
            .body("{\"phone\":\"" + phone + "\",\"code\":\"" + code + "\"}")
        .when()
            .post("/user/login")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success"))
            .as(description)
            .isEqualTo(expectSuccess);
    }
}
