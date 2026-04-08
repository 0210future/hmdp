package com.hmdp.client;

import com.hmdp.model.ShopRecord;
import com.hmdp.model.ShopTypeRecord;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "hmdp-shop-ai-client", url = "${SHOP_SERVICE_URL:http://127.0.0.1:8083}")
public interface ShopClient {

    @GetMapping("/shop/internal/all")
    List<ShopRecord> listAll();

    @GetMapping("/shop/internal/{id}")
    ShopRecord getShop(@PathVariable("id") Long shopId);

    @GetMapping("/shop/internal/types")
    List<ShopTypeRecord> listTypes();
}
