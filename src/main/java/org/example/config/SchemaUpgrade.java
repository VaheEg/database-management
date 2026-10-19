package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * The schema changes {@code ddl-auto=update} cannot make by itself. Hibernate adds tables and columns
 * but never relaxes a constraint, so a database created by an older build keeps it. Each step reads the
 * catalog first and does nothing on a database that is already up to date.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaUpgrade implements CommandLineRunner {

    private final DataSource dataSource;

    @Override
    public void run(String @NonNull ... args) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            // Registering no longer asks for an email: only the premium upgrade needs one
            dropNotNull(connection, "users", "email");
            // A deleted account leaves its audit entries behind, without a user
            dropNotNull(connection, "audit_logs", "user_id");
        }
    }

    static void dropNotNull(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        boolean upper = meta.storesUpperCaseIdentifiers();
        try (ResultSet rs = meta.getColumns(connection.getCatalog(), connection.getSchema(),
                upper ? table.toUpperCase(Locale.ROOT) : table,
                upper ? column.toUpperCase(Locale.ROOT) : column)) {
            if (!rs.next() || rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) {
                return;
            }
        }

        String sql = "ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            log.info("Schema upgraded: {}.{} now accepts NULL", table, column);
        } catch (SQLException ex) {
            log.warn("Could not make {}.{} accept NULL ({}). Until it does, inserts without it fail — "
                    + "run the equivalent of: {}", table, column, ex.getMessage(), sql);
        }
    }
}
