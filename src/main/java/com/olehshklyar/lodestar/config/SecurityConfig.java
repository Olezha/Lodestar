package com.olehshklyar.lodestar.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Two security profiles:
 * <ul>
 *   <li><b>dev</b> (any profile except 'prod'): open access for local development, Swagger UI and k6 load tests;</li>
 *   <li><b>prod</b>: stateless OAuth2 Resource Server with HS256 JWT and scope-based authorization.
 *       Only health probes and the landing page stay public; everything not listed is denied.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtSecurityProperties.class)
public class SecurityConfig {

    public static final String SCOPE_ALERTS_WRITE = "SCOPE_alerts:write";
    public static final String SCOPE_SUBSCRIPTIONS_MANAGE = "SCOPE_subscriptions:manage";
    public static final String SCOPE_REGIONS_ADMIN = "SCOPE_regions:admin";

    static final int MIN_SECRET_BYTES = 32;

    @Bean
    @Profile("!prod")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/favicon.ico",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/actuator/**",
                    "/api/v1/subscriptions/**",
                    "/api/v1/alerts/**",
                    "/api/v1/regions/**"
                ).permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    @Profile("prod")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            // Bearer tokens are not sent automatically by browsers, so CSRF protection is not applicable
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/favicon.ico").permitAll()
                // Kubernetes startup/liveness/readiness probes must stay reachable without credentials
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/alerts/**").hasAuthority(SCOPE_ALERTS_WRITE)
                .requestMatchers("/api/v1/subscriptions/**").hasAuthority(SCOPE_SUBSCRIPTIONS_MANAGE)
                .requestMatchers("/api/v1/regions/**").hasAuthority(SCOPE_REGIONS_ADMIN)
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Profile("prod")
    public JwtDecoder jwtDecoder(JwtSecurityProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "lodestar.security.jwt.secret (JWT_SECRET) must be at least " + MIN_SECRET_BYTES
                            + " bytes for HMAC-SHA256 in the 'prod' profile");
        }
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Default validators (exp/nbf with 60s clock skew) plus a strict issuer check
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }
}
