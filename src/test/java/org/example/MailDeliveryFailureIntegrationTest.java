package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Mail switched on, pointed at a port nothing listens on: every send fails. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "app.verification.mail-enabled=true",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=1"})
class MailDeliveryFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aCodeTheMailServerRefusedIsReportedAndCanBeAskedForAgainAtOnce() throws Exception {
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mail.refused","password":"password123"}"""))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(registered.getResponse().getContentAsString()).path("token").asString();

        // Twice: a code that never left must not hold the next one back for the resend interval
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/premium-users/request-code")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"connectedEmail":"mail.refused@example.com"}"""))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").value(containsString("could not be sent")))
                    .andExpect(jsonPath("$.message").value(not(containsString("127.0.0.1"))));
        }
    }
}
