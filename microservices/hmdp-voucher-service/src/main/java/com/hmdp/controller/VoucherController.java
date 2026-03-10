package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }

    @GetMapping("/seckill/{id}")
    public Result querySeckillVoucher(@PathVariable("id") Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        return Result.ok(voucher);
    }

    @PostMapping("/seckill/{id}/stock/decrease")
    public Result decreaseSeckillStock(@PathVariable("id") Long voucherId) {
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        return Result.ok(success);
    }
}
