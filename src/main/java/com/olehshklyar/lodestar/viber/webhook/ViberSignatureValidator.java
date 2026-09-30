package com.olehshklyar.lodestar.viber.webhook;

import com.olehshklyar.lodestar.config.ViberProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates incoming Viber webhook payloads using HMAC-SHA256 signature verification.
 * The signature is transmitted via the 'X-Viber-Content-Signature' HTTP header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViberSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final ViberProperties viberProperties;

    /**
     * Verifies that the provided signature matches the HMAC-SHA256 digest of the raw payload.
     * Uses constant-time comparison to prevent timing side-channel attacks.
     *
     * @param rawPayload raw JSON payload bytes or string received in HTTP request body
     * @param signature  the hex-encoded signature from X-Viber-Content-Signature header
     * @return true if signature is valid, false otherwise
     */
    public boolean isValid(byte[] rawPayload, String signature) {
        if (signature == null || signature.isBlank() || rawPayload == null) {
            log.warn("Missing Viber webhook signature or payload");
            return false;
        }

        String authToken = viberProperties.authToken();
        if (authToken == null || authToken.isBlank()) {
            log.warn("Viber auth token is not configured, rejecting webhook request");
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(authToken.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);
            byte[] calculatedHmac = mac.doFinal(rawPayload);
            String calculatedHex = HexFormat.of().formatHex(calculatedHmac);

            return MessageDigest.isEqual(
                    calculatedHex.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to calculate HMAC-SHA256 for Viber webhook verification", e);
            return false;
        }
    }

    public boolean isValid(String rawPayload, String signature) {
        if (rawPayload == null) {
            return false;
        }
        return isValid(rawPayload.getBytes(StandardCharsets.UTF_8), signature);
    }
}
