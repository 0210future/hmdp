package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    Result queryMyVoucherOrders(Integer current, Integer pageSize, Integer status);
    
    /**
     * 保存秒杀订单（供 RabbitMQ 消费者调用）
     */
    boolean saveVoucherOrder(VoucherOrder voucherOrder);
}
