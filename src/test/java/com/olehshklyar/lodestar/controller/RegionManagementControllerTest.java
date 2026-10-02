package com.olehshklyar.lodestar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehshklyar.lodestar.dto.AssignLocationRequest;
import com.olehshklyar.lodestar.dto.CanonicalRegionResponse;
import com.olehshklyar.lodestar.dto.CreateCanonicalRegionRequest;
import com.olehshklyar.lodestar.dto.CreateRegionMappingRequest;
import com.olehshklyar.lodestar.dto.RegionMappingResponse;
import com.olehshklyar.lodestar.dto.UnrecognizedLocationResponse;
import com.olehshklyar.lodestar.service.RegionMappingService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RegionMappingService regionMappingService;

    @Test
    @DisplayName("GET /api/v1/regions/canonical should return list of canonical regions")
    void shouldListCanonicalRegions() throws Exception {
        CanonicalRegionResponse r1 = new CanonicalRegionResponse("KYIV_REGION", "Київська область", Instant.now());
        when(regionMappingService.getAllCanonicalRegions()).thenReturn(List.of(r1));

        mockMvc.perform(get("/api/v1/regions/canonical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].regionId").value("KYIV_REGION"))
                .andExpect(jsonPath("$[0].displayName").value("Київська область"));
    }

    @Test
    @DisplayName("POST /api/v1/regions/canonical should create region and return 201 Created")
    void shouldCreateCanonicalRegion() throws Exception {
        CreateCanonicalRegionRequest req = new CreateCanonicalRegionRequest("LVIV_REGION", "Львівська область");
        CanonicalRegionResponse resp = new CanonicalRegionResponse("LVIV_REGION", "Львівська область", Instant.now());

        when(regionMappingService.createCanonicalRegion(any(CreateCanonicalRegionRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/regions/canonical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.regionId").value("LVIV_REGION"))
                .andExpect(jsonPath("$.displayName").value("Львівська область"));
    }

    @Test
    @DisplayName("GET /api/v1/regions/mappings should return all mapping rules")
    void shouldListMappings() throws Exception {
        RegionMappingResponse m = new RegionMappingResponse(1L, "м. київ", "KYIV_REGION", Instant.now(), Instant.now());
        when(regionMappingService.getAllMappings()).thenReturn(List.of(m));

        mockMvc.perform(get("/api/v1/regions/mappings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rawTitle").value("м. київ"))
                .andExpect(jsonPath("$[0].regionId").value("KYIV_REGION"));
    }

    @Test
    @DisplayName("POST /api/v1/regions/mappings should register rule and return 201 Created")
    void shouldAddMapping() throws Exception {
        CreateRegionMappingRequest req = new CreateRegionMappingRequest("м. бровари", "KYIV_REGION");
        RegionMappingResponse resp = new RegionMappingResponse(5L, "м. бровари", "KYIV_REGION", Instant.now(), Instant.now());

        when(regionMappingService.addMapping(any(CreateRegionMappingRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/regions/mappings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.rawTitle").value("м. бровари"))
                .andExpect(jsonPath("$.regionId").value("KYIV_REGION"));
    }

    @Test
    @DisplayName("DELETE /api/v1/regions/mappings/{id} should return 204 No Content")
    void shouldDeleteMapping() throws Exception {
        doNothing().when(regionMappingService).deleteMapping(10L);

        mockMvc.perform(delete("/api/v1/regions/mappings/10"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/regions/unrecognized should return list of unrecognized locations")
    void shouldListUnrecognized() throws Exception {
        UnrecognizedLocationResponse u = new UnrecognizedLocationResponse(1L, "Невпізнана ТГ", 4L, Instant.now(), Instant.now());
        when(regionMappingService.getAllUnrecognizedLocations()).thenReturn(List.of(u));

        mockMvc.perform(get("/api/v1/regions/unrecognized"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rawTitle").value("Невпізнана ТГ"))
                .andExpect(jsonPath("$[0].occurrencesCount").value(4));
    }

    @Test
    @DisplayName("POST /api/v1/regions/unrecognized/{id}/assign should assign location and return 200 OK")
    void shouldAssignUnrecognized() throws Exception {
        AssignLocationRequest req = new AssignLocationRequest("ODESA_REGION");
        RegionMappingResponse resp = new RegionMappingResponse(7L, "Невпізнана ТГ", "ODESA_REGION", Instant.now(), Instant.now());

        when(regionMappingService.assignUnrecognized(eq(1L), any(AssignLocationRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/regions/unrecognized/1/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regionId").value("ODESA_REGION"));
    }

    @Test
    @DisplayName("POST /api/v1/regions/refresh-cache should trigger refresh and return 200 OK")
    void shouldRefreshCache() throws Exception {
        doNothing().when(regionMappingService).refreshCache();

        mockMvc.perform(post("/api/v1/regions/refresh-cache"))
                .andExpect(status().isOk());
    }
}
