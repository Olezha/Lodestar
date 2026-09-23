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
 * Append-only entity recording every raw alert event received by the system.
 * Used for durable time-series archiving, audit trails, and analytical datasets.
 */
@Entity
@Table(name = "alert_events_history", indexes = {
    @Index(name = "idx_alert_history_region_ts", columnList = "region_id, event_timestamp"),
    @Index(name = "idx_alert_history_event_id", columnList = "event_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertEventHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "region_id", nullable = false)
    private String regionId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "kafka_topic", nullable = false)
    private String kafkaTopic;

    @Column(name = "kafka_partition", nullable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false)
    private long kafkaOffset;

    @PrePersist
    protected void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
    }
}
