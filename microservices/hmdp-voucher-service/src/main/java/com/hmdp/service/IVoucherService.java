package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;

import java.util.List;

/**
 * 优惠券服务接口。
 */
public interface IVoucherService extends IService<Voucher> {

    /**
     * 查询指定商铺下的全部可用优惠券。
     *
     * @param shopId 商铺 ID
     * @return 优惠券列表
     */
    Result queryVoucherOfShop(Long shopId);

    /**
     * 查询指定商铺下的秒杀优惠券列表。
     *
     * @param shopId 商铺 ID
     * @return 秒杀优惠券列表
     */
    Result querySeckillVouchersOfShop(Long shopId);

    /**
     * 新增秒杀优惠券。
     *
     * @param voucher 秒杀优惠券信息
     */
    void addSeckillVoucher(Voucher voucher);

    /**
     * 按优惠券 ID 批量查询优惠券。
     *
     * @param voucherIds 优惠券 ID 列表
     * @return 优惠券列表
     */
    List<Voucher> queryVoucherByIds(List<Long> voucherIds);
}
