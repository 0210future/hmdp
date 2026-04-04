package com.hmdp.tests.blog;

import com.hmdp.base.BaseTest;
import com.hmdp.utils.RedisUtil;
import io.qameta.allure.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 博客查询相关测试
 */
@Epic("博客服务")
@Feature("博客查询")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlogQueryTest extends BaseTest {

    private static String userToken;
    private static Long userId;

    @BeforeAll
    static void setUpUser() {
        // 准备一个测试用户
        String phone = "13800138004";
        prepareUser(phone);
        userToken = loginAndGetToken(phone, RedisUtil.getLoginCode(phone));
        
        // 获取用户ID
        Response userResponse = getCurrentUser(userToken);
        userId = userResponse.jsonPath().getLong("data.id");
    }

    @Test
    @Order(1)
    @Story("查询热门博客")
    @Description("测试查询热门博客功能")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryHotBlog_Success() {
        Response response = given()
        .when()
            .get("/blog/hot?current=1")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> blogs = response.jsonPath().getList("data");
        assertThat(blogs).isNotNull();
    }

    @Test
    @Order(2)
    @Story("查询用户博客")
    @Description("测试查询指定用户发布的博客")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryBlogByUser_Success() {
        Response response = given()
            .queryParam("id", userId)
            .queryParam("current", 1)
        .when()
            .get("/blog/of/user")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> blogs = response.jsonPath().getList("data");
        assertThat(blogs).isNotNull();
    }

    @Test
    @Order(3)
    @Story("查询我的博客")
    @Description("测试查询当前用户发布的博客")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryMyBlog_Success() {
        Response response = given()
            .header("Authorization", "Bearer " + userToken)
            .queryParam("current", 1)
        .when()
            .get("/blog/of/me")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> blogs = response.jsonPath().getList("data");
        assertThat(blogs).isNotNull();
    }

    @Test
    @Order(4)
    @Story("查询我的博客")
    @Description("测试未授权访问我的博客")
    @Severity(SeverityLevel.CRITICAL)
    void testQueryMyBlog_Unauthorized() {
        given()
        .when()
            .get("/blog/of/me")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(5)
    @Story("点赞博客")
    @Description("测试点赞博客功能")
    @Severity(SeverityLevel.BLOCKER)
    void testLikeBlog_Success() {
        // 先获取一个博客ID
        Response hotBlogResponse = given()
            .queryParam("current", 1)
        .when()
            .get("/blog/hot")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        List<Map<String, Object>> blogs = hotBlogResponse.jsonPath().getList("data");
        if (blogs != null && !blogs.isEmpty()) {
            Long blogId = Long.parseLong(blogs.get(0).get("id").toString());
            
            Response response = given()
                .header("Authorization", "Bearer " + userToken)
            .when()
                .put("/blog/like/" + blogId)
            .then()
                .statusCode(200)
                .extract()
                .response();
            
            assertThat(response.jsonPath().getBoolean("success")).isTrue();
        }
    }

    @Test
    @Order(6)
    @Story("点赞博客")
    @Description("测试未授权点赞博客")
    @Severity(SeverityLevel.CRITICAL)
    void testLikeBlog_Unauthorized() {
        given()
        .when()
            .put("/blog/like/1")
        .then()
            .statusCode(401);
    }

    @Test
    @Order(7)
    @Story("查询博客点赞用户")
    @Description("测试查询博客点赞用户列表")
    @Severity(SeverityLevel.NORMAL)
    void testQueryBlogLikes_Success() {
        // 先获取一个博客ID
        Response hotBlogResponse = given()
            .queryParam("current", 1)
        .when()
            .get("/blog/hot")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        List<Map<String, Object>> blogs = hotBlogResponse.jsonPath().getList("data");
        if (blogs != null && !blogs.isEmpty()) {
            Long blogId = Long.parseLong(blogs.get(0).get("id").toString());
            
            Response response = given()
            .when()
                .get("/blog/likes/" + blogId)
            .then()
                .statusCode(200)
                .extract()
                .response();
            
            assertThat(response.jsonPath().getBoolean("success")).isTrue();
        }
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