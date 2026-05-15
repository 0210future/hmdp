package com.hmdp.mq;

import com.hmdp.config.RabbitMQConstants;
import com.hmdp.dto.SeckillOrderMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 秒杀订单消费者
 */
@Slf4j
@Component
public class SeckillOrderConsumer {
    
    @Resource
    private IVoucherOrderService voucherOrderService;
    
    /**
     * 监听秒杀订单队列
     */
    @RabbitListener(queues = RabbitMQConstants.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(SeckillOrderMessage message, Message amqpMessage, Channel channel) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.info("收到秒杀订单消息: {}", message);
            
            // 将消息转换为订单对象
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(message.getOrderId());
            voucherOrder.setUserId(message.getUserId());
            voucherOrder.setVoucherId(message.getVoucherId());
            
            // 保存订单（内部包含事务、重复校验、库存扣减等逻辑）
            boolean success = voucherOrderService.saveVoucherOrder(voucherOrder);
            
            if (success) {
                log.info("秒杀订单处理成功: orderId={}", message.getOrderId());
                // 手动确认消息
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("秒杀订单处理失败，拒绝消息: orderId={}", message.getOrderId());
                // 拒绝消息，不重新入队（进入死信队列）
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception e) {
            log.error("处理秒杀订单消息异常: {}", message, e);
            try {
                // 发生异常，拒绝消息并重新入队（可配置重试次数）
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ioException) {
                log.error("拒绝消息失败", ioException);
            }
        }
    }
    
    /**
     * 监听死信队列（处理多次重试失败的消息）
     */
    @RabbitListener(queues = RabbitMQConstants.SECKILL_ORDER_DLX_QUEUE)
    public void handleDeadLetter(SeckillOrderMessage message, Message amqpMessage, Channel channel) {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        
        try {
            log.error("收到死信消息，需要人工处理: {}", message);
            // TODO: 可以将死信消息记录到数据库或发送告警
            
            // 确认消息
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("处理死信消息异常", e);
        }
    }
}
