package com.olehshklyar.lodestar.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.olehshklyar.lodestar.controller.AlertIngestController;
import com.olehshklyar.lodestar.controller.RegionManagementController;
import com.olehshklyar.lodestar.controller.SubscriptionController;
import com.olehshklyar.lodestar.producer.AlertEventProducer;
import com.olehshklyar.lodestar.service.RegionMappingService;
import com.olehshklyar.lodestar.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the 'prod' filter chain end-to-end with real HS256 tokens:
 * signature, issuer and expiry validation in {@code NimbusJwtDecoder} plus scope-based authorization.
 */
@WebMvcTest(controllers = {
        SubscriptionController.class,
        AlertIngestController.class,
        RegionManagementController.class
})
@Import(SecurityConfig.class)
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "lodestar.security.jwt.secret=" + ProdSecurityConfigTest.SECRET,
        "lodestar.security.jwt.issuer=" + ProdSecurityConfigTest.ISSUER
})
class ProdSecurityConfigTest {

    static final String SECRET = "test-secret-key-with-at-least-32-bytes!!";
    static final String ISSUER = "lodestar-test";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private AlertEventProducer alertEventProducer;

    @MockBean
    private RegionMappingService regionMappingService;

    @Test
    @DisplayName("Anonymous request to a business endpoint is rejected with 401 and a Bearer challenge")
    void anonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("Valid token with the required scope is granted access")
    void validTokenWithScopeIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("subscriptions:manage"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Valid token without the required scope is forbidden")
    void validTokenWithoutScopeIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/regions/canonical")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("subscriptions:manage"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Region management requires the regions:admin scope")
    void regionAdminScopeIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/regions/canonical")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("regions:admin"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Alert ingestion requires the alerts:write scope")
    void alertIngestionRequiresWriteScope() throws Exception {
        String body = """
                {"regionId":"KYIV_REGION","eventType":"AIR_RAID"}
                """;

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("subscriptions:manage"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("alerts:write"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Token signed with a different key is rejected")
    void tokenWithForeignSignatureIsUnauthorized() throws Exception {
        String forged = sign(claims("subscriptions:manage", ISSUER, Instant.now().plus(5, ChronoUnit.MINUTES)),
                "another-secret-key-with-at-least-32-bytes");

        mockMvc.perform(get("/api/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Expired token is rejected")
    void expiredTokenIsUnauthorized() throws Exception {
        // Beyond the default 60s clock skew tolerance
        String expired = sign(claims("subscriptions:manage", ISSUER, Instant.now().minus(5, ChronoUnit.MINUTES)), SECRET);

        mockMvc.perform(get("/api/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token from an unexpected issuer is rejected")
    void foreignIssuerIsUnauthorized() throws Exception {
        String foreign = sign(claims("subscriptions:manage", "someone-else", Instant.now().plus(5, ChronoUnit.MINUTES)), SECRET);

        mockMvc.perform(get("/api/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, bearer(foreign)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OpenAPI and non-probe Actuator endpoints are closed even for authenticated callers")
    void documentationAndActuatorAreDenied() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/env")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("subscriptions:manage regions:admin alerts:write"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Health probe paths are reachable without credentials")
    void healthProbesArePublic() throws Exception {
        // The Actuator endpoint itself is not part of the MVC slice, so 404 proves the request passed security
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isNotFound());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String token(String scope) throws JOSEException {
        return sign(claims(scope, ISSUER, Instant.now().plus(5, ChronoUnit.MINUTES)), SECRET);
    }

    private static JWTClaimsSet claims(String scope, String issuer, Instant expiresAt) {
        return new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer(issuer)
                .claim("scope", scope)
                .issueTime(Date.from(expiresAt.minus(10, ChronoUnit.MINUTES)))
                .expirationTime(Date.from(expiresAt))
                .build();
    }

    private static String sign(JWTClaimsSet claims, String secret) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
