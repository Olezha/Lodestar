package com.olehshklyar.lodestar.client.live;

import com.olehshklyar.lodestar.client.RegionNormalizer;
import com.olehshklyar.lodestar.config.AlertSourceProperties;
import com.olehshklyar.lodestar.dto.AlertEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LiveAlertSourceClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private RegionNormalizer regionNormalizer;
    private AlertSourceProperties properties;
    private LiveAlertSourceClient client;

    private static final String BASE_URL = "https://api.alerts.in.ua/v1";
    private static final String API_TOKEN = "test-token-123";

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        regionNormalizer = org.mockito.Mockito.mock(RegionNormalizer.class);
        org.mockito.Mockito.when(regionNormalizer.normalize("м. Київ")).thenReturn("KYIV_REGION");
        org.mockito.Mockito.when(regionNormalizer.normalize("Львівська область")).thenReturn("LVIV_REGION");
        properties = new AlertSourceProperties(
                "live",
                BASE_URL,
                API_TOKEN,
                Duration.ofSeconds(15),
                Duration.ofSeconds(5),
                Duration.ofSeconds(5),
                Duration.ofHours(1)
        );
        client = new LiveAlertSourceClient(restClientBuilder, regionNormalizer, properties);
    }

    @Test
    @DisplayName("Should fetch, deserialize, and map active alerts from alerts.in.ua API")
    void shouldFetchAndMapActiveAlerts() {
        // Arrange
        String jsonResponse = """
                {
                    "alerts": [
                        {
                            "id": 101,
                            "location_title": "м. Київ",
                            "location_type": "city",
                            "started_at": "2026-10-01T20:00:00Z",
                            "finished_at": null,
                            "updated_at": "2026-10-01T20:05:00Z",
                            "alert_type": "air_raid",
                            "location_uid": "31"
                        },
                        {
                            "id": 102,
                            "location_title": "Львівська область",
                            "location_type": "oblast",
                            "started_at": "2026-10-01T20:10:00Z",
                            "finished_at": null,
                            "updated_at": "2026-10-01T20:12:00Z",
                            "alert_type": "artillery_shelling",
                            "location_uid": "27"
                        }
                    ],
                    "disclaimer": "Official alerts stream"
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/alerts/active.json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // Act
        List<AlertEvent> events = client.fetchLatestEvents();

        // Assert
        assertThat(events).hasSize(2);

        AlertEvent kyiv = events.getFirst();
        assertThat(kyiv.eventId()).isEqualTo("alerts-in-ua-101");
        assertThat(kyiv.regionId()).isEqualTo("KYIV_REGION");
        assertThat(kyiv.eventType()).isEqualTo("AIR_RAID");
        assertThat(kyiv.severity()).isEqualTo("CRITICAL");
        assertThat(kyiv.status()).isEqualTo("ACTIVE");

        AlertEvent lviv = events.get(1);
        assertThat(lviv.eventId()).isEqualTo("alerts-in-ua-102");
        assertThat(lviv.regionId()).isEqualTo("LVIV_REGION");
        assertThat(lviv.eventType()).isEqualTo("ARTILLERY");
        assertThat(lviv.severity()).isEqualTo("CRITICAL");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty list when external live API returns 5xx error")
    void shouldReturnEmptyListOnServerError() {
        // Arrange
        mockServer.expect(requestTo(BASE_URL + "/alerts/active.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // Act
        List<AlertEvent> events = client.fetchLatestEvents();

        // Assert
        assertThat(events).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should send If-Modified-Since header when Last-Modified was received and handle 304 Not Modified")
    void shouldHandleConditionalGetAndNotModified() {
        String lastModified = "Wed, 01 Oct 2026 20:00:00 GMT";
        String jsonResponse = """
                {
                    "alerts": [
                        {
                            "id": 101,
                            "location_title": "м. Київ",
                            "alert_type": "air_raid",
                            "started_at": "2026-10-01T20:00:00Z"
                        }
                    ]
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.LAST_MODIFIED, lastModified);

        // First call: returns 200 OK with Last-Modified header
        mockServer.expect(requestTo(BASE_URL + "/alerts/active.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON).headers(headers));

        List<AlertEvent> firstResult = client.fetchLatestEvents();
        assertThat(firstResult).hasSize(1);
        mockServer.verify();

        // Reset expectations for second call
        mockServer.reset();

        // Second call: client sends If-Modified-Since header, server returns 304 Not Modified
        mockServer.expect(requestTo(BASE_URL + "/alerts/active.json"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.IF_MODIFIED_SINCE, lastModified))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        List<AlertEvent> secondResult = client.fetchLatestEvents();
        assertThat(secondResult).isEmpty();
        mockServer.verify();
    }
}
