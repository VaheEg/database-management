package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.config.QueryProperties;
import org.example.dto.ColumnSchemaResponse;
import org.example.dto.DbConnectionDetailsRequest;
import org.example.dto.QueryRequest;
import org.example.dto.QueryResponse;
import org.example.dto.ScriptResponse;
import org.example.dto.TableSchemaResponse;
import org.example.entity.AdditionalRoleName;
import org.example.entity.DbConnectionDetails;
import org.example.exception.AccessDeniedException;
import org.example.exception.BadRequestException;
import org.example.exception.ExternalDatabaseException;
import org.example.exception.NotFoundException;
import org.example.repository.DbConnectionDetailsRepository;
import org.example.security.CryptoService;
import org.example.security.CurrentUserService;
import org.springframework.stereotype.Service;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Runs SQL against a registered database: work out what each statement needs, check the caller holds
 * it on that one database, then open a JDBC connection with the decrypted credentials. A SELECT,
 * INSERT, UPDATE or DELETE needs the matching shared permission; anything else (CREATE TABLE, ALTER,
 * DROP, ...) only the owner may run. A script runs in one transaction and every statement is checked
 * before the first one runs.
 *
 * <p>Deliberately not {@code @Transactional}: an external query can run for the whole statement
 * timeout and holding an application-database transaction open for it buys nothing. Each audit row
 * therefore commits on its own, which is safe here because the connection row it references was
 * committed long ago — unlike the just-inserted rows {@link AuditLogService#record} warns about.
 * It is also what lets a failed attempt be audited at all.
 */
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

    private static final Pattern HOST = Pattern.compile(DbConnectionDetailsRequest.HOST_PATTERN);
    private static final Pattern DB_NAME = Pattern.compile(DbConnectionDetailsRequest.DB_NAME_PATTERN);
    private static final int MAX_TEXT_LENGTH = 10_000;

    private final DbConnectionDetailsRepository dbConnectionDetailsRepository;
    private final InvitationService invitationService;
    private final AuditLogService auditLogService;
    private final CurrentUserService currentUserService;
    private final CryptoService cryptoService;
    private final QueryProperties queryProperties;

    public QueryResponse execute(Long dbConnectionId, QueryRequest request) {
        return run(dbConnectionId, request, false).getResults().getFirst();
    }

    public ScriptResponse executeScript(Long dbConnectionId, QueryRequest request) {
        return run(dbConnectionId, request, true);
    }

    private ScriptResponse run(Long dbConnectionId, QueryRequest request, boolean script) {
        Long callerId = currentUserService.requireId();
        DbConnectionDetails details = findOrThrow(dbConnectionId);
        int maxRows = request.getMaxRows() == null
                ? queryProperties.getMaxRows()
                : Math.min(request.getMaxRows(), queryProperties.getMaxRows());

        try {
            List<SqlStatementGuard.Inspection> inspections = script
                    ? SqlStatementGuard.inspectScript(request.getSql(), details.getDbType())
                    : List.of(SqlStatementGuard.inspect(request.getSql(), details.getDbType()));
            for (SqlStatementGuard.Inspection inspection : inspections) {
                authorize(callerId, dbConnectionId, inspection);
            }

            ScriptResponse response = runStatements(details, inspections, maxRows);
            for (int i = 0; i < inspections.size(); i++) {
                QueryResponse result = response.getResults().get(i);
                String outcome = result.getUpdateCount() != null
                        ? result.getUpdateCount() + " row(s) affected"
                        : result.getRows().size() + " row(s) returned";
                auditLogService.record(callerId, dbConnectionId, "QUERY_" + result.getStatementType(),
                        outcome + ": " + inspections.get(i).statement(), true);
            }
            return response;
        } catch (BadRequestException | AccessDeniedException | ExternalDatabaseException ex) {
            auditLogService.record(callerId, dbConnectionId, "QUERY_FAILED",
                    ex.getMessage() + " | " + request.getSql(), false);
            throw ex;
        }
    }

    private void authorize(Long callerId, Long dbConnectionId, SqlStatementGuard.Inspection inspection) {
        if (!inspection.ownerOnly()) {
            inspection.permissions().forEach(permission ->
                    invitationService.requirePermission(callerId, dbConnectionId, permission));
            return;
        }
        if (dbConnectionDetailsRepository.findByIdAndOwnerUserId(dbConnectionId, callerId).isEmpty()) {
            throw new AccessDeniedException("Only the owner of database connection " + dbConnectionId
                    + " can run " + inspection.statementType() + " statements — shared access covers "
                    + "single SELECT, INSERT, UPDATE and DELETE statements");
        }
    }

    public List<TableSchemaResponse> schema(Long dbConnectionId) {
        Long callerId = currentUserService.requireId();
        DbConnectionDetails details = findOrThrow(dbConnectionId);
        invitationService.requirePermission(callerId, dbConnectionId, AdditionalRoleName.SELECT_ROLE);

        Connection jdbc = open(details);
        try (jdbc) {
            DatabaseMetaData meta = jdbc.getMetaData();
            String catalog = jdbc.getCatalog();
            String schema = jdbc.getSchema();

            List<TableSchemaResponse> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(TableSchemaResponse.builder()
                            .name(rs.getString("TABLE_NAME"))
                            .type(rs.getString("TABLE_TYPE"))
                            .build());
                }
            }
            for (TableSchemaResponse table : tables) {
                table.setColumns(readColumns(meta, catalog, schema, table.getName()));
            }
            return tables;
        } catch (SQLException ex) {
            throw new ExternalDatabaseException("Could not read the schema of database connection "
                    + dbConnectionId + ": " + ex.getMessage());
        }
    }

    private ScriptResponse runStatements(DbConnectionDetails details,
                                         List<SqlStatementGuard.Inspection> inspections, int maxRows) {
        long started = System.nanoTime();
        Connection jdbc = open(details);
        try (jdbc; Statement statement = jdbc.createStatement()) {
            // pgjdbc only enforces read-only inside an explicit transaction.
            jdbc.setAutoCommit(false);
            jdbc.setReadOnly(inspections.stream().allMatch(SqlStatementGuard.Inspection::readOnly));
            statement.setQueryTimeout(Math.toIntExact(queryProperties.getStatementTimeout().toSeconds()));
            statement.setMaxRows(maxRows + 1);

            List<QueryResponse> results = new ArrayList<>();
            for (int i = 0; i < inspections.size(); i++) {
                SqlStatementGuard.Inspection inspection = inspections.get(i);
                try {
                    results.add(runOne(statement, inspection, maxRows));
                } catch (SQLException ex) {
                    rollbackQuietly(jdbc, ex);
                    String which = inspections.size() > 1
                            ? "statement " + (i + 1) + " of " + inspections.size() + " (" + inspection.statementType()
                            + ") — nothing was saved"
                            : "the statement";
                    throw new BadRequestException("The database rejected " + which + ": " + ex.getMessage());
                }
            }
            jdbc.commit();
            return ScriptResponse.builder()
                    .results(results)
                    .durationMs((System.nanoTime() - started) / 1_000_000)
                    .build();
        } catch (SQLException ex) {
            throw new BadRequestException("The database rejected the statement: " + ex.getMessage());
        }
    }

    private static QueryResponse runOne(Statement statement, SqlStatementGuard.Inspection inspection, int maxRows)
            throws SQLException {
        long started = System.nanoTime();
        QueryResponse.QueryResponseBuilder response = QueryResponse.builder()
                .statementType(inspection.statementType())
                .permissionsUsed(inspection.permissions());
        if (statement.execute(inspection.statement())) {
            try (ResultSet rs = statement.getResultSet()) {
                readRows(rs, maxRows, response);
            }
        } else {
            // DDL reports -1 on some drivers; "nothing counted" reads better as 0.
            response.columns(List.of()).rows(List.of()).updateCount(Math.max(0, statement.getUpdateCount()));
        }
        return response.durationMs((System.nanoTime() - started) / 1_000_000).build();
    }

    private static void readRows(ResultSet rs, int maxRows, QueryResponse.QueryResponseBuilder response)
            throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        List<String> columns = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<List<Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() == maxRows) {
                truncated = true;
                break;
            }
            List<Object> row = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                row.add(toJsonValue(rs.getObject(i)));
            }
            rows.add(row);
        }
        response.columns(columns).rows(rows).truncated(truncated);
    }

    private static List<ColumnSchemaResponse> readColumns(DatabaseMetaData meta, String catalog, String schema,
                                                          String table) throws SQLException {
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        Map<String, String[]> foreignKeys = new HashMap<>();
        try (ResultSet rs = meta.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                foreignKeys.putIfAbsent(rs.getString("FKCOLUMN_NAME"),
                        new String[]{rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME")});
            }
        }

        List<ColumnSchemaResponse> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                String[] reference = foreignKeys.get(name);
                columns.add(ColumnSchemaResponse.builder()
                        .name(name)
                        .type(rs.getString("TYPE_NAME"))
                        .nullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls)
                        .primaryKey(primaryKeys.contains(name))
                        .referencedTable(reference == null ? null : reference[0])
                        .referencedColumn(reference == null ? null : reference[1])
                        .build());
            }
        }
        return columns;
    }

    private Connection open(DbConnectionDetails details) {
        String url = jdbcUrl(details);
        int timeoutSeconds = Math.toIntExact(queryProperties.getConnectTimeout().toSeconds());

        Properties props = new Properties();
        props.setProperty("user", details.getDbUsername());
        props.setProperty("password", cryptoService.decrypt(details.getDbPassword()));
        switch (details.getDbType()) {
            case POSTGRESQL -> {
                props.setProperty("connectTimeout", String.valueOf(timeoutSeconds));
                props.setProperty("loginTimeout", String.valueOf(timeoutSeconds));
            }
            case MYSQL, MARIADB -> {
                props.setProperty("connectTimeout", String.valueOf(timeoutSeconds * 1000L));
                props.setProperty("allowMultiQueries", "false");
                props.setProperty("allowLoadLocalInfile", "false");
            }
            case SQLSERVER -> props.setProperty("loginTimeout", String.valueOf(timeoutSeconds));
            default -> {
            }
        }

        try {
            return DriverManager.getConnection(url, props);
        } catch (SQLException ex) {
            throw new ExternalDatabaseException("Could not connect to " + details.getDbType() + " "
                    + details.getHost() + ":" + details.getPort() + "/" + details.getDbName() + ": " + ex.getMessage());
        }
    }

    private static String jdbcUrl(DbConnectionDetails details) {
        // Rows registered before the request patterns existed are checked again here.
        if (!HOST.matcher(details.getHost()).matches() || !DB_NAME.matcher(details.getDbName()).matches()) {
            throw new BadRequestException("This connection's host or database name contains characters "
                    + "that cannot go into a JDBC URL — edit the connection first");
        }
        String host = details.getHost();
        int port = details.getPort();
        String db = details.getDbName();
        return switch (details.getDbType()) {
            case POSTGRESQL -> "jdbc:postgresql://" + host + ":" + port + "/" + db;
            // MariaDB servers speak the MySQL protocol, so the bundled MySQL driver serves both.
            case MYSQL, MARIADB -> "jdbc:mysql://" + host + ":" + port + "/" + db;
            // Local SQL Server installs ship a self-signed certificate, hence trustServerCertificate.
            case SQLSERVER -> "jdbc:sqlserver://" + host + ":" + port + ";databaseName=" + db
                    + ";encrypt=true;trustServerCertificate=true";
            case H2 -> "jdbc:h2:tcp://" + host + ":" + port + "/" + db;
            case ORACLE -> throw new BadRequestException(
                    "ORACLE databases can be registered but not queried yet — no Oracle JDBC driver is bundled");
        };
    }

    private static Object toJsonValue(Object value) throws SQLException {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            return "0x" + HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, MAX_TEXT_LENGTH / 2));
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), MAX_TEXT_LENGTH));
        }
        if (value instanceof Blob blob) {
            return "<" + blob.length() + " bytes>";
        }
        return value.toString();
    }

    private static void rollbackQuietly(Connection jdbc, SQLException cause) {
        try {
            jdbc.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    private DbConnectionDetails findOrThrow(Long dbConnectionId) {
        return dbConnectionDetailsRepository.findById(dbConnectionId)
                .orElseThrow(() -> NotFoundException.of("Database connection", dbConnectionId));
    }
}
