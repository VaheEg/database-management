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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class InvitationSharingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LoggingVerificationMailSender mailSender;

    @Test
    void anOwnerSharesByUsernameAndTheGuestGetsExactlyWhatWasAccepted() throws Exception {
        String ownerToken = register("share.owner");
        String guestToken = register("share.guest");
        upgradeToPremium(ownerToken, "share.owner.connected@example.com");
        Long connectionId = registerConnection(ownerToken, "shared_db");

        Long selectInvite = idOf(invite(ownerToken, "share.guest", connectionId, permissionId(ownerToken, "SELECT_ROLE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.fromUsername").value("share.owner"))
                .andExpect(jsonPath("$.toUsername").value("share.guest"))
                .andExpect(jsonPath("$.permission").value("SELECT_ROLE"))
                .andExpect(jsonPath("$.dbName").value("shared_db"))
                .andExpect(jsonPath("$.dbType").value("POSTGRESQL")));
        Long insertInvite = idOf(invite(ownerToken, "share.guest", connectionId, permissionId(ownerToken, "INSERT_ROLE"))
                .andExpect(status().isCreated()));

        mockMvc.perform(get("/api/invitations").header("Authorization", "Bearer " + guestToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(0));

        answer(guestToken, selectInvite, "ACCEPTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        answer(guestToken, insertInvite, "DECLINED")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].dbName").value("shared_db"));

        answer(guestToken, selectInvite, "REVOKED")
                .andExpect(status().isForbidden());
        answer(ownerToken, selectInvite, "REVOKED")
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/db-connections").header("Authorization", "Bearer " + guestToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theRecipientHasToBeSomebodyElseWhoExists() throws Exception {
        String ownerToken = register("share.lonely.owner");
        upgradeToPremium(ownerToken, "share.lonely.owner.connected@example.com");
        Long connectionId = registerConnection(ownerToken, "lonely_db");
        Long select = permissionId(ownerToken, "SELECT_ROLE");

        invite(ownerToken, "share.nobody.at.all", connectionId, select)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("no user called 'share.nobody.at.all'")));
        invite(ownerToken, "share.lonely.owner", connectionId, select)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("nothing to grant yourself")));
        mockMvc.perform(post("/api/invitations")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dbConnectionId":%d,"additionalRoleId":%d}""".formatted(connectionId, select)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("toUsername or toUserId")));
    }

    private ResultActions invite(String token, String username, Long connectionId, Long permissionId)
            throws Exception {
        return mockMvc.perform(post("/api/invitations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"toUsername":"%s","dbConnectionId":%d,"additionalRoleId":%d}"""
                        .formatted(username, connectionId, permissionId)));
    }

    private ResultActions answer(String token, Long invitationId, String status) throws Exception {
        return mockMvc.perform(put("/api/invitations/" + invitationId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status":"%s"}""".formatted(status)));
    }

    private Long idOf(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString()).path("id").asLong();
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

    /** Registers without an email: an account needs one only to become premium. */
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
