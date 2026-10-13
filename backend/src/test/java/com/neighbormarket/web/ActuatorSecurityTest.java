package com.neighbormarket.web;

import com.neighbormarket.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ActuatorSecurityTest.TestEndpoints.class)
@Import({SecurityConfig.class, ActuatorSecurityTest.TestEndpoints.class})
@TestPropertySource(properties = "app.allowed-origins=http://localhost")
class ActuatorSecurityTest {
    @Autowired
    MockMvc mvc;

    @Test
    void healthAndInfoArePublicButOtherActuatorPathsRequireAuthentication() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/info")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @RestController
    public static class TestEndpoints {
        @GetMapping({"/actuator/health", "/actuator/info", "/actuator/env"})
        String ok() {
            return "ok";
        }
    }
}
