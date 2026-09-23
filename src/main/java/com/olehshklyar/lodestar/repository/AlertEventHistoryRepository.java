package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.AlertEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertEventHistoryRepository extends JpaRepository<AlertEventHistory, Long> {

    List<AlertEventHistory> findByRegionIdOrderByEventTimestampDesc(String regionId);

    List<AlertEventHistory> findByRegionIdAndEventTimestampBetween(String regionId, Instant from, Instant to);
}
