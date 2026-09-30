package com.olehshklyar.lodestar.client.ntfy;

import com.olehshklyar.lodestar.config.NtfyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NtfyClientImplTest {

    private static final String SERVER_URL = "https://ntfy.sh";
    private static final String DEFAULT_TOPIC = "lodestar-alerts";

    @Test
    @DisplayName("Should return true in dry-run mode without sending network requests")
    void shouldReturnTrueInDryRunMode() {
        // Arrange
        NtfyProperties properties = new NtfyProperties(SERVER_URL, DEFAULT_TOPIC, true);
        RestClient.Builder builder = RestClient.builder();
        NtfyClientImpl client = new NtfyClientImpl(builder, properties);

        // Act
        boolean result = client.sendMessage("my-topic", "Air Raid Alert!");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should deliver push notification with correct URL, body, and headers")
    void shouldDeliverNotificationWithHeaders() {
        // Arrange
        NtfyProperties properties = new NtfyProperties(SERVER_URL, DEFAULT_TOPIC, false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NtfyClientImpl client = new NtfyClientImpl(builder, properties);

        server.expect(requestTo("https://ntfy.sh/custom-topic"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("Kyiv Alert: Missile danger"))
                .andExpect(header("Title", "Critical Alert"))
                .andExpect(header("Priority", "urgent"))
                .andRespond(withSuccess());

        // Act
        boolean result = client.sendMessage("custom-topic", "Kyiv Alert: Missile danger", "Critical Alert", "urgent");

        // Assert
        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Should resolve full URL when topicOrUrl is already an HTTP URL")
    void shouldDeliverNotificationWhenFullUrlProvided() {
        // Arrange
        NtfyProperties properties = new NtfyProperties(SERVER_URL, DEFAULT_TOPIC, false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NtfyClientImpl client = new NtfyClientImpl(builder, properties);

        server.expect(requestTo("https://my-ntfy-server.example.com/alerts"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        // Act
        boolean result = client.sendMessage("https://my-ntfy-server.example.com/alerts", "Test alert");

        // Assert
        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Should fallback to default topic when topicOrUrl is null or blank")
    void shouldFallbackToDefaultTopicWhenBlank() {
        // Arrange
        NtfyProperties properties = new NtfyProperties(SERVER_URL, DEFAULT_TOPIC, false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NtfyClientImpl client = new NtfyClientImpl(builder, properties);

        server.expect(requestTo("https://ntfy.sh/" + DEFAULT_TOPIC))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        // Act
        boolean result = client.sendMessage("", "Test alert");

        // Assert
        assertThat(result).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Should throw NtfyApiException when remote server returns HTTP 500 error")
    void shouldThrowExceptionOnHttpError() {
        // Arrange
        NtfyProperties properties = new NtfyProperties(SERVER_URL, DEFAULT_TOPIC, false);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NtfyClientImpl client = new NtfyClientImpl(builder, properties);

        server.expect(requestTo("https://ntfy.sh/error-topic"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        // Act & Assert
        assertThatThrownBy(() -> client.sendMessage("error-topic", "Test alert"))
                .isInstanceOf(NtfyApiException.class)
                .hasMessageContaining("HTTP error calling ntfy API");
        server.verify();
    }
}
