package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.client.VoucherClient;
import com.hmdp.dto.Result;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String STREAM_ORDERS = "stream.orders";
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";

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

    @PostConstruct
    private void init() {
        // 消费组和后台线程在服务启动后初始化，避免首单请求再触发懒加载。
        initStreamGroup();
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

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
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );

        // Lua 只负责原子校验库存/一人一单并写入 Stream，真正落库交给后台线程异步完成。
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

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 主流程消费新消息；如果服务重启或处理失败，再回补 pending-list。
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_ORDERS, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }

                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder voucherOrder = mapToVoucherOrder(record.getValue());
                    if (!isValidVoucherOrder(voucherOrder)) {
                        stringRedisTemplate.opsForStream().acknowledge(STREAM_ORDERS, GROUP_NAME, record.getId());
                        continue;
                    }
                    saveVoucherOrderTx(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_ORDERS, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("Handle stream order failed", e);
                    handlePendingList();
                }
            }
        }
    }

    private void handlePendingList() {
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_ORDERS, ReadOffset.from("0"))
                );
                if (list == null || list.isEmpty()) {
                    break;
                }

                MapRecord<String, Object, Object> record = list.get(0);
                VoucherOrder voucherOrder = mapToVoucherOrder(record.getValue());
                if (!isValidVoucherOrder(voucherOrder)) {
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_ORDERS, GROUP_NAME, record.getId());
                    continue;
                }
                saveVoucherOrderTx(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge(STREAM_ORDERS, GROUP_NAME, record.getId());
            } catch (Exception e) {
                log.error("Handle pending-list failed", e);
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private VoucherOrder mapToVoucherOrder(Map<Object, Object> values) {
        return BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
    }

    private boolean isValidVoucherOrder(VoucherOrder voucherOrder) {
        return voucherOrder != null
                && voucherOrder.getId() != null
                && voucherOrder.getUserId() != null
                && voucherOrder.getVoucherId() != null;
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

    private void initStreamGroup() {
        try {
            Boolean hasKey = stringRedisTemplate.hasKey(STREAM_ORDERS);
            if (Boolean.FALSE.equals(hasKey)) {
                // Stream 需要先有键才能建消费组，初始化时写一条占位消息即可。
                stringRedisTemplate.opsForStream().add(STREAM_ORDERS, Collections.singletonMap("init", "0"));
            }
            stringRedisTemplate.opsForStream().createGroup(STREAM_ORDERS, GROUP_NAME);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || !message.contains("BUSYGROUP")) {
                log.warn("Create stream group skipped: {}", message);
            }
        }
    }
}
