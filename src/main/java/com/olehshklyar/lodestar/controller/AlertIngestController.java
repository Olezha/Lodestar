package com.olehshklyar.lodestar.controller;

import com.olehshklyar.lodestar.dto.AlertEvent;
import com.olehshklyar.lodestar.producer.AlertEventProducer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alert Ingestion API", description = "Endpoints for receiving and ingesting raw alert events")
public class AlertIngestController {

    private final AlertEventProducer alertEventProducer;

    @PostMapping
    @Operation(summary = "Publish a raw alert event to Kafka stream")
    public ResponseEntity<AlertEvent> ingestAlert(@RequestBody AlertEvent request) {
        AlertEvent eventToPublish = new AlertEvent(
                request.eventId() != null ? request.eventId() : UUID.randomUUID().toString(),
                request.regionId(),
                request.eventType(),
                request.status() != null ? request.status() : "ACTIVE",
                request.severity() != null ? request.severity() : "WARNING",
                request.timestamp() != null ? request.timestamp() : Instant.now()
        );

        alertEventProducer.sendAlertEvent(eventToPublish);

        return ResponseEntity.ok(eventToPublish);
    }
}
