package com.hmdp.client;

import com.hmdp.model.BlogRecord;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "hmdp-blog-ai-client", url = "${BLOG_SERVICE_URL:http://127.0.0.1:8084}")
public interface BlogClient {

    @PostMapping("/blog/internal/by-shop-ids")
    List<BlogRecord> listByShopIds(@RequestBody List<Long> shopIds);

    @PostMapping("/blog/internal/by-ids")
    List<BlogRecord> listByIds(@RequestBody List<Long> blogIds);

    @GetMapping("/blog/internal/hot")
    List<BlogRecord> listHot(@RequestParam(value = "limit", defaultValue = "20") Integer limit);
}
