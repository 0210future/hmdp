package com.hmdp.tests.follow;

import com.hmdp.base.BaseTest;
import com.hmdp.utils.RedisUtil;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 关注功能相关测试
 */
@Epic("关注服务")
@Feature("关注功能")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FollowTest extends BaseTest {

    private static String userToken1;
    private static String userToken2;
    private static Long userId1;
    private static Long userId2;

    @BeforeAll
    static void setUpUsers() {
        // 准备两个测试用户
        String phone1 = "13800138005";
        String phone2 = "13800138006";
        
        prepareUser(phone1);
        prepareUser(phone2);
        
        userToken1 = loginAndGetToken(phone1, RedisUtil.getLoginCode(phone1));
        userToken2 = loginAndGetToken(phone2, RedisUtil.getLoginCode(phone2));
        
        // 获取用户ID
        userId1 = getCurrentUser(userToken1).jsonPath().getLong("data.id");
        userId2 = getCurrentUser(userToken2).jsonPath().getLong("data.id");
    }

    @Test
    @Order(1)
    @Story("关注用户")
    @Description("测试关注其他用户功能")
    @Severity(SeverityLevel.BLOCKER)
    void testFollowUser_Success() {
        Response response = given()
            .header("Authorization", "Bearer " + userToken1)
        .when()
            .put("/follow/" + userId2 + "/true")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }

    @Test
    @Order(2)
    @Story("取消关注用户")
    @Description("测试取消关注用户功能")
    @Severity(SeverityLevel.BLOCKER)
    void testUnfollowUser_Success() {
        Response response = given()
            .header("Authorization", "Bearer " + userToken1)
        .when()
            .put("/follow/" + userId2 + "/false")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }

    @Test
    @Order(3)
    @Story("检查是否关注")
    @Description("测试检查是否关注某用户")
    @Severity(SeverityLevel.CRITICAL)
    void testIsFollow_Success() {
        // 先关注用户
        given()
            .header("Authorization", "Bearer " + userToken1)
        .when()
            .put("/follow/" + userId2 + "/true");
        
        Response response = given()
            .header("Authorization", "Bearer " + userToken1)
        .when()
            .get("/follow/or/not/" + userId2)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        Boolean isFollow = response.jsonPath().getBoolean("data");
        assertThat(isFollow).isTrue();
    }

    @Test
    @Order(4)
    @Story("检查是否关注")
    @Description("测试未授权访问关注状态")
    @Severity(SeverityLevel.CRITICAL)
    void testIsFollow_Unauthorized() {
        given()
        .when()
            .get("/follow/or/not/" + userId2)
        .then()
            .statusCode(401);
    }

    @Test
    @Order(5)
    @Story("查询共同关注")
    @Description("测试查询与他人的共同关注")
    @Severity(SeverityLevel.NORMAL)
    void testFollowCommons_Success() {
        // 确保用户1关注了用户2
        given()
            .header("Authorization", "Bearer " + userToken1)
        .when()
            .put("/follow/" + userId2 + "/true");

        Response response = given()
            .header("Authorization", "Bearer " + userToken1)
        .when()
            .get("/follow/common/" + userId2)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }

    @Test
    @Order(6)
    @Story("查询共同关注")
    @Description("测试未授权访问共同关注")
    @Severity(SeverityLevel.CRITICAL)
    void testFollowCommons_Unauthorized() {
        given()
        .when()
            .get("/follow/common/" + userId2)
        .then()
            .statusCode(401);
    }

    @Test
    @Order(7)
    @Story("查询用户粉丝")
    @Description("测试查询用户的粉丝列表")
    @Severity(SeverityLevel.NORMAL)
    void testQueryFollowers_Success() {
        // 使用直连服务地址查询，因为这个接口没有返回Result包装
        Response response = given()
            .baseUri(FOLLOW_SERVICE_URL)
        .when()
            .get("/follow/of/user/" + userId2)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        // 这个接口直接返回List<Long>，不是Result包装的对象
        List<Integer> followers = response.jsonPath().getList("$");
        assertThat(followers).isNotNull();
    }

    /**
     * 准备用户（如果不存在则注册）
     */
    private static void prepareUser(String phone) {
        // 发送验证码
        given()
            .queryParam("phone", phone)
        .when()
            .post("/user/code")
        .then()
            .statusCode(200);
    }
}