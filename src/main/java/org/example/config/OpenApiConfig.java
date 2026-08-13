package org.example.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Describes the API for Swagger UI, served at {@code /swagger-ui.html}.
 *
 * <p>The important part is the {@code bearerAuth} scheme: it puts an
 * <b>Authorize</b> button in the UI, and because it is also declared as a
 * top-level requirement, every operation sends the token you paste there as
 * {@code Authorization: Bearer <token>}. Register or log in first, copy the
 * {@code token} out of the response, and paste it — the raw token, without the
 * word "Bearer", which Swagger adds itself.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI dbManagerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DB Manager API")
                        .version("v1")
                        .description("""
                                Backend for DB Manager: users register their own external databases, \
                                share them with other users under a database-scoped permission, and \
                                every meaningful action is written to an audit log.

                                **Getting a token**
                                1. `POST /api/auth/register` (or `/api/auth/login` if you already have an account)
                                2. Copy `token` from the response
                                3. Click **Authorize**, paste the token, and every request below is authenticated
                                4. `POST /api/auth/logout` revokes that token — reusing it afterwards returns 401
                                """)
                        .contact(new Contact().name("DB Manager")))
                .servers(List.of(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the raw JWT from /api/auth/login — Swagger prefixes 'Bearer ' for you.")))
                // Applies to every operation; register and login also work without it.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
