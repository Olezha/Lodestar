package com.olehshklyar.lodestar.dto;

public record UpdateSubscriptionRequest(
        String regionId,
        String minSeverity,
        Boolean active
) {}
