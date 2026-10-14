package com.olehshklyar.lodestar.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtDecoderConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("Missing secret fails fast at startup")
    void missingSecretFailsFast() {
        assertThatThrownBy(() -> securityConfig.jwtDecoder(new JwtSecurityProperties(null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("Secret shorter than 256 bits fails fast at startup")
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> securityConfig.jwtDecoder(new JwtSecurityProperties("too-short", "lodestar")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Sufficiently long secret produces a decoder")
    void validSecretCreatesDecoder() {
        assertThat(securityConfig.jwtDecoder(
                new JwtSecurityProperties("x".repeat(SecurityConfig.MIN_SECRET_BYTES), null))).isNotNull();
    }

    @Test
    @DisplayName("Blank issuer defaults to 'lodestar'")
    void blankIssuerDefaults() {
        assertThat(new JwtSecurityProperties("s", " ").issuer()).isEqualTo("lodestar");
    }
}
