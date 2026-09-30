package com.olehshklyar.lodestar.service;

import com.olehshklyar.lodestar.dto.CreateSubscriptionRequest;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.dto.UpdateSubscriptionRequest;
import com.olehshklyar.lodestar.entity.NotificationChannel;

import java.util.List;

public interface SubscriptionService {

    SubscriptionResponse createSubscription(CreateSubscriptionRequest request);

    SubscriptionResponse getSubscriptionById(Long id);

    List<SubscriptionResponse> getSubscriptions(String userId, String regionId, Boolean active);

    SubscriptionResponse updateSubscription(Long id, UpdateSubscriptionRequest request);

    void deleteSubscription(Long id);

    void deactivateSubscriptionsForRecipient(String recipientAddress, NotificationChannel channel);

    SubscriptionResponse activateOrRegisterViberSubscriber(String viberUserId, String userName);
}
