package com.hmdp.util;

import com.hmdp.dto.ai.AiSearchFilter;
import com.hmdp.model.ShopTypeRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class AiIntentParserTest {

    @Test
    void shouldParsePriceDistanceAndScene() {
        ShopTypeRecord food = new ShopTypeRecord();
        food.setId(1L);
        food.setName("美食");

        AiSearchFilter filter = AiIntentParser.parse("附近适合约会的人均100以内餐厅", Arrays.asList(food));
        Assertions.assertEquals(Integer.valueOf(100), filter.getMaxAvgPrice());
        Assertions.assertEquals(Integer.valueOf(5000), filter.getMaxDistanceMeters());
        Assertions.assertTrue(filter.getScenes().contains("适合约会"));
        Assertions.assertEquals(Long.valueOf(1L), filter.getTypeId());
    }

    @Test
    void shouldParseLateNightHour() {
        AiSearchFilter filter = AiIntentParser.parse("晚上十一点还开门的火锅店", Arrays.<ShopTypeRecord>asList());
        Assertions.assertEquals(Integer.valueOf(23), filter.getOpenAfterHour());
    }
}
