package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.AlertSubscription;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, Long> {

    List<AlertSubscription> findByRegionIdAndActiveTrue(String regionId);

    List<AlertSubscription> findByUserId(String userId);

    List<AlertSubscription> findByUserIdAndActive(String userId, boolean active);

    List<AlertSubscription> findByRegionId(String regionId);

    List<AlertSubscription> findByActive(boolean active);

    List<AlertSubscription> findByRecipientAddressAndChannel(String recipientAddress, NotificationChannel channel);

    List<AlertSubscription> findByRecipientAddressAndChannelAndActiveTrue(String recipientAddress, NotificationChannel channel);

    Optional<AlertSubscription> findByUserIdAndRegionIdAndChannel(String userId, String regionId, NotificationChannel channel);
}
