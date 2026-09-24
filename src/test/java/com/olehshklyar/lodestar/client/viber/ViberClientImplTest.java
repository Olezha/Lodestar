package com.olehshklyar.lodestar.client.viber;

import com.olehshklyar.lodestar.config.ViberProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ViberClientImplTest {

    private static final String API_URL = "https://chatapi.viber.com/pa/send_message";
    private static final String AUTH_TOKEN = "test-auth-token";

    @Test
    @DisplayName("Should return true in dry-run mode without sending network requests")
    void shouldReturnTrueInDryRunMode() {
        // Arrange
        ViberProperties properties = new ViberProperties(API_URL, AUTH_TOKEN, "TestSender", true);
        RestClient.Builder builder = RestClient.builder();
        ViberClientImpl client = new ViberClientImpl(builder, properties);

        // Act
        boolean result = client.sendMessage("viber-chat-id-101", "Air Raid Alert!");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should successfully send message when Viber API returns status 0")
    void shouldSendMessageSuccessfullyWhenViberReturnsStatusZero() {
        // Arrange
        ViberProperties properties = new ViberProperties(API_URL, AUTH_TOKEN, "TestSender", false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViberClientImpl client = new ViberClientImpl(builder, properties);

        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Viber-Auth-Token", AUTH_TOKEN))
                .andRespond(withSuccess("""
                        {
                            "status": 0,
                            "status_message": "ok",
                            "message_token": 123456789
                        }
                        """, MediaType.APPLICATION_JSON));

        // Act
        boolean result = client.sendMessage("viber-chat-id-101", "Air Raid Alert!");

        // Assert
        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Should throw ViberApiException when Viber API returns non-zero status")
    void shouldThrowViberApiExceptionWhenStatusNonZero() {
        // Arrange
        ViberProperties properties = new ViberProperties(API_URL, AUTH_TOKEN, "TestSender", false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViberClientImpl client = new ViberClientImpl(builder, properties);

        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                            "status": 5,
                            "status_message": "receiverNotRegistered"
                        }
                        """, MediaType.APPLICATION_JSON));

        // Act & Assert
        assertThatThrownBy(() -> client.sendMessage("unregistered-id", "Air Raid Alert!"))
                .isInstanceOf(ViberApiException.class)
                .hasMessageContaining("receiverNotRegistered");
        server.verify();
    }

    @Test
    @DisplayName("Should throw ViberApiException when remote server returns HTTP 500 error")
    void shouldThrowViberApiExceptionOnHttpError() {
        // Arrange
        ViberProperties properties = new ViberProperties(API_URL, AUTH_TOKEN, "TestSender", false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ViberClientImpl client = new ViberClientImpl(builder, properties);

        server.expect(requestTo(API_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // Act & Assert
        assertThatThrownBy(() -> client.sendMessage("viber-chat-id-101", "Air Raid Alert!"))
                .isInstanceOf(ViberApiException.class)
                .hasMessageContaining("HTTP error calling Viber API");
        server.verify();
    }
}
