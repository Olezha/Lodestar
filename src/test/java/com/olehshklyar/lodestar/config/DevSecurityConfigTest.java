package com.olehshklyar.lodestar.config;

import com.olehshklyar.lodestar.controller.SubscriptionController;
import com.olehshklyar.lodestar.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without the 'prod' profile the API stays open for local development and load tests,
 * and no JWT secret is required.
 */
@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class DevSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    @DisplayName("Business endpoints are accessible anonymously outside the 'prod' profile")
    void anonymousAccessIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("JWT decoder is not created outside the 'prod' profile")
    void jwtDecoderIsAbsent() {
        assertThat(context.getBeanNamesForType(JwtDecoder.class)).isEmpty();
    }
}
