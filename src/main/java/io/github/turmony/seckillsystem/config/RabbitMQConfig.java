package io.github.turmony.seckillsystem.config;

import io.github.turmony.seckillsystem.common.MQConstant;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 配置队列、交换机、绑定关系、消息转换器等
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 配置消息转换器
     * 使用Jackson将对象转换为JSON格式
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 配置RabbitTemplate
     * 设置消息转换器和确认机制
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());

        // 开启发送确认（可选）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("消息发送成功");
            } else {
                System.err.println("消息发送失败: " + cause);
            }
        });

        // 开启返回确认（可选）
        template.setReturnsCallback(returnedMessage -> {
            System.err.println("消息未路由到队列: " + returnedMessage.getMessage());
        });
        template.setMandatory(true);

        return template;
    }

    /**
     * 配置监听器容器工厂
     * 设置消息转换器、并发数等
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());

        // 设置并发消费者数量
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);

        // 设置预取数量（每次从队列获取的消息数）
        factory.setPrefetchCount(1);

        // 手动确认模式（建议使用，更可靠）
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);

        return factory;
    }

    /**
     * 创建秒杀订单队列
     * durable: 持久化，重启后队列不会丢失
     */
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(MQConstant.SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", MQConstant.SECKILL_DEAD_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MQConstant.SECKILL_DEAD_ROUTING_KEY)
                .build();
    }

    /**
     * 创建秒杀交换机
     * Direct类型：根据routing key精确匹配
     */
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(MQConstant.SECKILL_EXCHANGE, true, false);
    }

    /**
     * 绑定队列到交换机
     * 通过routing key关联
     */
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder
                .bind(seckillQueue())
                .to(seckillExchange())
                .with(MQConstant.SECKILL_ROUTING_KEY);
    }

    /**
     * 创建死信队列（可选）
     * 用于处理消费失败的消息
     */
    @Bean
    public Queue seckillDeadQueue() {
        return QueueBuilder.durable(MQConstant.SECKILL_DEAD_QUEUE).build();
    }

    /**
     * 创建死信交换机（可选）
     */
    @Bean
    public DirectExchange seckillDeadExchange() {
        return new DirectExchange(MQConstant.SECKILL_DEAD_EXCHANGE, true, false);
    }

    /**
     * 绑定死信队列（可选）
     */
    @Bean
    public Binding seckillDeadBinding() {
        return BindingBuilder
                .bind(seckillDeadQueue())
                .to(seckillDeadExchange())
                .with(MQConstant.SECKILL_DEAD_ROUTING_KEY);
    }
}