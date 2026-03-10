package com.hmdp.client;

import com.hmdp.dto.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "hmdp-user-service", url = "${USER_SERVICE_URL:http://127.0.0.1:8082}")
public interface UserClient {

    @PostMapping("/user/basic/list")
    List<UserDTO> listByIds(@RequestBody List<Long> ids);
}
