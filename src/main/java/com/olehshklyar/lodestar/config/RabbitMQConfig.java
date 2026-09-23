package com.olehshklyar.lodestar.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure configuration:
 * Defines notification exchanges, Viber delivery queue with Dead Letter Exchange (DLX) & DLQ,
 * and JSON message conversion.
 */
@Configuration
public class RabbitMQConfig {

    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String NOTIFICATIONS_DLX = "notifications.dlx";

    public static final String VIBER_QUEUE = "notifications.viber";
    public static final String VIBER_DLQ = "notifications.viber.dlq";

    public static final String VIBER_ROUTING_KEY = "notification.viber";
    public static final String VIBER_DLQ_ROUTING_KEY = "notification.viber.dlq";

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(NOTIFICATIONS_EXCHANGE);
    }

    @Bean
    public DirectExchange notificationsDlx() {
        return new DirectExchange(NOTIFICATIONS_DLX);
    }

    @Bean
    public Queue viberQueue() {
        return QueueBuilder.durable(VIBER_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", VIBER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue viberDlq() {
        return QueueBuilder.durable(VIBER_DLQ).build();
    }

    @Bean
    public Binding viberBinding(Queue viberQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(viberQueue).to(notificationsExchange).with(VIBER_ROUTING_KEY);
    }

    @Bean
    public Binding viberDlqBinding(Queue viberDlq, DirectExchange notificationsDlx) {
        return BindingBuilder.bind(viberDlq).to(notificationsDlx).with(VIBER_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter);
        return template;
    }
}
