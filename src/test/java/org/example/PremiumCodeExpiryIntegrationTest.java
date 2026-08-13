package org.example;

import org.example.mail.LoggingVerificationMailSender;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "app.verification.code-ttl=0s")
class PremiumCodeExpiryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingVerificationMailSender mailSender;

    @Test
    void anExpiredCodeIsRefusedAndCannotBeRetried() throws Exception {
        String token = register("premium.expired", "premium.expired@example.com");
        String connected = "connect.expired@example.com";

        mockMvc.perform(post("/api/premium-users/request-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s"}""".formatted(connected)))
                .andExpect(status().isOk());

        String code = mailSender.lastCodeFor(connected)
                .orElseThrow(() -> new AssertionError("No code was sent to " + connected));

        mockMvc.perform(post("/api/premium-users/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s","code":"%s"}""".formatted(connected, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("That code has expired — request a new one"));

        mockMvc.perform(get("/api/premium-users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"password123"}"""
                                .formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("token").asString();
        assertThat(token).isNotBlank();
        return token;
    }
}
