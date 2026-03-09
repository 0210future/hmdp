package com.hmdp.shop.client;

import com.hmdp.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "hmdp-user-service")
public interface UserClient {

    @GetMapping("/internal/users/{id}")
    Result queryUserById(@PathVariable("id") Long id);
}