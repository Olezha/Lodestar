package com.olehshklyar.lodestar.viber.webhook;

import com.olehshklyar.lodestar.config.ViberProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class ViberSignatureValidatorTest {

    private static final String TEST_SECRET = "test-secret-token-12345";
    private ViberSignatureValidator validator;

    @BeforeEach
    void setUp() {
        ViberProperties properties = new ViberProperties(
                "https://chatapi.viber.com/pa/send_message",
                TEST_SECRET,
                "Test Sender",
                true
        );
        validator = new ViberSignatureValidator(properties);
    }

    private String calculateExpectedSignature(byte[] payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload));
    }

    @Test
    @DisplayName("Should return true when HMAC-SHA256 signature matches payload")
    void shouldReturnTrueWhenSignatureMatches() throws Exception {
        byte[] payload = "{\"event\":\"webhook\",\"timestamp\":1727700000}".getBytes(StandardCharsets.UTF_8);
        String signature = calculateExpectedSignature(payload, TEST_SECRET);

        boolean result = validator.isValid(payload, signature);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should return false when signature is invalid or tampered")
    void shouldReturnFalseWhenSignatureInvalid() {
        byte[] payload = "{\"event\":\"webhook\"}".getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "deadbeef1234567890abcdef";

        boolean result = validator.isValid(payload, invalidSignature);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should return false when signature or payload is null or blank")
    void shouldReturnFalseWhenInputNullOrBlank() {
        byte[] payload = "{\"event\":\"webhook\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(validator.isValid(payload, null)).isFalse();
        assertThat(validator.isValid(payload, "   ")).isFalse();
        assertThat(validator.isValid((byte[]) null, "valid-sig")).isFalse();
    }

    @Test
    @DisplayName("Should return false when auth token is empty")
    void shouldReturnFalseWhenAuthTokenBlank() {
        ViberProperties emptyTokenProps = new ViberProperties(
                "https://chatapi.viber.com",
                "",
                "Sender",
                true
        );
        ViberSignatureValidator unconfiguredValidator = new ViberSignatureValidator(emptyTokenProps);
        byte[] payload = "{\"event\":\"webhook\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(unconfiguredValidator.isValid(payload, "some-sig")).isFalse();
    }
}
