package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.dto.CreateSubscriptionRequest;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.dto.UpdateSubscriptionRequest;
import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.exception.SubscriptionNotFoundException;
import com.olehshklyar.lodestar.repository.AlertSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final AlertSubscriptionRepository subscriptionRepository;

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(CreateSubscriptionRequest request) {
        log.info("Creating subscription for user: {}, channel: {}, region: {}",
                request.userId(), request.channel(), request.regionId());

        AlertSubscription subscription = AlertSubscription.builder()
                .userId(request.userId())
                .channel(request.channel())
                .recipientAddress(request.recipientAddress())
                .regionId(request.regionId())
                .minSeverity(request.minSeverity())
                .active(true)
                .build();

        return SubscriptionResponse.fromEntity(subscriptionRepository.save(subscription));
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscriptionById(Long id) {
        return subscriptionRepository.findById(id)
                .map(SubscriptionResponse::fromEntity)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubscriptionResponse> getSubscriptions(String userId, String regionId, Boolean active) {
        List<AlertSubscription> subscriptions;

        if (userId != null && !userId.isBlank()) {
            subscriptions = (active != null)
                    ? subscriptionRepository.findByUserIdAndActive(userId, active)
                    : subscriptionRepository.findByUserId(userId);
        } else if (regionId != null && !regionId.isBlank()) {
            subscriptions = (active != null && active)
                    ? subscriptionRepository.findByRegionIdAndActiveTrue(regionId)
                    : subscriptionRepository.findByRegionId(regionId);
        } else if (active != null) {
            subscriptions = subscriptionRepository.findByActive(active);
        } else {
            subscriptions = subscriptionRepository.findAll();
        }

        return subscriptions.stream()
                .filter(sub -> regionId == null || regionId.isBlank() || sub.getRegionId().equalsIgnoreCase(regionId))
                .filter(sub -> active == null || sub.isActive() == active)
                .map(SubscriptionResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public SubscriptionResponse updateSubscription(Long id, UpdateSubscriptionRequest request) {
        AlertSubscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new SubscriptionNotFoundException(id));

        if (request.regionId() != null && !request.regionId().isBlank()) {
            subscription.setRegionId(request.regionId());
        }
        if (request.minSeverity() != null && !request.minSeverity().isBlank()) {
            subscription.setMinSeverity(request.minSeverity());
        }
        if (request.active() != null) {
            subscription.setActive(request.active());
        }

        AlertSubscription updatedSubscription = subscriptionRepository.save(subscription);
        log.info("Updated subscription ID: {}, active: {}, region: {}",
                updatedSubscription.getId(), updatedSubscription.isActive(), updatedSubscription.getRegionId());
        return SubscriptionResponse.fromEntity(updatedSubscription);
    }

    @Override
    @Transactional
    public void deleteSubscription(Long id) {
        if (!subscriptionRepository.existsById(id)) {
            throw new SubscriptionNotFoundException(id);
        }
        subscriptionRepository.deleteById(id);
        log.info("Deleted subscription ID: {}", id);
    }

    @Override
    @Transactional
    public void deactivateSubscriptionsForRecipient(String recipientAddress, NotificationChannel channel) {
        List<AlertSubscription> subscriptions = subscriptionRepository
                .findByRecipientAddressAndChannel(recipientAddress, channel);

        if (subscriptions.isEmpty()) {
            log.debug("No subscriptions found to deactivate for recipient: {}", recipientAddress);
            return;
        }

        subscriptions.forEach(sub -> sub.setActive(false));
        subscriptionRepository.saveAll(subscriptions);
        log.info("Deactivated {} subscription(s) for recipient: {}", subscriptions.size(), recipientAddress);
    }
}
