package com.hmdp.mq;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;


@Component
@Slf4j
@AllArgsConstructor
public class MessageListener {

    private final IVoucherOrderService iVoucherOrderService;
    private final RabbitTemplate rabbitTemplate;
    /**
     * RabbitMQ监听器，用于消费订单消息
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME)
    public void handleVoucherOrder(Message message, Channel channel) { // 加 Channel
        log.info("接收到消息：{}", message);
        // 消息唯一标识
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            //获取消息体map
            Object payload = rabbitTemplate.getMessageConverter().fromMessage(message);

            if (payload instanceof Map) {
                Map<String, Object> orderMap = (Map<String, Object>) payload;
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(orderMap, new VoucherOrder(), true);
                iVoucherOrderService.saveVoucherOrder(voucherOrder);
                // 确认消息
                channel.basicAck(deliveryTag, false);
                log.info("订单处理完成，消息已确认");
            } else {
                log.error("消息类型错误：{}", payload != null ? payload.getClass() : "null");
                // 无法处理的消息：直接丢弃 / 死信队列
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception e) {
            log.error("订单处理异常", e);
            try {
                // 异常：拒绝消息，并且重新入队（true=重入队，false=丢弃/死信）
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("消息ACK异常", ex);
            }
        }
    }
}
