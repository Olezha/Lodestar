package com.olehshklyar.lodestar.viber.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackEvent;
import com.olehshklyar.lodestar.viber.webhook.dto.ViberCallbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/v1/viber/webhook")
@RequiredArgsConstructor
@Tag(name = "Viber Webhook API", description = "Endpoint for receiving Viber Bot callback events with HMAC-SHA256 signature verification")
public class ViberWebhookController {

    public static final String VIBER_SIGNATURE_HEADER = "X-Viber-Content-Signature";

    private final ViberSignatureValidator signatureValidator;
    private final ViberWebhookService viberWebhookService;
    private final ObjectMapper objectMapper;

    @PostMapping
    @Operation(summary = "Handle incoming Viber callback events with HMAC-SHA256 signature check")
    public ResponseEntity<ViberCallbackResponse> handleWebhook(
            @RequestHeader(value = VIBER_SIGNATURE_HEADER, required = false) String signature,
            @RequestBody byte[] rawPayload) {

        if (!signatureValidator.isValid(rawPayload, signature)) {
            log.warn("Unauthorized Viber webhook attempt: missing or invalid signature header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ViberCallbackEvent event = objectMapper.readValue(rawPayload, ViberCallbackEvent.class);
            ViberCallbackResponse response = viberWebhookService.processEvent(event);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to deserialize Viber webhook payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
}
