package com.hmdp.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "hmdp-follow-service", url = "${FOLLOW_SERVICE_URL:http://127.0.0.1:8085}")
public interface FollowClient {

    @GetMapping("/follow/of/user/{id}")
    List<Long> queryFollowers(@PathVariable("id") Long userId);
}
