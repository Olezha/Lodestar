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

    public static final String DISCORD_QUEUE = "notifications.discord";
    public static final String DISCORD_DLQ = "notifications.discord.dlq";
    public static final String DISCORD_ROUTING_KEY = "notification.discord";
    public static final String DISCORD_DLQ_ROUTING_KEY = "notification.discord.dlq";

    public static final String NTFY_QUEUE = "notifications.ntfy";
    public static final String NTFY_DLQ = "notifications.ntfy.dlq";
    public static final String NTFY_ROUTING_KEY = "notification.ntfy";
    public static final String NTFY_DLQ_ROUTING_KEY = "notification.ntfy.dlq";

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
    public Queue discordQueue() {
        return QueueBuilder.durable(DISCORD_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", DISCORD_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue discordDlq() {
        return QueueBuilder.durable(DISCORD_DLQ).build();
    }

    @Bean
    public Binding discordBinding(Queue discordQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(discordQueue).to(notificationsExchange).with(DISCORD_ROUTING_KEY);
    }

    @Bean
    public Binding discordDlqBinding(Queue discordDlq, DirectExchange notificationsDlx) {
        return BindingBuilder.bind(discordDlq).to(notificationsDlx).with(DISCORD_DLQ_ROUTING_KEY);
    }

    @Bean
    public Queue ntfyQueue() {
        return QueueBuilder.durable(NTFY_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATIONS_DLX)
                .withArgument("x-dead-letter-routing-key", NTFY_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue ntfyDlq() {
        return QueueBuilder.durable(NTFY_DLQ).build();
    }

    @Bean
    public Binding ntfyBinding(Queue ntfyQueue, TopicExchange notificationsExchange) {
        return BindingBuilder.bind(ntfyQueue).to(notificationsExchange).with(NTFY_ROUTING_KEY);
    }

    @Bean
    public Binding ntfyDlqBinding(Queue ntfyDlq, DirectExchange notificationsDlx) {
        return BindingBuilder.bind(ntfyDlq).to(notificationsDlx).with(NTFY_DLQ_ROUTING_KEY);
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
