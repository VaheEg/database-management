package org.example.service;

import org.example.entity.AdditionalRoleName;
import org.example.entity.DbType;
import org.example.exception.BadRequestException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Splits SQL into statements and decides what each one needs: a SELECT, INSERT, UPDATE or DELETE
 * maps to the database-scoped permissions a shared user can hold; anything else (CREATE TABLE,
 * ALTER, DROP, a data-modifying CTE, SELECT ... INTO, ...) is reserved for the database's owner.
 *
 * <p>This is a small lexer, not a parser. It skips string literals, quoted identifiers and comments
 * the way the target dialect does, so a keyword or a {@code ;} hidden inside one of them is not
 * mistaken for SQL. Where dialects disagree it errs towards rejecting, never towards seeing fewer
 * statements than the driver will run, because a shared user's permission is only as good as the
 * statement boundaries found here.
 */
public final class SqlStatementGuard {

    private static final Set<String> DATA_CHANGING = Set.of("INSERT", "UPDATE", "DELETE", "MERGE");
    private static final Set<String> LOCKING_PREFIXES = Set.of("FOR", "KEY");
    private static final int MAX_STATEMENTS = 1000;

    private SqlStatementGuard() {
    }

    /**
     * @param statement     the SQL to send, without a trailing semicolon
     * @param statementType the leading verb (SELECT, INSERT, CREATE, ...), after any WITH clause
     * @param permissions   every permission a shared user must hold (an upsert needs INSERT and UPDATE);
     *                      empty when the statement is owner-only
     * @param ownerOnly     true when no shared permission covers the statement
     */
    public record Inspection(String statement, String statementType, Set<AdditionalRoleName> permissions,
                             boolean ownerOnly) {

        /** SELECTs run in a read-only transaction, so a side-effecting function cannot write either. */
        public boolean readOnly() {
            return !ownerOnly && statementType.equals(AdditionalRoleName.SELECT_ROLE.sqlVerb());
        }
    }

    /** Exactly one statement; a trailing semicolon is allowed. */
    public static Inspection inspect(String sql, DbType dbType) {
        Inspection inspection = inspectOne(sql, dbType, false);
        if (inspection == null) {
            throw new BadRequestException("The statement is empty");
        }
        return inspection;
    }

    /** Any number of statements separated by semicolons, in order. */
    public static List<Inspection> inspectScript(String sql, DbType dbType) {
        if (sql == null || sql.isBlank()) {
            throw new BadRequestException("The script is empty");
        }
        Scanner scanner = new Scanner(sql, dbType, true);
        scanner.scan();

        List<Inspection> inspections = new ArrayList<>();
        int start = 0;
        List<Integer> ends = new ArrayList<>(scanner.boundaries);
        ends.add(sql.length());
        for (int end : ends) {
            Inspection inspection = inspectOne(sql.substring(start, end), dbType, true);
            if (inspection != null) {
                inspections.add(inspection);
            }
            start = end + 1;
        }
        if (inspections.isEmpty()) {
            throw new BadRequestException("The script is empty");
        }
        if (inspections.size() > MAX_STATEMENTS) {
            throw new BadRequestException("A script may hold at most " + MAX_STATEMENTS + " statements");
        }
        return inspections;
    }

    /** Null when the text holds only whitespace and comments. */
    private static Inspection inspectOne(String sql, DbType dbType, boolean alreadySplit) {
        if (sql == null || sql.isBlank()) {
            return null;
        }
        Scanner scanner = new Scanner(sql, dbType, false);
        scanner.scan();
        List<Word> words = scanner.words;
        if (words.isEmpty()) {
            return null;
        }
        if (alreadySplit && scanner.statementEnd >= 0) {
            throw new IllegalStateException("A script segment still contains a statement separator");
        }

        int verbIndex = 0;
        if (words.getFirst().text().equals("WITH")) {
            verbIndex = -1;
            for (int i = 1; i < words.size(); i++) {
                Word word = words.get(i);
                if (word.depth() == 0 && AdditionalRoleName.forSqlVerb(word.text()).isPresent()) {
                    verbIndex = i;
                    break;
                }
            }
        }
        String statementType = verbIndex >= 0 ? words.get(verbIndex).text() : "WITH";
        Optional<AdditionalRoleName> verb = AdditionalRoleName.forSqlVerb(statementType);
        if (verb.isEmpty()) {
            return new Inspection(scanner.statement(), statementType, Set.of(), true);
        }

        Set<AdditionalRoleName> permissions = EnumSet.of(verb.get());
        for (int i = 0; i < words.size(); i++) {
            if (i == verbIndex) {
                continue;
            }
            Word word = words.get(i);
            String text = word.text();
            if (text.equals("UPDATE") && i > 0 && LOCKING_PREFIXES.contains(words.get(i - 1).text())) {
                continue; // SELECT ... FOR UPDATE / FOR NO KEY UPDATE only locks rows
            }
            if (text.equals("UPDATE") && verb.get() == AdditionalRoleName.INSERT_ROLE && word.depth() == 0) {
                permissions.add(AdditionalRoleName.UPDATE_ROLE); // ON CONFLICT DO UPDATE / ON DUPLICATE KEY UPDATE
                continue;
            }
            // A data-modifying CTE, or SELECT ... INTO (creates a table or a file): no single
            // shared permission describes these, so only the owner may run them.
            if (DATA_CHANGING.contains(text)
                    || (text.equals("INTO") && verb.get() == AdditionalRoleName.SELECT_ROLE)) {
                return new Inspection(scanner.statement(), statementType, Set.of(), true);
            }
        }
        return new Inspection(scanner.statement(), statementType, permissions, false);
    }

