package com.olehshklyar.lodestar.viber.webhook.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing user details in a Viber callback event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ViberUser(
        String id,
        String name,
        String avatar,
        String country,
        String language
) {}
