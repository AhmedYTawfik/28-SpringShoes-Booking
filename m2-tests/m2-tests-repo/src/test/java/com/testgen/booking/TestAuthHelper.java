package com.testgen.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Admin-seed + login helpers, derived from the L0 scanner's manifest.
 *
 * <p>The M2 spec deliberately omits a standardized admin-seed mechanism
 * (per the {@code feedback_no_first_admin_bootstrap} decision). Students
 * may use Flyway, CommandLineRunner, or direct DB insert, so the tests
 * cannot rely on any student-shipped seed.
 *
 * <p>We also cannot POST {@code role=ADMIN} through the register endpoint
 * — every M2 theme rejects that payload (Freelance accepts
 * {@code CLIENT|FREELANCER} but not {@code ADMIN}; others ignore role and
 * pin to the theme default). Nor can we BCrypt the password in-test (no
 * BCrypt dep on the grader-controlled test classpath).
 *
 * <p>The workaround: register a normal user over HTTP (student's service
 * hashes the password), then promote to ADMIN via JDBC using the role
 * enum type discovered at runtime. Finally, log in to obtain the JWT.
 *
 * <h2>Talabat-specific constants in this golden</h2>
 * <ul>
 *   <li>Register path: {@code /api/auth/register}</li>
 *   <li>Login path: {@code /api/auth/login}</li>
 *   <li>User table: {@code users}</li>
 *   <li>Role column: {@code role}</li>
 *   <li>Admin label: {@code ADMIN}</li>
 * </ul>
 *
 * <p>The template derived from this golden replaces every one of these
 * constants with {@code {{auth.<field>}}} holes backed by the L0 manifest.
 */
public final class TestAuthHelper {

    /** Shared strong-looking password used for every seeded admin in a run. */
    public static final String ADMIN_PASSWORD = "TestAdmin!Pass2026";

    private static final ObjectMapper OM = new ObjectMapper();

    private TestAuthHelper() {}

