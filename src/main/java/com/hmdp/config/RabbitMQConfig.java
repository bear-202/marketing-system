package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "voucher.exchange";
    public static final String ROUTING_KEY = "voucher.order";
    public static final String QUEUE_NAME = "voucher.queue";

    // 创建交换机
    @Bean
    public DirectExchange voucherExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    // 创建队列
    @Bean
    public Queue voucherQueue() {
        return new Queue(QUEUE_NAME);
    }

    // 绑定交换机和队列
    @Bean
    public Binding voucherBinding() {
        return BindingBuilder.bind(voucherQueue()).to(voucherExchange()).with(ROUTING_KEY);
    }
    // 配置消息转换器，允许反序列化 HashMap
    @Bean
    public MessageConverter messageConverter() {
        // 使用 JacksonJsonMessageConverter
        return new JacksonJsonMessageConverter();
    }

    // 配置 RabbitTemplate 使用自定义的消息转换器
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
