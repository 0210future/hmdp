package com.hmdp.client;

import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "hmdp-voucher-service", url = "${VOUCHER_SERVICE_URL:http://127.0.0.1:8086}")
public interface VoucherClient {

    @GetMapping("/voucher/seckill/{id}")
    Result querySeckillVoucher(@PathVariable("id") Long voucherId);

    @PostMapping("/voucher/seckill/{id}/stock/decrease")
    Result decreaseSeckillStock(@PathVariable("id") Long voucherId);

    @PostMapping("/voucher/internal/list")
    List<VoucherDTO> listByIds(@RequestBody List<Long> voucherIds);
}
