package com.olehshklyar.lodestar.dispatcher;

import com.olehshklyar.lodestar.config.RabbitMQConfig;
import com.olehshklyar.lodestar.dto.NotificationTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Service responsible for routing and dispatching notification tasks to the appropriate RabbitMQ queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final RabbitTemplate rabbitTemplate;

    public void dispatch(NotificationTask task) {
        log.info("Dispatching NotificationTask [id={}] for user [{}] via channel [{}] to RabbitMQ",
                task.taskId(), task.userId(), task.channel());

        String routingKey = getRoutingKeyForChannel(task.channel());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.NOTIFICATIONS_EXCHANGE,
                routingKey,
                task
        );

        log.debug("Successfully published NotificationTask [id={}] with routingKey [{}]",
                task.taskId(), routingKey);
    }

    private String getRoutingKeyForChannel(String channel) {
        if ("VIBER".equalsIgnoreCase(channel)) {
            return RabbitMQConfig.VIBER_ROUTING_KEY;
        }
        return RabbitMQConfig.VIBER_ROUTING_KEY; // default fallback
    }
}
