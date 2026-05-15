package com.hmdp.config;

/**
 * RabbitMQ 常量定义
 */
public class RabbitMQConstants {
    
    /**
     * 秒杀订单交换机
     */
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";
    
    /**
     * 秒杀订单队列
     */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    
    /**
     * 路由键
     */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";
    
    /**
     * 死信交换机
     */
    public static final String SECKILL_ORDER_DLX_EXCHANGE = "seckill.order.dlx.exchange";
    
    /**
     * 死信队列
     */
    public static final String SECKILL_ORDER_DLX_QUEUE = "seckill.order.dlx.queue";
}
