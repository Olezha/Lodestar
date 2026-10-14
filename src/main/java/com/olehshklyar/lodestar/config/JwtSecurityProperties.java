package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT verification settings for the 'prod' security profile.
 * Tokens are signed with a shared HMAC-SHA256 secret and must carry the configured issuer.
 */
@ConfigurationProperties(prefix = "lodestar.security.jwt")
public record JwtSecurityProperties(
        String secret,
        String issuer
) {
    public JwtSecurityProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "lodestar";
        }
    }
}
