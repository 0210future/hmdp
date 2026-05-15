package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 */
@Configuration
public class RabbitMQConfig {
    
    /**
     * 创建死信交换机
     */
    @Bean
    public Exchange seckillOrderDlxExchange() {
        return ExchangeBuilder.directExchange(RabbitMQConstants.SECKILL_ORDER_DLX_EXCHANGE)
                .durable(true)
                .build();
    }
    
    /**
     * 创建死信队列
     */
    @Bean
    public Queue seckillOrderDlxQueue() {
        return QueueBuilder.durable(RabbitMQConstants.SECKILL_ORDER_DLX_QUEUE)
                .build();
    }
    
    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding seckillOrderDlxBinding() {
        return BindingBuilder.bind(seckillOrderDlxQueue())
                .to(seckillOrderDlxExchange())
                .with(RabbitMQConstants.SECKILL_ORDER_ROUTING_KEY)
                .noargs();
    }
    
    /**
     * 创建秒杀订单交换机
     */
    @Bean
    public Exchange seckillOrderExchange() {
        return ExchangeBuilder.directExchange(RabbitMQConstants.SECKILL_ORDER_EXCHANGE)
                .durable(true)
                .build();
    }
    
    /**
     * 创建秒杀订单队列（配置死信交换机）
     */
    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new HashMap<>();
        // 设置死信交换机
        args.put("x-dead-letter-exchange", RabbitMQConstants.SECKILL_ORDER_DLX_EXCHANGE);
        // 设置死信路由键
        args.put("x-dead-letter-routing-key", RabbitMQConstants.SECKILL_ORDER_ROUTING_KEY);
        // 消息 TTL（可选，这里设置为 60 秒）
        // args.put("x-message-ttl", 60000);
        
        return QueueBuilder.durable(RabbitMQConstants.SECKILL_ORDER_QUEUE)
                .withArguments(args)
                .build();
    }
    
    /**
     * 绑定秒杀订单队列到交换机
     */
    @Bean
    public Binding seckillOrderBinding() {
        return BindingBuilder.bind(seckillOrderQueue())
                .to(seckillOrderExchange())
                .with(RabbitMQConstants.SECKILL_ORDER_ROUTING_KEY)
                .noargs();
    }
}
