package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.AlertSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertSubscriptionRepository extends JpaRepository<AlertSubscription, Long> {

    List<AlertSubscription> findByRegionIdAndActiveTrue(String regionId);

    List<AlertSubscription> findByUserId(String userId);
}
