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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AccountDeletionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingVerificationMailSender mailSender;

    @Test
    void aDeletedAccountTakesItsDatabasesAndSharesWithItAndFreesItsName() throws Exception {
        JsonNode owner = register("delete.me", "delete.me@example.com");
        JsonNode guest = register("delete.guest", null);
        JsonNode other = register("delete.other", null);
        String ownerToken = owner.path("token").asString();
        String guestToken = guest.path("token").asString();
        long ownerId = owner.path("userId").asLong();

        upgradeToPremium(ownerToken, "delete.me@example.com");
        Long connectionId = registerConnection(ownerToken, "doomed_db");
        share(ownerToken, "delete.guest", connectionId, guestToken);
        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(1));

        deleteAccount(other.path("token").asString(), ownerId, "password123")
                .andExpect(status().isForbidden());
        deleteAccount(ownerToken, ownerId, "not-my-password")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(containsString("nothing was deleted")));
        deleteAccount(ownerToken, ownerId, "password123")
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"delete.me","password":"password123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        // The guest's access went with the database and its stored password
        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/invitations").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(0));
        // ...while the guest's own history is untouched
        mockMvc.perform(get("/api/audit-logs").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$[?(@.action == 'INVITATION_ACCEPTED')]").exists());

        JsonNode again = register("delete.me", "delete.me@example.com");
        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + again.path("token").asString()))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/premium-users").header("Authorization", "Bearer " + again.path("token").asString()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions deleteAccount(String token, long userId, String password) throws Exception {
        return mockMvc.perform(delete("/api/users/" + userId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password":"%s"}""".formatted(password)));
    }

    private void share(String ownerToken, String guestUsername, Long connectionId, String guestToken)
            throws Exception {
        MvcResult sent = mockMvc.perform(post("/api/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"toUsername":"%s","dbConnectionId":%d,"additionalRoleId":%d}"""
                                .formatted(guestUsername, connectionId, permissionId(ownerToken, "SELECT_ROLE"))))
                .andExpect(status().isCreated())
                .andReturn();
        long invitationId = objectMapper.readTree(sent.getResponse().getContentAsString()).path("id").asLong();
        mockMvc.perform(put("/api/invitations/" + invitationId)
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACCEPTED"}"""))
                .andExpect(status().isOk());
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
        String body = email == null
                ? """
                  {"username":"%s","password":"password123"}""".formatted(username)
                : """
                  {"username":"%s","email":"%s","password":"password123"}""".formatted(username, email);
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
