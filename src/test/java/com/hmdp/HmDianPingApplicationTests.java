package com.hmdp;

import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private IShopService shopService;
    @Resource
    private RedisIdWorker redisIdWoker;
    @Test
    void saveTest() {
        shopService.saveShop2Redis(1L,10L);
//        saveShop2Redis(1L,10L);
    }
    @Test
    void testIdWorker() throws InterruptedException {
        for (int i = 0; i < 30; i++) {
            new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    long id = redisIdWoker.nextId("order");
                    System.out.println("id = " + id);
                }
            });
        }
    }

}