    private record Word(String text, int depth) {
    }

    private static final class Scanner {

        private final String sql;
        private final DbType dbType;
        private final boolean mySqlFamily;
        private final boolean script;
        private final List<Word> words = new ArrayList<>();
        private final List<Integer> boundaries = new ArrayList<>();
        private int pos;
        private int depth;
        private int statementEnd = -1;

        Scanner(String sql, DbType dbType, boolean script) {
            this.sql = sql;
            this.dbType = dbType;
            this.mySqlFamily = dbType == DbType.MYSQL || dbType == DbType.MARIADB;
            this.script = script;
        }

        String statement() {
            return (statementEnd < 0 ? sql : sql.substring(0, statementEnd)).strip();
        }

        void scan() {
            while (pos < sql.length()) {
                char c = sql.charAt(pos);
                if (startsLineComment()) {
                    skipLineComment();
                    continue;
                }
                if (c == '/' && peek(1) == '*') {
                    skipBlockComment();
                    continue;
                }
                if (statementEnd >= 0) {
                    if (!Character.isWhitespace(c)) {
                        throw new BadRequestException("Run one statement at a time — there is more SQL after ';'");
                    }
                    pos++;
                    continue;
                }
                if (c == ';') {
                    if (script) {
                        boundaries.add(pos++);
                        depth = 0;
                    } else {
                        statementEnd = pos++;
                    }
                } else if (c == '\'') {
                    skipQuoted('\'', mySqlFamily);
                } else if (c == '"') {
                    skipQuoted('"', mySqlFamily);
                } else if (c == '`' && mySqlFamily) {
                    skipQuoted('`', false);
                } else if (c == '[' && dbType == DbType.SQLSERVER) {
                    skipQuoted(']', false);
                } else if (c == '$' && (dbType == DbType.POSTGRESQL || dbType == DbType.H2)) {
                    skipDollarQuoted();
                } else if (c == '(') {
                    depth++;
                    pos++;
                } else if (c == ')') {
                    depth--;
                    pos++;
                } else if (Character.isLetter(c) || c == '_') {
                    readWord();
                } else if (Character.isDigit(c)) {
                    while (pos < sql.length() && (Character.isLetterOrDigit(sql.charAt(pos)) || sql.charAt(pos) == '.'
                            || sql.charAt(pos) == '_')) {
                        pos++;
                    }
                } else {
                    pos++;
                }
            }
        }

        private void readWord() {
            int start = pos;
            while (pos < sql.length()
                    && (Character.isLetterOrDigit(sql.charAt(pos)) || sql.charAt(pos) == '_' || sql.charAt(pos) == '$')) {
                pos++;
            }
            String word = sql.substring(start, pos);
            // PostgreSQL E'...' strings honour backslash escapes; plain ones do not.
            if (dbType == DbType.POSTGRESQL && word.equalsIgnoreCase("E") && peek(0) == '\'') {
                skipQuoted('\'', true);
                return;
            }
            words.add(new Word(word.toUpperCase(Locale.ROOT), depth));
        }

        private boolean startsLineComment() {
            char c = sql.charAt(pos);
            if (c == '-' && peek(1) == '-') {
                // MySQL only starts a comment at "-- " — "1--1" is arithmetic there.
                char after = peek(2);
                return !mySqlFamily || after == 0 || Character.isWhitespace(after) || Character.isISOControl(after);
            }
            return (c == '#' && mySqlFamily) || (c == '/' && peek(1) == '/' && dbType == DbType.H2);
        }

        private void skipLineComment() {
            while (pos < sql.length() && sql.charAt(pos) != '\n' && sql.charAt(pos) != '\r') {
                pos++;
            }
        }

        private void skipBlockComment() {
            int close = sql.indexOf("*/", pos + 2);
            if (close < 0) {
                throw new BadRequestException("Unterminated /* comment");
            }
            String body = sql.substring(pos + 2, close);
            // PostgreSQL and SQL Server nest comments, MySQL does not — refuse rather than guess.
            if (body.contains("/*")) {
                throw new BadRequestException("Nested /* comments are not supported");
            }
            if (mySqlFamily && (body.startsWith("!") || body.startsWith("M!"))) {
                throw new BadRequestException("MySQL executable comments (/*! ... */) are not allowed");
            }
            pos = close + 2;
        }

        private void skipQuoted(char close, boolean backslashEscapes) {
            int i = pos + 1;
            while (i < sql.length()) {
                char c = sql.charAt(i);
                if (backslashEscapes && c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == close) {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == close) {
                        i += 2;
                        continue;
                    }
                    pos = i + 1;
                    return;
                }
                i++;
            }
            throw new BadRequestException("Unterminated quoted string or identifier");
        }

        private void skipDollarQuoted() {
            int i = pos + 1;
            // H2 only knows $$...$$; PostgreSQL also allows a tag: $body$...$body$.
            if (dbType == DbType.POSTGRESQL && i < sql.length()
                    && (Character.isLetter(sql.charAt(i)) || sql.charAt(i) == '_')) {
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_')) {
                    i++;
                }
            }
            if (i >= sql.length() || sql.charAt(i) != '$') {
                pos++; // a lone $ or a $1 parameter
                return;
            }
            String tag = sql.substring(pos, i + 1);
            int close = sql.indexOf(tag, i + 1);
            if (close < 0) {
                throw new BadRequestException("Unterminated " + tag + " quoted string");
            }
            pos = close + tag.length();
        }

        private char peek(int offset) {
            int i = pos + offset;
            return i < sql.length() ? sql.charAt(i) : 0;
        }
    }
}
