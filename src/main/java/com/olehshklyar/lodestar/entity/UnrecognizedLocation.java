package com.olehshklyar.lodestar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Entity tracking location strings encountered during alert ingestion that could not be matched
 * to any canonical region mapping. Enables operational observability and manual assignment.
 */
@Entity
@Table(name = "unrecognized_locations", indexes = {
    @Index(name = "idx_unrecognized_locations_title", columnList = "raw_title")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnrecognizedLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_title", nullable = false, unique = true)
    private String rawTitle;

    @Column(name = "occurrences_count", nullable = false)
    private Long occurrencesCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        if (occurrencesCount == null) {
            occurrencesCount = 1L;
        }
    }
}
