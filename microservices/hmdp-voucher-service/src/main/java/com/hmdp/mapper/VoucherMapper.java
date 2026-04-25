package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券数据访问接口。
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * 查询指定商铺下的全部可用优惠券。
     *
     * @param shopId 商铺 ID
     * @return 优惠券列表
     */
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);

    /**
     * 查询指定商铺下的秒杀优惠券列表。
     *
     * @param shopId 商铺 ID
     * @return 秒杀优惠券列表
     */
    List<Voucher> querySeckillVouchersOfShop(@Param("shopId") Long shopId);

    /**
     * 按优惠券 ID 批量查询优惠券。
     *
     * @param voucherIds 优惠券 ID 列表
     * @return 优惠券列表
     */
    List<Voucher> queryVoucherByIds(@Param("voucherIds") List<Long> voucherIds);
    /**
     * 查询当前时间可用的优惠券。
     *
     * @return 优惠券列表
     */
    List<Voucher> queryVouchersOfCurrentTime();

    /**
     * 查询所有优惠券，包含普通券和秒杀券。
     *
     * @return 优惠券列表
     */
    List<Voucher> queryAllVouchers();
}
