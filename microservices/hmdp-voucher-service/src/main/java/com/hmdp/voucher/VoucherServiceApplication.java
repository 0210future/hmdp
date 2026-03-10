package com.hmdp.voucher;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@MapperScan("com.hmdp.mapper")
@SpringBootApplication(scanBasePackages = "com.hmdp")
public class VoucherServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoucherServiceApplication.class, args);
    }
}
