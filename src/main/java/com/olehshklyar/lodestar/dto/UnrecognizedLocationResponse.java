package com.olehshklyar.lodestar.dto;

import java.time.Instant;

public record UnrecognizedLocationResponse(
        Long id,
        String rawTitle,
        Long occurrencesCount,
        Instant firstSeenAt,
        Instant lastSeenAt
) {}
