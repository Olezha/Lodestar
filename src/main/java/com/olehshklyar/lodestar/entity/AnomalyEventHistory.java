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
 * Append-only entity recording stream anomalies detected across sliding time windows.
 */
@Entity
@Table(name = "anomaly_events_history", indexes = {
    @Index(name = "idx_anomaly_history_region_ts", columnList = "region_id, detected_at"),
    @Index(name = "idx_anomaly_history_id", columnList = "anomaly_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyEventHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anomaly_id", nullable = false)
    private String anomalyId;

    @Column(name = "region_id", nullable = false)
    private String regionId;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "window_duration_seconds", nullable = false)
    private long windowDurationSeconds;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
