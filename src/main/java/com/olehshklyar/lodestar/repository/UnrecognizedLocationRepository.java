package com.olehshklyar.lodestar.repository;

import com.olehshklyar.lodestar.entity.UnrecognizedLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UnrecognizedLocationRepository extends JpaRepository<UnrecognizedLocation, Long> {

    Optional<UnrecognizedLocation> findByRawTitle(String rawTitle);

    void deleteByRawTitle(String rawTitle);
}
