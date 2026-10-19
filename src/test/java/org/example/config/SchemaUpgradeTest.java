package org.example.config;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThatCode;

/** On its own in-memory database: the application's shared one already has the current schema. */
class SchemaUpgradeTest {

    @Test
    void aUsersTableFromAnOlderBuildAcceptsAccountsWithoutAnEmail() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:schema_upgrade_test");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE)");

            SchemaUpgrade.dropNotNull(connection, "users", "email");
            SchemaUpgrade.dropNotNull(connection, "users", "email");  // a second start changes nothing

            assertThatCode(() -> statement.execute("INSERT INTO users (id, email) VALUES (1, NULL), (2, NULL)"))
                    .doesNotThrowAnyException();
        }
    }
}
