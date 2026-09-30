package com.olehshklyar.lodestar.client.discord;

import com.olehshklyar.lodestar.config.DiscordProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DiscordClientImplTest {

    private static final String DEFAULT_WEBHOOK = "https://discord.com/api/webhooks/default/123";
    private static final String CUSTOM_WEBHOOK = "https://discord.com/api/webhooks/custom/456";

    @Test
    @DisplayName("Should return true in dry-run mode without sending network requests")
    void shouldReturnTrueInDryRunMode() {
        // Arrange
        DiscordProperties properties = new DiscordProperties(DEFAULT_WEBHOOK, "Lodestar Bot", true);
        RestClient.Builder builder = RestClient.builder();
        DiscordClientImpl client = new DiscordClientImpl(builder, properties);

        // Act
        boolean result = client.sendMessage(CUSTOM_WEBHOOK, "Air Raid Alert!");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should deliver message to custom webhook URL when provided")
    void shouldDeliverMessageToCustomWebhookUrl() {
        // Arrange
        DiscordProperties properties = new DiscordProperties(DEFAULT_WEBHOOK, "Lodestar Bot", false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DiscordClientImpl client = new DiscordClientImpl(builder, properties);

        server.expect(requestTo(CUSTOM_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                            "content": "Air Raid Alert!",
                            "username": "Lodestar Bot"
                        }
                        """))
                .andRespond(withNoContent());

        // Act
        boolean result = client.sendMessage(CUSTOM_WEBHOOK, "Air Raid Alert!");

        // Assert
        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Should fallback to default webhook URL when custom webhook is null or blank")
    void shouldFallbackToDefaultWebhookWhenNotSpecified() {
        // Arrange
        DiscordProperties properties = new DiscordProperties(DEFAULT_WEBHOOK, "Lodestar Bot", false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DiscordClientImpl client = new DiscordClientImpl(builder, properties);

        server.expect(requestTo(DEFAULT_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                            "content": "Air Raid Alert!",
                            "username": "Lodestar Bot"
                        }
                        """))
                .andRespond(withSuccess());

        // Act
        boolean result = client.sendMessage("", "Air Raid Alert!");

        // Assert
        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Should throw DiscordApiException when both custom and default webhook URLs are empty")
    void shouldThrowExceptionWhenNoWebhookConfigured() {
        // Arrange
        DiscordProperties properties = new DiscordProperties("", "Lodestar Bot", false);
        RestClient.Builder builder = RestClient.builder();
        DiscordClientImpl client = new DiscordClientImpl(builder, properties);

        // Act & Assert
        assertThatThrownBy(() -> client.sendMessage(null, "Air Raid Alert!"))
                .isInstanceOf(DiscordApiException.class)
                .hasMessageContaining("Discord webhook URL must not be blank");
    }

    @Test
    @DisplayName("Should throw DiscordApiException on HTTP 500 error from Discord")
    void shouldThrowExceptionOnHttpError() {
        // Arrange
        DiscordProperties properties = new DiscordProperties(DEFAULT_WEBHOOK, "Lodestar Bot", false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DiscordClientImpl client = new DiscordClientImpl(builder, properties);

        server.expect(requestTo(CUSTOM_WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> client.sendMessage(CUSTOM_WEBHOOK, "Air Raid Alert!"))
                .isInstanceOf(DiscordApiException.class)
                .hasMessageContaining("HTTP error calling Discord webhook");
        server.verify();
    }
}
