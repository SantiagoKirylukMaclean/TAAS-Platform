package com.koni.telemetry.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test de integración para Dead Letter Queue (DLQ).
 * Valida que los endpoints de administración de DLQ estén disponibles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DlqIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldExposeDlqAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dlq"))
                .andExpect(status().isOk());
    }
    
    @Test
    void shouldAcceptDlqReprocessRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/dlq/reprocess"))
                .andExpect(status().isAccepted());
    }
}
