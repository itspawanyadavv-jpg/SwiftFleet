package com.swiftfleet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"swiftfleet.store-mode=memory"})
@AutoConfigureMockMvc
class FleetRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthAndSeededRidersAndNearbyGeoSearchWork() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.storeMode").value("IN_MEMORY_FALLBACK"));

        mockMvc.perform(get("/api/riders/nearby")
                        .param("lat", "28.646705")
                        .param("lng", "77.444478")
                        .param("radiusKm", "3.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void createOrderAndRaceConditionEndpointWork() throws Exception {
        mockMvc.perform(post("/api/demo/reset"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Kabir\",\"customerAddress\":\"Vaishali\",\"itemsSummary\":\"Apples\",\"dropLat\":28.653,\"dropLng\":77.452}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_DISPATCH"))
                .andExpect(jsonPath("$.deliveryOtp").isNotEmpty());

        mockMvc.perform(post("/api/demo/race-condition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winnerRiderId").isNotEmpty())
                .andExpect(jsonPath("$.attempts.length()").value(2));
    }
}

