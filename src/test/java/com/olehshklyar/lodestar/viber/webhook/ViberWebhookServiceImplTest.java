package com.olehshklyar.lodestar.viber.webhook;

import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.service.SubscriptionService;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackEvent;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackResponse;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ViberWebhookServiceImplTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private ViberWebhookServiceImpl webhookService;

    @Test
    @DisplayName("Should handle 'webhook' registration handshake")
    void shouldHandleWebhookHandshake() {
        ViberCallbackEvent event = new ViberCallbackEvent("webhook", 1727700000L, 100L, null, null, null);

        ViberCallbackResponse response = webhookService.processEvent(event);

        assertThat(response.status()).isEqualTo(0);
        assertThat(response.statusMessage()).isEqualTo("ok");
        verifyNoInteractions(subscriptionService);
    }

    @Test
    @DisplayName("Should handle 'subscribed' event and activate subscription")
    void shouldHandleSubscribedEvent() {
        ViberUser user = new ViberUser("viber-u1", "Oleh", null, "UA", "uk");
        ViberCallbackEvent event = new ViberCallbackEvent("subscribed", 1727700000L, 101L, user, "viber-u1", null);

        ViberCallbackResponse response = webhookService.processEvent(event);

        assertThat(response.status()).isEqualTo(0);
        verify(subscriptionService).activateOrRegisterViberSubscriber("viber-u1", "Oleh");
    }

    @Test
    @DisplayName("Should handle 'unsubscribed' event and deactivate subscriptions")
    void shouldHandleUnsubscribedEvent() {
        ViberCallbackEvent event = new ViberCallbackEvent("unsubscribed", 1727700000L, 102L, null, "viber-u1", null);

        ViberCallbackResponse response = webhookService.processEvent(event);

        assertThat(response.status()).isEqualTo(0);
        verify(subscriptionService).deactivateSubscriptionsForRecipient("viber-u1", NotificationChannel.VIBER);
    }

    @Test
    @DisplayName("Should handle null or empty event gracefully")
    void shouldHandleNullEventGracefully() {
        ViberCallbackResponse response = webhookService.processEvent(null);

        assertThat(response.status()).isEqualTo(0);
        verifyNoInteractions(subscriptionService);
    }
}
