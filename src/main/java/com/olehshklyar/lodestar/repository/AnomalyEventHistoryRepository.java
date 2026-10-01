package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.AnomalyEventHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AnomalyEventHistoryRepository extends JpaRepository<AnomalyEventHistory, Long> {

    List<AnomalyEventHistory> findByRegionIdOrderByDetectedAtDesc(String regionId);

    List<AnomalyEventHistory> findByRegionIdAndDetectedAtBetween(String regionId, Instant from, Instant to);
}
