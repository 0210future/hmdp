package com.hmdp.controller;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.VoucherDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

/**
 * 优惠券控制器，负责提供普通优惠券和秒杀优惠券相关接口。
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    /**
     * 新增普通优惠券。
     *
     * @param voucher 优惠券信息
     * @return 新增后的优惠券 ID
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀优惠券，同时写入秒杀库存和有效期信息。
     *
     * @param voucher 秒杀优惠券信息
     * @return 新增后的优惠券 ID
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询指定商铺下的全部可用优惠券列表。
     *
     * @param shopId 商铺 ID
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.queryVoucherOfShop(shopId);
    }

    /**
     * 查询指定商铺下的秒杀优惠券列表。
     *
     * @param shopId 商铺 ID
     * @return 秒杀优惠券列表
     */
    @GetMapping("/seckill/list/{shopId}")
    public Result querySeckillVouchersOfShop(@PathVariable("shopId") Long shopId) {
        return voucherService.querySeckillVouchersOfShop(shopId);
    }

    /**
     * 内部接口：按商铺查询优惠券列表。
     *
     * @param shopId 商铺 ID
     * @return 优惠券列表
     */
    @GetMapping("/internal/shop/{shopId}")
    public List<Voucher> listVoucherOfShopInternal(@PathVariable("shopId") Long shopId) {
        return voucherService.query().eq("shop_id", shopId).list();
    }

    /**
     * 内部接口：按优惠券 ID 批量查询优惠券详情。
     *
     * @param voucherIds 优惠券 ID 列表
     * @return 优惠券 DTO 列表
     */
    @PostMapping("/internal/list")
    public List<VoucherDTO> listByIdsInternal(@RequestBody List<Long> voucherIds) {
        if (voucherIds == null || voucherIds.isEmpty()) {
            return Collections.emptyList();
        }
        return BeanUtil.copyToList(voucherService.queryVoucherByIds(voucherIds), VoucherDTO.class);
    }

    /**
     * 查询秒杀优惠券详情。
     *
     * @param voucherId 优惠券 ID
     * @return 秒杀优惠券详情
     */
    @GetMapping("/seckill/{id}")
    public Result querySeckillVoucher(@PathVariable("id") Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        return Result.ok(voucher);
    }

    /**
     * 扣减秒杀优惠券库存。
     *
     * @param voucherId 优惠券 ID
     * @return 是否扣减成功
     */
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
