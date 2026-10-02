package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.RegionMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegionMappingRepository extends JpaRepository<RegionMapping, Long> {

    Optional<RegionMapping> findByRawTitle(String rawTitle);

    boolean existsByRawTitle(String rawTitle);

    void deleteByRawTitle(String rawTitle);
}
