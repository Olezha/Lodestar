package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.CanonicalRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CanonicalRegionRepository extends JpaRepository<CanonicalRegion, String> {
}
