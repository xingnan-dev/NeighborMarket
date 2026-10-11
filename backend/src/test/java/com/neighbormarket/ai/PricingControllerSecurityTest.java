package com.neighbormarket.ai;

import com.neighbormarket.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PricingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.allowed-origins=http://localhost")
class PricingControllerSecurityTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    PricingService service;

    @Test
    void unauthenticatedRequestWithCsrfIsRejectedAsUnauthorized() throws Exception {
        mvc.perform(post("/api/ai/pricing")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"tradeType\":\"FIXED_PRICE\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedRequestWithoutCsrfIsRejectedAsForbidden() throws Exception {
        mvc.perform(post("/api/ai/pricing")
                        .with(user("neighbor_a"))
                        .contentType("application/json")
                        .content("{\"tradeType\":\"FIXED_PRICE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