    /**
     * Register a user via HTTP, promote to ADMIN via direct JDBC, return the new user id.
     *
     * @return the newly-created user's {@code id}
     * @throws AssertionError if register returns non-2xx, UPDATE affects zero rows,
     *         or the SELECT to fetch the id comes back empty.
     */
    public static long seedAdmin(HttpClient http, JdbcTemplate jdbc, String baseUrl, String email,
                                 String userTable, String roleCol, String emailCol) throws Exception {
        String phone = "+2010" + System.nanoTime();
        String body = String.format("""
                {"name":"TestAdmin","email":"%s","password":"%s","phone":"%s"}
                """, email, ADMIN_PASSWORD, phone.substring(0, 15));

        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            // /api/auth/register not public — insert user row directly via JDBC.
            // Pre-computed BCrypt($2b$10) of ADMIN_PASSWORD; compatible with Spring BCryptPasswordEncoder.
            final String BCRYPT_HASH = "$2b$10$xiDaMMZcqRGXlTWhwCIaPO1PhcsNSOsrGbA68Bxg9hgexEOOmUfR2";
            java.util.List<String> cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = ? AND column_name != 'id' ORDER BY ordinal_position",
                String.class, userTable);
            StringBuilder colSql = new StringBuilder();
            StringBuilder valSql = new StringBuilder();
            java.util.List<Object> params = new java.util.ArrayList<>();
            for (String c : cols) {
                Object val = null; String castType = null;
                switch (c.toLowerCase()) {
                    case "email"                             -> val = email;
                    case "password","password_hash","passwd" -> val = BCRYPT_HASH;
                    case "name","full_name","username"       -> val = "TestAdmin";
                    case "phone","phone_number","mobile"     -> val = phone.substring(0, Math.min(phone.length(), 15));
                    case "status","user_status"              -> { val = "ACTIVE"; castType = discoverEnumTypeForColumn(jdbc, userTable, c); }
                    case "role","user_role"                  -> { val = discoverDefaultRole(jdbc, userTable, c); castType = discoverEnumTypeForColumn(jdbc, userTable, c); }
                    case "created_at","createdat","registration_date","joined_at","updated_at","last_modified" -> val = new java.sql.Timestamp(System.currentTimeMillis());
                    default -> { /* skip — let PG use column defaults */ }
                }
                if (val != null) {
                    if (colSql.length() > 0) { colSql.append(", "); valSql.append(", "); }
                    colSql.append('"').append(c).append('"');
                    valSql.append(castType != null ? "CAST(? AS " + castType + ")" : "?");
                    params.add(val);
                }
            }
            jdbc.update("INSERT INTO \"" + userTable + "\" (" + colSql + ") VALUES (" + valSql + ")",
                params.toArray());
            // fall through — ADMIN promotion + SELECT id below complete the setup
        }

        // Promote to ADMIN via JDBC. The role column is a PG native enum,
        // whose type name varies per theme — discover it via pg_type. Table
        // and column names are resolved from the student's manifest.
        String roleEnumType = discoverEnumTypeForColumn(jdbc, userTable, roleCol);
        String updateSql;
        if (roleEnumType != null) {
            updateSql = "UPDATE \"" + userTable + "\" SET \"" + roleCol + "\" = CAST(? AS "
                      + roleEnumType + ") WHERE \"" + emailCol + "\" = ?";
        } else {
            updateSql = "UPDATE \"" + userTable + "\" SET \"" + roleCol + "\" = ? WHERE \""
                      + emailCol + "\" = ?";
        }
        int affected;
        try {
            affected = jdbc.update(updateSql, "ADMIN", email);
        } catch (DataAccessException e) {
            throw new AssertionError("seedAdmin: UPDATE " + userTable + "." + roleCol
                + " failed: " + e.getMessage(), e);
        }
        if (affected != 1) {
            throw new AssertionError("seedAdmin: UPDATE affected " + affected + " rows, expected 1");
        }

        Long id;
        try {
            id = jdbc.queryForObject("SELECT id FROM \"" + userTable + "\" WHERE \""
                + emailCol + "\" = ?", Long.class, email);
        } catch (DataAccessException e) {
            throw new AssertionError("seedAdmin: SELECT id failed: " + e.getMessage(), e);
        }
        if (id == null) {
            throw new AssertionError("seedAdmin: no user row found after register + promote");
        }
        return id;
    }

    /**
     * POST /api/auth/login for the seeded admin, return the JWT from the response.
     *
     * @return the {@code token} field value from the login response
     */
    public static String loginAsAdmin(HttpClient http, String baseUrl, String email) throws Exception {
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, ADMIN_PASSWORD);
        HttpResponse<String> r = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() / 100 != 2) {
            throw new AssertionError(
                    "loginAsAdmin: POST /api/auth/login returned " + r.statusCode()
                            + " body=" + r.body());
        }
        JsonNode j = OM.readTree(r.body());
        if (!j.has("token") || j.get("token").asText().isBlank()) {
            throw new AssertionError("loginAsAdmin: response missing or empty token: " + r.body());
        }
        return j.get("token").asText();
    }

    /** Build-once helper: one call to cover both seed and login. */
    public static String seedAndLoginAdmin(HttpClient http, JdbcTemplate jdbc, String baseUrl, String email,
                                           String userTable, String roleCol, String emailCol) throws Exception {
        seedAdmin(http, jdbc, baseUrl, email, userTable, roleCol, emailCol);
        return loginAsAdmin(http, baseUrl, email);
    }

    // ────────────────────────────────────────────────────────────────

    static String discoverEnumTypeForColumn(JdbcTemplate jdbc, String table, String col) {
        try {
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND column_name = ?",
                    String.class, table, col);
            if (udt == null) return null;
            if (udt.equals("varchar") || udt.equals("text") || udt.startsWith("int")) return null;
            return udt;
        } catch (DataAccessException e) {
            return null;
        }
    }

    /**
     * Discover a valid non-ADMIN role value for the given table/column.
     * Handles both PG native enums (queries pg_enum) and VARCHAR columns
     * with CHECK constraints (parses pg_get_constraintdef output).
     * Returns "USER" as a last-resort fallback.
     */
    static String discoverDefaultRole(JdbcTemplate jdbc, String table, String col) {
        try {
            // Case 1: PG native enum — pick first non-ADMIN label
            String enumType = discoverEnumTypeForColumn(jdbc, table, col);
            if (enumType != null) {
                java.util.List<String> vals = jdbc.queryForList(
                    "SELECT enumlabel FROM pg_enum e JOIN pg_type t ON e.enumtypid = t.oid " +
                    "WHERE t.typname = ? AND enumlabel != 'ADMIN' ORDER BY enumsortorder",
                    String.class, enumType);
                if (!vals.isEmpty()) return vals.get(0);
            }
            // Case 2: VARCHAR/text with CHECK constraint — parse allowed values
            java.util.List<String> defs = jdbc.queryForList(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
                "WHERE conrelid = ?::regclass AND contype = 'c'",
                String.class, table);
            for (String def : defs) {
                if (!def.contains(col)) continue;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(def);
                while (m.find()) {
                    String v = m.group(1);
                    if (!v.equals("ADMIN")) return v;
                }
            }
        } catch (Exception ignored) {}
        return "USER";
    }
}
