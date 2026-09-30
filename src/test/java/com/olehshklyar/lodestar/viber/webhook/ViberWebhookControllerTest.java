package com.olehshklyar.lodestar.viber.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackEvent;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackResponse;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViberWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class ViberWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ViberSignatureValidator signatureValidator;

    @MockBean
    private ViberWebhookService viberWebhookService;

    @Test
    @DisplayName("Should return 401 Unauthorized when signature is invalid")
    void shouldReturnUnauthorizedWhenSignatureInvalid() throws Exception {
        byte[] payload = "{\"event\":\"webhook\"}".getBytes();
        when(signatureValidator.isValid(any(byte[].class), eq("invalid-signature"))).thenReturn(false);

        mockMvc.perform(post("/api/v1/viber/webhook")
                        .header("X-Viber-Content-Signature", "invalid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should process valid webhook callback and return 200 OK")
    void shouldProcessValidWebhook() throws Exception {
        ViberCallbackEvent event = new ViberCallbackEvent(
                "subscribed",
                1727700000L,
                12345L,
                new ViberUser("viber-user-1", "John Doe", null, "UA", "uk"),
                "viber-user-1",
                null
        );
        byte[] payload = objectMapper.writeValueAsBytes(event);

        when(signatureValidator.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);
        when(viberWebhookService.processEvent(any(ViberCallbackEvent.class))).thenReturn(ViberCallbackResponse.OK);

        mockMvc.perform(post("/api/v1/viber/webhook")
                        .header("X-Viber-Content-Signature", "valid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.status_message").value("ok"));

        verify(viberWebhookService).processEvent(any(ViberCallbackEvent.class));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when JSON is unparseable but signature valid")
    void shouldReturnBadRequestWhenJsonInvalid() throws Exception {
        byte[] invalidJson = "{not-json".getBytes();
        when(signatureValidator.isValid(any(byte[].class), eq("valid-signature"))).thenReturn(true);

        mockMvc.perform(post("/api/v1/viber/webhook")
                        .header("X-Viber-Content-Signature", "valid-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
