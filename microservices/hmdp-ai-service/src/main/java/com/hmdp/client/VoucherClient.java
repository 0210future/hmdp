package com.hmdp.client;

import com.hmdp.model.VoucherRecord;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "hmdp-voucher-ai-client", url = "${VOUCHER_SERVICE_URL:http://127.0.0.1:8086}")
public interface VoucherClient {

    @GetMapping("/voucher/internal/shop/{shopId}")
    List<VoucherRecord> listByShopId(@PathVariable("shopId") Long shopId);
}
