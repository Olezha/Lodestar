package com.olehshklyar.lodestar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehshklyar.lodestar.dto.CreateSubscriptionRequest;
import com.olehshklyar.lodestar.dto.SubscriptionResponse;
import com.olehshklyar.lodestar.dto.UpdateSubscriptionRequest;
import com.olehshklyar.lodestar.entity.NotificationChannel;
import com.olehshklyar.lodestar.exception.SubscriptionNotFoundException;
import com.olehshklyar.lodestar.service.SubscriptionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    @DisplayName("POST /api/v1/subscriptions should create subscription and return 201 Created")
    void shouldCreateSubscription() throws Exception {
        CreateSubscriptionRequest request = new CreateSubscriptionRequest(
                "user-1",
                NotificationChannel.VIBER,
                "viber-chat-id-1",
                "KYIV_REGION",
                "WARNING"
        );
        SubscriptionResponse response = new SubscriptionResponse(
                1L, "user-1", NotificationChannel.VIBER, "viber-chat-id-1",
                "KYIV_REGION", "WARNING", true, Instant.now()
        );

        when(subscriptionService.createSubscription(any(CreateSubscriptionRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.regionId").value("KYIV_REGION"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/subscriptions/{id} should return subscription when found")
    void shouldGetSubscriptionById() throws Exception {
        SubscriptionResponse response = new SubscriptionResponse(
                1L, "user-1", NotificationChannel.VIBER, "viber-chat-id-1",
                "KYIV_REGION", "WARNING", true, Instant.now()
        );

        when(subscriptionService.getSubscriptionById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/subscriptions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.userId").value("user-1"));
    }

    @Test
    @DisplayName("GET /api/v1/subscriptions/{id} should return 404 when not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(subscriptionService.getSubscriptionById(999L))
                .thenThrow(new SubscriptionNotFoundException(999L));

        mockMvc.perform(get("/api/v1/subscriptions/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/subscriptions should return list of subscriptions")
    void shouldListSubscriptions() throws Exception {
        SubscriptionResponse response = new SubscriptionResponse(
                1L, "user-1", NotificationChannel.VIBER, "viber-chat-id-1",
                "KYIV_REGION", "WARNING", true, Instant.now()
        );

        when(subscriptionService.getSubscriptions("user-1", null, true))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/subscriptions?userId=user-1&active=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    @DisplayName("PUT /api/v1/subscriptions/{id} should update subscription")
    void shouldUpdateSubscription() throws Exception {
        UpdateSubscriptionRequest updateRequest = new UpdateSubscriptionRequest("LVIV_REGION", "INFO", true);
        SubscriptionResponse updatedResponse = new SubscriptionResponse(
                1L, "user-1", NotificationChannel.VIBER, "viber-chat-id-1",
                "LVIV_REGION", "INFO", true, Instant.now()
        );

        when(subscriptionService.updateSubscription(eq(1L), any(UpdateSubscriptionRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/subscriptions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value("LVIV_REGION"))
                .andExpect(jsonPath("$.minSeverity").value("INFO"));
    }

    @Test
    @DisplayName("DELETE /api/v1/subscriptions/{id} should delete and return 204 No Content")
    void shouldDeleteSubscription() throws Exception {
        doNothing().when(subscriptionService).deleteSubscription(1L);

        mockMvc.perform(delete("/api/v1/subscriptions/1"))
                .andExpect(status().isNoContent());
    }
}
