package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.client.VoucherClient;
import com.hmdp.config.RabbitMQConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.dto.UserVoucherOrderVO;
import com.hmdp.dto.UserDTO;
import com.hmdp.dto.VoucherDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private VoucherClient voucherClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private TransactionTemplate transactionTemplate;
    
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }

        // 先做活动时间校验，减少无效请求进入 Lua 脚本和异步下单链路。
        SeckillVoucher voucher = fetchSeckillVoucher(voucherId);
        if (voucher == null) {
            return Result.fail("Voucher not found");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("Seckill has not started");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("Seckill already ended");
        }

        Long userId = user.getId();
        long orderId = redisIdWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        // Lua 只负责原子校验库存/一人一单，真正落库交给 RabbitMQ 异步完成。
        int r = result == null ? -1 : result.intValue();
        if (r == 1) {
            return Result.fail("Insufficient stock");
        }
        if (r == 2) {
            return Result.fail("User already purchased once");
        }
        if (r != 0) {
            return Result.fail("Order request failed");
        }
        
        // 发送消息到 RabbitMQ
        try {
            SeckillOrderMessage message = new SeckillOrderMessage(orderId, userId, voucherId);
            rabbitTemplate.convertAndSend(
                RabbitMQConstants.SECKILL_ORDER_EXCHANGE,
                RabbitMQConstants.SECKILL_ORDER_ROUTING_KEY,
                message
            );
            log.info("秒杀订单消息发送成功: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
        } catch (Exception e) {
            log.error("秒杀订单消息发送失败: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId, e);
            return Result.fail("Order request failed");
        }
        
        return Result.ok(orderId);
    }

    @Override
    public Result createVoucherOrder(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setUserId(user.getId());
        voucherOrder.setVoucherId(voucherId);
        boolean success = saveVoucherOrderTx(voucherOrder);
        return success ? Result.ok(voucherOrder.getId()) : Result.fail("Create order failed");
    }

    @Override
    public Result queryMyVoucherOrders(Integer current, Integer pageSize, Integer status) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("Unauthorized");
        }

        // 订单表只保存购买事实，展示页需要额外拼接券信息后再返回前端。
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 50);
        Page<VoucherOrder> page = query()
                .eq("user_id", user.getId())
                .eq(status != null, "status", status)
                .orderByDesc("create_time")
                .page(new Page<VoucherOrder>(safeCurrent, safePageSize));

        List<VoucherOrder> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList(), page.getTotal());
        }

        List<Long> voucherIds = new ArrayList<Long>(records.size());
        for (VoucherOrder record : records) {
            voucherIds.add(record.getVoucherId());
        }

        List<VoucherDTO> vouchers = voucherClient.listByIds(voucherIds);
        Map<Long, VoucherDTO> voucherMap = new HashMap<Long, VoucherDTO>();
        if (vouchers != null) {
            for (VoucherDTO voucher : vouchers) {
                voucherMap.put(voucher.getId(), voucher);
            }
        }

        List<UserVoucherOrderVO> result = new ArrayList<UserVoucherOrderVO>(records.size());
        for (VoucherOrder record : records) {
            UserVoucherOrderVO vo = new UserVoucherOrderVO();
            vo.setOrderId(record.getId());
            vo.setVoucherId(record.getVoucherId());
            vo.setPayType(record.getPayType());
            vo.setStatus(record.getStatus());
            vo.setCreateTime(record.getCreateTime());
            vo.setPayTime(record.getPayTime());
            vo.setUseTime(record.getUseTime());
            vo.setRefundTime(record.getRefundTime());

            VoucherDTO voucher = voucherMap.get(record.getVoucherId());
            if (voucher != null) {
                vo.setShopId(voucher.getShopId());
                vo.setTitle(voucher.getTitle());
                vo.setSubTitle(voucher.getSubTitle());
                vo.setRules(voucher.getRules());
                vo.setPayValue(voucher.getPayValue());
                vo.setActualValue(voucher.getActualValue());
                vo.setType(voucher.getType());
                vo.setBeginTime(voucher.getBeginTime());
                vo.setEndTime(voucher.getEndTime());
            }
            result.add(vo);
        }
        return Result.ok(result, page.getTotal());
    }

    /**
     * 保存秒杀订单（供 RabbitMQ 消费者调用）
     */
    public boolean saveVoucherOrder(VoucherOrder voucherOrder) {
        return saveVoucherOrderTx(voucherOrder);
    }

    private boolean saveVoucherOrderTx(VoucherOrder voucherOrder) {
        Boolean result = transactionTemplate.execute(status -> {
            Long userId = voucherOrder.getUserId();
            Long voucherId = voucherOrder.getVoucherId();

            // Redis 已做过一人一单校验，这里再做一次数据库侧兜底，避免重复消费或并发脏写。
            Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count != null && count > 0) {
                log.warn("Duplicate order, userId={}, voucherId={}", userId, voucherId);
                return false;
            }

            Result stockResult = voucherClient.decreaseSeckillStock(voucherId);
            boolean stockOk = stockResult != null && Boolean.TRUE.equals(stockResult.getData());
            if (!stockOk) {
                log.warn("Insufficient db stock, voucherId={}", voucherId);
                return false;
            }

            save(voucherOrder);
            return true;
        });
        return Boolean.TRUE.equals(result);
    }

    private SeckillVoucher fetchSeckillVoucher(Long voucherId) {
        Result result = voucherClient.querySeckillVoucher(voucherId);
        if (result == null || result.getData() == null) {
            return null;
        }
        if (result.getData() instanceof SeckillVoucher) {
            return (SeckillVoucher) result.getData();
        }
        if (result.getData() instanceof Map) {
            return BeanUtil.fillBeanWithMap((Map<?, ?>) result.getData(), new SeckillVoucher(), true);
        }
        return null;
    }
}
