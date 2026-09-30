package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.dto.CreateSubscriptionRequest;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.dto.UpdateSubscriptionRequest;
import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.exception.SubscriptionNotFoundException;
import com.olehshklyar.lodestar.repository.AlertSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private AlertSubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private AlertSubscription sampleSubscription;

    @BeforeEach
    void setUp() {
        sampleSubscription = AlertSubscription.builder()
                .id(1L)
                .userId("user-100")
                .channel(NotificationChannel.VIBER)
                .recipientAddress("viber-chat-id-100")
                .regionId("KYIV_REGION")
                .minSeverity("INFO")
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should create and return subscription")
    void shouldCreateSubscription() {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "user-100",
                NotificationChannel.VIBER,
                "viber-chat-id-100",
                "KYIV_REGION",
                "WARNING"
        );

        when(subscriptionRepository.save(any(AlertSubscription.class))).thenAnswer(invocation -> {
            AlertSubscription sub = invocation.getArgument(0);
            sub.setId(10L);
            return sub;
        });

        SubscriptionResponse response = subscriptionService.createSubscription(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.userId()).isEqualTo("user-100");
        assertThat(response.channel()).isEqualTo(NotificationChannel.VIBER);
        assertThat(response.regionId()).isEqualTo("KYIV_REGION");
        assertThat(response.minSeverity()).isEqualTo("WARNING");
        assertThat(response.active()).isTrue();
    }

    @Test
    @DisplayName("Should return subscription by ID when present")
    void shouldGetSubscriptionById() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sampleSubscription));

        SubscriptionResponse response = subscriptionService.getSubscriptionById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo("user-100");
    }

    @Test
    @DisplayName("Should throw SubscriptionNotFoundException when ID does not exist")
    void shouldThrowWhenSubscriptionNotFound() {
        when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getSubscriptionById(999L))
                .isInstanceOf(SubscriptionNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should update subscription details")
    void shouldUpdateSubscription() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sampleSubscription));
        when(subscriptionRepository.save(any(AlertSubscription.class))).thenAnswer(i -> i.getArgument(0));

        UpdateSubscriptionRequest updateRequest = new UpdateSubscriptionRequest("LVIV_REGION", "CRITICAL", false);

        SubscriptionResponse updated = subscriptionService.updateSubscription(1L, updateRequest);

        assertThat(updated.regionId()).isEqualTo("LVIV_REGION");
        assertThat(updated.minSeverity()).isEqualTo("CRITICAL");
        assertThat(updated.active()).isFalse();
    }

    @Test
    @DisplayName("Should deactivate subscriptions for specified recipient and channel")
    void shouldDeactivateSubscriptionsForRecipient() {
        when(subscriptionRepository.findByRecipientAddressAndChannel("viber-chat-id-100", NotificationChannel.VIBER))
                .thenReturn(List.of(sampleSubscription));

        subscriptionService.deactivateSubscriptionsForRecipient("viber-chat-id-100", NotificationChannel.VIBER);

        assertThat(sampleSubscription.isActive()).isFalse();
        verify(subscriptionRepository).saveAll(List.of(sampleSubscription));
    }

    @Test
    @DisplayName("Should register new default Viber subscription if user has none")
    void shouldRegisterNewViberSubscriber() {
        when(subscriptionRepository.findByRecipientAddressAndChannel("new-viber-id", NotificationChannel.VIBER))
                .thenReturn(List.of());

        when(subscriptionRepository.save(any(AlertSubscription.class))).thenAnswer(invocation -> {
            AlertSubscription sub = invocation.getArgument(0);
            sub.setId(55L);
            return sub;
        });

        SubscriptionResponse response = subscriptionService.activateOrRegisterViberSubscriber("new-viber-id", "Alice");

        assertThat(response.id()).isEqualTo(55L);
        assertThat(response.channel()).isEqualTo(NotificationChannel.VIBER);
        assertThat(response.recipientAddress()).isEqualTo("new-viber-id");
        assertThat(response.userId()).isEqualTo("Alice");
        assertThat(response.active()).isTrue();
    }
}
