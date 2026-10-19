package org.example;

import org.example.mail.LoggingVerificationMailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class PremiumUpgradeFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingVerificationMailSender mailSender;

    @Test
    void requestingACodeChangesNothingUntilItIsConfirmed() throws Exception {
        String token = register("premium.flow", "premium.flow@example.com");
        String connected = "connect.flow@example.com";

        mockMvc.perform(post("/api/premium-users/request-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s"}""".formatted(connected)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(connected)))
                // Mail is off in the tests: the answer must not claim an email went out
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("server's log")));

        mockMvc.perform(get("/api/premium-users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        assertThat(rolesAfterFreshLogin("premium.flow")).doesNotContain("ROLE_PREMIUM");

        String code = codeFor(connected);
        confirm(token, connected, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectedEmail").value(connected));

        mockMvc.perform(get("/api/premium-users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        assertThat(rolesAfterFreshLogin("premium.flow")).contains("ROLE_PREMIUM");
    }

    @Test
    void aWrongCodeIsRefusedAndLeavesTheAccountUnchanged() throws Exception {
        String token = register("premium.wrong", "premium.wrong@example.com");
        String connected = "connect.wrong@example.com";

        requestCode(token, connected);
        String code = codeFor(connected);

        confirm(token, connected, wrongVariantOf(code))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not correct")));

        mockMvc.perform(get("/api/premium-users").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));

        confirm(token, connected, code).andExpect(status().isOk());
    }

    @Test
    void theCodeIsDestroyedAfterTooManyWrongAttempts() throws Exception {
        String token = register("premium.brute", "premium.brute@example.com");
        String connected = "connect.brute@example.com";

        requestCode(token, connected);
        String code = codeFor(connected);
        String wrong = wrongVariantOf(code);

        for (int attempt = 1; attempt <= 4; attempt++) {
            confirm(token, connected, wrong).andExpect(status().isBadRequest());
        }
        confirm(token, connected, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Too many incorrect attempts")));

        confirm(token, connected, code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("No verification code is pending")));

        mockMvc.perform(get("/api/premium-users").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anAddressAlreadyConnectedElsewhereIsRefusedBeforeAnyCodeIsSent() throws Exception {
        String owner = register("premium.owner", "premium.owner@example.com");
        String shared = "contested@example.com";
        requestCode(owner, shared);
        confirm(owner, shared, codeFor(shared)).andExpect(status().isOk());

        String other = register("premium.other", "premium.other@example.com");
        mockMvc.perform(post("/api/premium-users/request-code")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s"}""".formatted(shared)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("already connected to another account")));
    }

    @Test
    void confirmingRejectsAnythingThatIsNotSixDigits() throws Exception {
        String token = register("premium.format", "premium.format@example.com");
        requestCode(token, "connect.format@example.com");

        confirm(token, "connect.format@example.com", "12ab56")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists());
    }

    @Test
    void theUpgradeEndpointsAreClosedToAnonymousCallers() throws Exception {
        mockMvc.perform(post("/api/premium-users/request-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"nobody@example.com"}"""))
                .andExpect(status().isUnauthorized());
    }

    private void requestCode(String token, String email) throws Exception {
        mockMvc.perform(post("/api/premium-users/request-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s"}""".formatted(email)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String email,
                                                                       String code) throws Exception {
        return mockMvc.perform(post("/api/premium-users/confirm")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"connectedEmail":"%s","code":"%s"}""".formatted(email, code)));
    }

    private String codeFor(String email) {
        return mailSender.lastCodeFor(email)
                .orElseThrow(() -> new AssertionError("No code was sent to " + email));
    }

    private static String wrongVariantOf(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"password123"}"""
                                .formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return tokenOf(result);
    }

    private String rolesAfterFreshLogin(String username) throws Exception {
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password123"}""".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult me = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokenOf(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(me.getResponse().getContentAsString()).path("roles").toString();
    }

    private String tokenOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("token").asString();
        assertThat(token).isNotBlank();
        return token;
    }
}
