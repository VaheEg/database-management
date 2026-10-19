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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nothing listens on port 1, so every connection attempt here fails fast with "refused". */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class DbConnectionManagementIntegrationTest {

    private static final String UNREACHABLE = """
            {"host":"127.0.0.1","port":1,"dbName":"%s","dbUsername":"app","dbPassword":"secret","dbType":"POSTGRESQL"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingVerificationMailSender mailSender;

    @Test
    void settingsCanBeTestedBeforeTheyAreSaved() throws Exception {
        String token = register("manage.tester");

        mockMvc.perform(post("/api/db-connections/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UNREACHABLE.formatted("typo_db")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("Could not connect to POSTGRESQL 127.0.0.1:1/typo_db")));

        mockMvc.perform(post("/api/db-connections/test")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"127.0.0.1","port":1,"dbName":"x","dbUsername":"app","dbType":"POSTGRESQL"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("dbPassword is required")));

        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void onlyTheOwnerCanTestASavedDatabaseWithItsStoredPassword() throws Exception {
        String ownerToken = register("manage.test.owner");
        String guestToken = register("manage.test.guest");
        upgradeToPremium(ownerToken, "manage.test.owner.connected@example.com");
        Long connectionId = registerConnection(ownerToken, "saved_db");
        share(ownerToken, "manage.test.guest", connectionId, guestToken);

        mockMvc.perform(post("/api/db-connections/" + connectionId + "/test")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("127.0.0.1:1/saved_db")));
        // An edit is tested before it is saved: the stored password, the new database name
        mockMvc.perform(post("/api/db-connections/" + connectionId + "/test")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"127.0.0.1","port":1,"dbName":"renamed_db","dbUsername":"app","dbType":"POSTGRESQL"}"""))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(containsString("127.0.0.1:1/renamed_db")));

        mockMvc.perform(post("/api/db-connections/" + connectionId + "/test")
                        .header("Authorization", "Bearer " + guestToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"attacker.example","port":5432,"dbName":"x","dbUsername":"app","dbType":"POSTGRESQL"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingADatabaseTakesItsSharesWithItAndLeavesTheOthers() throws Exception {
        String ownerToken = register("manage.delete.owner");
        String guestToken = register("manage.delete.guest");
        upgradeToPremium(ownerToken, "manage.delete.owner.connected@example.com");
        Long keep = registerConnection(ownerToken, "keep_db");
        Long drop = registerConnection(ownerToken, "drop_db");
        share(ownerToken, "manage.delete.guest", drop, guestToken);

        mockMvc.perform(delete("/api/db-connections/" + drop).header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/db-connections/" + drop).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + ownerToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(keep));
        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/invitations").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/db-connections/" + drop).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    /** Offers SELECT on the database and has the guest accept it. */
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
                        .content(UNREACHABLE.formatted(dbName)))
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

    private String register(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"password123"}""".formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asString();
    }
}
