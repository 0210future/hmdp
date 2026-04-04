package com.hmdp.tests.shop;

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
 * 商户查询相关测试
 */
@Epic("商户服务")
@Feature("商户查询")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ShopQueryTest extends BaseTest {

    @Test
    @Order(1)
    @Story("查询商户详情")
    @Description("测试正常查询商户详情")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryShopById_Success() {
        Long shopId = 1L;
        
        Response response = given()
        .when()
            .get("/shop/" + shopId)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        assertThat(response.jsonPath().getString("data.id")).isEqualTo(shopId.toString());
        assertThat(response.jsonPath().getString("data.name")).isNotNull();
        assertThat(response.jsonPath().getString("data.address")).isNotNull();
        
        // 验证响应时间（缓存命中应该很快）
        long responseTime = response.getTime();
        System.out.println("Response time: " + responseTime + "ms");
    }

    @Test
    @Order(2)
    @Story("查询商户详情")
    @Description("测试查询不存在的商户")
    @Severity(SeverityLevel.CRITICAL)
    void testQueryShopById_NotFound() {
        Long shopId = 99999L;
        
        // 先删除缓存
        RedisUtil.deleteShopCache(shopId);
        
        Response response = given()
        .when()
            .get("/shop/" + shopId)
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
        assertThat(response.jsonPath().getString("errorMsg")).contains("not found");
    }

    @Test
    @Order(3)
    @Story("查询商户详情")
    @Description("测试缓存命中情况")
    @Severity(SeverityLevel.NORMAL)
    void testQueryShopById_CacheHit() {
        Long shopId = 1L;
        
        // 第一次查询
        given().when().get("/shop/" + shopId);
        
        // 第二次查询（应该命中缓存）
        long startTime = System.currentTimeMillis();
        Response response = given()
        .when()
            .get("/shop/" + shopId)
        .then()
            .statusCode(200)
            .extract()
            .response();
        long endTime = System.currentTimeMillis();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        assertThat(endTime - startTime).isLessThan(100); // 缓存命中应该很快
    }

    @Test
    @Order(4)
    @Story("查询商户类型")
    @Description("测试查询商户类型列表")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryShopTypeList_Success() {
        Response response = given()
        .when()
            .get("/shop-type/list")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> types = response.jsonPath().getList("data");
        assertThat(types).isNotEmpty();
        assertThat(types.size()).isGreaterThan(0);
        
        // 验证类型字段
        Map<String, Object> firstType = types.get(0);
        assertThat(firstType.get("id")).isNotNull();
        assertThat(firstType.get("name")).isNotNull();
        assertThat(firstType.get("icon")).isNotNull();
    }

    @Test
    @Order(5)
    @Story("按类型查询商户")
    @Description("测试按类型查询商户（不带坐标）")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryShopByType_WithoutLocation() {
        Integer typeId = 1;
        
        Response response = given()
            .queryParam("typeId", typeId)
            .queryParam("current", 1)
        .when()
            .get("/shop/of/type")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> shops = response.jsonPath().getList("data");
        assertThat(shops).isNotNull();
    }

    @Test
    @Order(6)
    @Story("按类型查询商户")
    @Description("测试按类型查询商户（带坐标，使用Geo）")
    @Severity(SeverityLevel.BLOCKER)
    void testQueryShopByType_WithLocation() {
        Integer typeId = 1;
        Double x = 120.15; // 经度
        Double y = 30.32;  // 纬度
        
        Response response = given()
            .queryParam("typeId", typeId)
            .queryParam("current", 1)
            .queryParam("x", x)
            .queryParam("y", y)
        .when()
            .get("/shop/of/type")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> shops = response.jsonPath().getList("data");
        assertThat(shops).isNotNull();
        
        // 验证返回的商户包含距离信息
        if (!shops.isEmpty()) {
            Map<String, Object> firstShop = shops.get(0);
            assertThat(firstShop.get("distance")).isNotNull();
        }
    }

    @Test
    @Order(7)
    @Story("按名称搜索商户")
    @Description("测试按名称搜索商户")
    @Severity(SeverityLevel.NORMAL)
    void testQueryShopByName_Success() {
        String name = "茶餐厅";
        
        Response response = given()
            .queryParam("name", name)
            .queryParam("current", 1)
        .when()
            .get("/shop/of/name")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        
        List<Map<String, Object>> shops = response.jsonPath().getList("data");
        assertThat(shops).isNotNull();
    }

    @Test
    @Order(8)
    @Story("按名称搜索商户")
    @Description("测试空名称搜索")
    @Severity(SeverityLevel.NORMAL)
    void testQueryShopByName_Empty() {
        Response response = given()
            .queryParam("current", 1)
        .when()
            .get("/shop/of/name")
        .then()
            .statusCode(200)
            .extract()
            .response();
        
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
    }

    @Test
    @Order(9)
    @Story("查询商户详情")
    @Description("测试无效商户ID")
    @Severity(SeverityLevel.NORMAL)
    void testQueryShopById_Invalid() {
        given()
        .when()
            .get("/shop/abc")
        .then()
            .statusCode(400);
    }

    @Test
    @Order(10)
    @Story("查询商户详情")
    @Description("测试负数商户ID")
    @Severity(SeverityLevel.NORMAL)
    void testQueryShopById_Negative() {
        given()
        .when()
            .get("/shop/-1")
        .then()
            .statusCode(400);
    }
}
