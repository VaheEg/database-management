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
class InvitationPremiumGateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingVerificationMailSender mailSender;

    @Test
    void aFreeOwnerCannotInviteUntilTheAccountIsUpgraded() throws Exception {
        JsonNode owner = register("invite.owner", "invite.owner@example.com");
        JsonNode guest = register("invite.guest", "invite.guest@example.com");
        String ownerToken = owner.path("token").asString();

        Long connectionId = registerConnection(ownerToken, "gated_db");
        Long selectRole = permissionId(ownerToken, "SELECT_ROLE");
        String body = """
                {"toUserId":%d,"dbConnectionId":%d,"additionalRoleId":%d}"""
                .formatted(guest.path("userId").asLong(), connectionId, selectRole);

        mockMvc.perform(post("/api/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("premium feature")));

        mockMvc.perform(get("/api/invitations").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        upgradeToPremium(ownerToken, "invite.owner.connected@example.com");

        mockMvc.perform(post("/api/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void premiumWithoutOwnershipStillCannotShareSomebodyElsesDatabase() throws Exception {
        JsonNode owner = register("invite.real.owner", "invite.real.owner@example.com");
        JsonNode outsider = register("invite.outsider", "invite.outsider@example.com");
        JsonNode target = register("invite.target", "invite.target@example.com");

        Long connectionId = registerConnection(owner.path("token").asString(), "not_yours_db");
        String outsiderToken = outsider.path("token").asString();
        upgradeToPremium(outsiderToken, "invite.outsider.connected@example.com");

        mockMvc.perform(post("/api/invitations")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toUserId":%d,"dbConnectionId":%d,"additionalRoleId":%d}"""
                                .formatted(target.path("userId").asLong(), connectionId,
                                        permissionId(outsiderToken, "SELECT_ROLE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("Only the owner")));
    }

    private void upgradeToPremium(String token, String email) throws Exception {
        mockMvc.perform(post("/api/premium-users/request-code")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s"}""".formatted(email)))
                .andExpect(status().isOk());
        String code = mailSender.lastCodeFor(email)
                .orElseThrow(() -> new AssertionError("No code was sent to " + email));
        mockMvc.perform(post("/api/premium-users/confirm")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"connectedEmail":"%s","code":"%s"}""".formatted(email, code)))
                .andExpect(status().isOk());
    }

    private Long registerConnection(String token, String dbName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/db-connections")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"localhost","port":5432,"dbName":"%s","dbUsername":"app",
                                 "dbPassword":"secret","dbType":"POSTGRESQL"}""".formatted(dbName)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private Long permissionId(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/additional-roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode role : objectMapper.readTree(result.getResponse().getContentAsString())) {
            if (name.equals(role.path("name").asString())) {
                return role.path("id").asLong();
            }
        }
        throw new AssertionError("Permission " + name + " was not seeded");
    }

    private JsonNode register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","email":"%s","password":"password123"}"""
                                .formatted(username, email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("token").asString()).isNotBlank();
        return body;
    }
}
