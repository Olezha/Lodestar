package com.olehshklyar.lodestar.viber.webhook;

import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.service.SubscriptionService;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackEvent;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViberWebhookServiceImpl implements ViberWebhookService {

    private final SubscriptionService subscriptionService;

    @Override
    public ViberCallbackResponse processEvent(ViberCallbackEvent event) {
        if (event == null || event.event() == null) {
            log.warn("Received empty or malformed Viber callback event");
            return ViberCallbackResponse.OK;
        }

        String eventType = event.event().toLowerCase();
        log.info("Processing Viber callback event: '{}', messageToken: {}", eventType, event.messageToken());

        switch (eventType) {
            case "webhook" -> {
                log.info("Viber webhook endpoint verification handshake succeeded");
                return ViberCallbackResponse.OK;
            }
            case "subscribed" -> {
                String viberUserId = event.getEffectiveUserId();
                String userName = event.user() != null ? event.user().name() : null;
                log.info("User subscribed via Viber: {} ({})", viberUserId, userName);
                if (viberUserId != null) {
                    subscriptionService.activateOrRegisterViberSubscriber(viberUserId, userName);
                }
                return ViberCallbackResponse.OK;
            }
            case "unsubscribed" -> {
                String viberUserId = event.getEffectiveUserId();
                log.info("User unsubscribed via Viber: {}", viberUserId);
                if (viberUserId != null) {
                    subscriptionService.deactivateSubscriptionsForRecipient(viberUserId, NotificationChannel.VIBER);
                }
                return ViberCallbackResponse.OK;
            }
            case "conversation_started" -> {
                String viberUserId = event.getEffectiveUserId();
                log.info("Viber conversation started by user: {}", viberUserId);
                return ViberCallbackResponse.OK;
            }
            case "message" -> {
                log.info("Incoming Viber message received from user: {}", event.getEffectiveUserId());
                return ViberCallbackResponse.OK;
            }
            default -> {
                log.debug("Unhandled Viber callback event type: {}", eventType);
                return ViberCallbackResponse.OK;
            }
        }
    }
}
