package com.testgen.booking;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared base class for every generated test class.
 *
 * <p>Owns the JdbcTemplate, HttpClient, ObjectMapper, BASE_URL and common SQL /
 * HTTP helpers. Each test class in the factory's output extends this to get a
 * consistent setup without re-initializing these in every file.
 *
 * <p>This class has no {@code @Test} methods, so Surefire's
 * {@code **<!---->/*Tests.java} include filter (it ends in {@code TestBase}, not
 * {@code Tests}) will skip it as a runnable class. The {@code @TestInstance} +
 * {@code @BeforeAll} shape is inherited by subclasses.
 *
 * <p>The "package com.testgen.talabat" header is rewritten by the grader's
 * test_deployer to match the student's detected package at deploy time.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(30)
@SuppressWarnings("all")
public abstract class TestBase {

    protected static volatile JdbcTemplate jdbc;
    private static final Object _JDBC_INIT_LOCK = new Object();
    protected static volatile HttpClient http;
    private static final Object _HTTP_INIT_LOCK = new Object();
    protected String BASE_URL;

    // Per-service URLs — populated from env vars in initBase(). Each test
    // method sets BASE_URL to one of these on its first line, picked per
    // the section comment above the test class's @Tag.
    protected String userServiceUrl;
    protected String catalogServiceUrl;
    protected String orderServiceUrl;
    protected String deliveryServiceUrl;
    protected String checkoutServiceUrl;
    protected final ObjectMapper OM = new ObjectMapper();

    /**
     * Direct MongoDB access for tests that need to verify documents the
     * student's app wrote (e.g. activity events, dashboard logs). Lazily
     * initialized in {@link #initBase()}; null if Mongo is unreachable
     * (Mongo is a "soft" dependency in the M2 spec).
     */
    protected static volatile com.mongodb.client.MongoClient mongoClient;
    private static final Object _MONGO_INIT_LOCK = new Object();
    protected static volatile com.mongodb.client.MongoDatabase mongo;

    /**
     * Direct Elasticsearch access for tests that need to verify search
     * indices. We use the existing {@link #http} client to talk to ES
     * over its REST API — no extra driver dep needed. Holds the base
     * URL ({@code http://host:9200}) without trailing slash.
     */
    protected String esBaseUrl;

    /**
     * Direct Neo4j (bolt) access for tests that need to verify the
     * recommendation graph (S3-F11/F12). Lazily initialized in
     * {@link #initBase()}; null if Neo4j is unreachable (Neo4j is a
     * "soft" dependency in the M2 spec). Tests must guard with a null
     * check and fail with a clear "Neo4j required for this test" message.
     */
    protected static volatile org.neo4j.driver.Driver neo4j;
    private static final Object _NEO4J_INIT_LOCK = new Object();

    /**
     * Direct Redis (Jedis) access for tests that need to verify cache
     * state (S3-F10 + S3-F12 confirm cache TTL after endpoint calls).
     * Lazily initialized in {@link #initBase()}; null if Redis is
     * unreachable. Bound to the same DB index as the student's app
     * (default 0).
     */
    protected static volatile redis.clients.jedis.Jedis redis;
    private static final Object _REDIS_INIT_LOCK = new Object();

    /**
     * Direct Cassandra (CqlSession) access for tests that verify the
     * time-series tracking-events table (S4-F11 writes, S4-F12 reads).
     * Lazily initialized in {@link #initBase()}; null if Cassandra is
     * unreachable. Bound to the keyspace declared by the student's app
     * (Talabat: ``fooddeliveryks``).
     */
    // STATIC: built once per JVM (not per test class). The prior per-class
    // build leaked sessions (driver `advanced.session-leak.threshold` warning
    // fired at 200+ active) AND wasted ~5s per class on cluster handshake. A
    // JVM shutdown hook installed on first build closes the singleton, so we
    // no longer depend on every @AfterAll firing cleanly.
    protected static volatile com.datastax.oss.driver.api.core.CqlSession cassandra;
    private static final Object _CASSANDRA_INIT_LOCK = new Object();

    // ────────────────────────────────────────────────────────────────
    // Manifest-driven helpers — every path / table / enum / column
    // referenced in a test body is resolved here at runtime from
    // manifest.json on the test classpath. The manifest shape mirrors
    // the scanner's output (test-generator/scanner/.../Scanner.java
    // Manifest record):
    //
    //   * entities:    [{ className, packageName, tableName,
    //                     columns:[{fieldName,columnName,javaType,
    //                               nullable,unique,required,isId,
    //                               isGenerated,isEnum,relationKind}] }]
    //   * enums:       [{ enumName, packageName, values:[...] }]
    //   * controllers: [{ className, packageName, basePaths:[],
    //                     endpoints:[{verb,path,methodName,
    //                                 pathVariables:[],
    //                                 requestBodyType,responseType}] }]
    //   * auth:        { userTable, userColumns:[], roleColumn,
    //                    roleEnumJavaType, roleEnumValues:[],
    //                    adminLabel, emailColumn, passwordColumn,
    //                    loginPath, loginVerb }
    //
    // At grading time the grader runs scanner.jar against the
    // student's project (all 5 services, unified) and writes the JSON
    // into src/test/resources/manifest.json before `mvn test`. At
    // factory time a placeholder scanner-shape JSON sits there so
    // `mvn test-compile` parses cleanly. See
    // memory/reference_grader_manifest_integration.md for deferred
    // grader-side work.
    // ────────────────────────────────────────────────────────────────

    private static volatile java.util.Map<String, Object> _manifestCache;
    private static volatile java.util.Map<String, Object> _themeCache;

    /** Lazily load and cache manifest.json from the test classpath. */
    @SuppressWarnings("unchecked")
    protected static java.util.Map<String, Object> manifest() {
        java.util.Map<String, Object> m = _manifestCache;
        if (m == null) {
            try (java.io.InputStream is = TestBase.class.getClassLoader()
                    .getResourceAsStream("manifest.json")) {
                if (is == null) {
                    throw new IllegalStateException(
                        "manifest.json not on test classpath — check src/test/resources/manifest.json");
                }
                m = new ObjectMapper().readValue(is,
                        new TypeReference<java.util.Map<String, Object>>() {});
                _manifestCache = m;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read manifest.json: " + e.getMessage(), e);
            }
        }
        return m;
    }

    /** Lazily load and cache theme.json from the test classpath (factory-time spec metadata). */
    @SuppressWarnings("unchecked")
    protected static java.util.Map<String, Object> theme() {
        java.util.Map<String, Object> m = _themeCache;
        if (m == null) {
            try (java.io.InputStream is = TestBase.class.getClassLoader()
                    .getResourceAsStream("theme.json")) {
                if (is == null) throw new IllegalStateException("theme.json not on test classpath");
                m = new ObjectMapper().readValue(is, new TypeReference<java.util.Map<String, Object>>() {});
                _themeCache = m;
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to read theme.json: " + e.getMessage(), e);
            }
        }
        return m;
    }

    protected String s2CatalogEntity() {
        String v = (String) theme().get("s2CatalogEntity");
        if (v == null) throw new IllegalStateException("theme.json missing 's2CatalogEntity'");
        return v;
    }
    protected String s3OrderEntity() {
        String v = (String) theme().get("s3OrderEntity");
        if (v == null) throw new IllegalStateException("theme.json missing 's3OrderEntity'");
        return v;
    }
    protected String s2EventsCollection() {
        String v = (String) theme().get("s2EventsCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's2EventsCollection'");
        return mongoCollectionByName(v);
    }
    protected String s2SearchIndex() {
        String v = (String) theme().get("s2SearchIndex");
        if (v == null) throw new IllegalStateException("theme.json missing 's2SearchIndex'");
        return esIndexByName(v);
    }

    /** S3 events Mongo collection — strict spec-name lookup against
     *  manifest.mongoCollections. For Talabat this is "order_events". */
    protected String s3EventsCollection() {
        String v = (String) theme().get("s3EventsCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's3EventsCollection'");
        return mongoCollectionByName(v);
    }

    /** S3 recommendation graph: User-side node label (e.g., "User"). Strict
     *  spec-name lookup against manifest.neo4jNodes. */
    protected String s3GraphUserLabel() {
        String v = (String) theme().get("s3GraphUserLabel");
        if (v == null) throw new IllegalStateException("theme.json missing 's3GraphUserLabel'");
        return neo4jLabelByName(v);
    }

    /** S3 recommendation graph: catalog-side node label (e.g., "Restaurant"
     *  for Talabat). Strict spec-name lookup against manifest.neo4jNodes. */
    protected String s3GraphCatalogLabel() {
        String v = (String) theme().get("s3GraphCatalogLabel");
        if (v == null) throw new IllegalStateException("theme.json missing 's3GraphCatalogLabel'");
        return neo4jLabelByName(v);
    }

    /** S3 recommendation graph: relationship type connecting User → catalog
     *  (e.g., "ORDERED_FROM" for Talabat). Read straight from theme.json —
     *  the type is a runtime literal, not a class annotation, so the scanner
     *  cannot cross-check it. */
    protected String s3GraphRelationship() {
        String v = (String) theme().get("s3GraphRelationship");
        if (v == null) throw new IllegalStateException("theme.json missing 's3GraphRelationship'");
        return v;
    }

    /** S4 entity managed by the delivery/tracking service (e.g., "Delivery"
     *  for Talabat, "Ride" for Uber). Read from theme.json. */
    protected String s4Entity() {
        String v = (String) theme().get("s4Entity");
        if (v == null) throw new IllegalStateException("theme.json missing 's4Entity'");
        return v;
    }

    /** S4 events Mongo collection — strict spec-name lookup against
     *  manifest.mongoCollections. For Talabat this is "delivery_events". */
    protected String s4EventsCollection() {
        String v = (String) theme().get("s4EventsCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's4EventsCollection'");
        return mongoCollectionByName(v);
    }

    /** S4 Cassandra time-series table — strict spec-name lookup against
     *  manifest.cassandraTables. For Talabat this is "delivery_tracking_events". */
    protected String s4TimeseriesTable() {
        String v = (String) theme().get("s4TimeseriesTable");
        if (v == null) throw new IllegalStateException("theme.json missing 's4TimeseriesTable'");
        return cassandraTableByName(v);
    }

    /** S4 Cassandra partition-key field name on the time-series entity
     *  (e.g., "deliveryId"). Read from theme.json. */
    protected String s4TimeseriesPartitionField() {
        String v = (String) theme().get("s4TimeseriesPartitionField");
        if (v == null) throw new IllegalStateException("theme.json missing 's4TimeseriesPartitionField'");
        return v;
    }

    /** S4 Cassandra clustering-key field name on the time-series entity
     *  (e.g., "eventTime"). Read from theme.json. */
    protected String s4TimeseriesClusteringField() {
        String v = (String) theme().get("s4TimeseriesClusteringField");
        if (v == null) throw new IllegalStateException("theme.json missing 's4TimeseriesClusteringField'");
        return v;
    }

    /** S4 actor field on the time-series entity (e.g., "driverName" for
     *  Talabat, sourced from PG via cross-service SQL). Read from theme.json. */
    protected String s4ActorField() {
        String v = (String) theme().get("s4ActorField");
        if (v == null) throw new IllegalStateException("theme.json missing 's4ActorField'");
        return v;
    }

    /** S5 entity managed by the checkout/billing service (e.g., "Payment" for
     *  Talabat, "Transaction" for Amazon). Read from theme.json. */
    protected String s5RefundEntity() {
        String v = (String) theme().get("s5RefundEntity");
        if (v == null) throw new IllegalStateException("theme.json missing 's5RefundEntity'");
        return v;
    }

    /** S5 audit-trail Mongo collection — strict spec-name lookup against
     *  manifest.mongoCollections. For Talabat this is "payment_audit_trail". */
    protected String s5AuditCollection() {
        String v = (String) theme().get("s5AuditCollection");
        if (v == null) throw new IllegalStateException("theme.json missing 's5AuditCollection'");
        return mongoCollectionByName(v);
    }

    /** S5 service cache-key prefix (e.g., "checkout-service" for Talabat,
     *  "billing-service" for Amazon). Used by S5-F12 to verify cache
     *  invalidation. Read from theme.json — read literal, no manifest cross-check. */
    protected String s5ServiceCachePrefix() {
        String v = (String) theme().get("s5ServiceCachePrefix");
        if (v == null) throw new IllegalStateException("theme.json missing 's5ServiceCachePrefix'");
        return v;
    }

    /** S5-F12 strategy class names per spec — read straight from theme.json so
     *  per-theme strategy renames stay declarative. */
    protected String s5StrategyFullRefund() {
        String v = (String) theme().get("s5StrategyFullRefund");
        if (v == null) throw new IllegalStateException("theme.json missing 's5StrategyFullRefund'");
        return v;
    }
    protected String s5StrategyFoodOnly() {
        String v = (String) theme().get("s5StrategyFoodOnly");
        if (v == null) throw new IllegalStateException("theme.json missing 's5StrategyFoodOnly'");
        return v;
    }
    protected String s5StrategyNoRefund() {
        String v = (String) theme().get("s5StrategyNoRefund");
        if (v == null) throw new IllegalStateException("theme.json missing 's5StrategyNoRefund'");
        return v;
    }

    @SuppressWarnings("unchecked")
    protected String s2CategoricalFilterParam() {
        String entity = s2CatalogEntity();
        for (java.util.Map<String, Object> col : entityColumns(entity)) {
            if (Boolean.TRUE.equals(col.get("isId"))) continue;
            String fieldName = (String) col.get("fieldName");
            if ("status".equalsIgnoreCase(fieldName)) continue;
            if (Boolean.TRUE.equals(col.get("isEnum"))) return fieldName;
        }
        // Fallback: entity uses a String field as categorical filter (e.g. Booking theme
        // uses Provider.specialty rather than an enum)
        for (java.util.Map<String, Object> col : entityColumns(entity)) {
            String fieldName = (String) col.get("fieldName");
            if (fieldName == null) continue;
            String jt = (String) col.get("javaType");
            if (!"String".equals(jt)) continue;
            String fLc = fieldName.toLowerCase();
            if (fLc.contains("specialty") || fLc.equals("type") || fLc.contains("category")) {
                return fieldName;
            }
        }
        throw new IllegalStateException("No non-status enum field on entity " + entity);
    }

    @SuppressWarnings("unchecked")
    protected String enumValueAt(String entityClass, String fieldName, int index) {
        for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
            if (fieldName.equals(col.get("fieldName"))) {
                String javaType = (String) col.get("javaType");
                if (!Boolean.TRUE.equals(col.get("isEnum"))) {
                    // String categorical field — return hardcoded values per field name
                    String fLc = fieldName.toLowerCase();
                    if (fLc.contains("specialty")) {
                        return index == 0 ? "CLEANING" : "PLUMBING";
                    }
                    if (fLc.equals("type") || fLc.contains("category")) {
                        return index == 0 ? "TYPE_A" : "TYPE_B";
                    }
                    throw new IllegalArgumentException(
                        "Field " + fieldName + " on " + entityClass + " is not an enum (javaType=" + javaType + ")");
                }
                java.util.List<String> values = enumValues(javaType);
                if (values == null || values.size() <= index) {
                    throw new IllegalStateException(
                        "Enum " + javaType + " has only "
                      + (values == null ? 0 : values.size()) + " values, can't pick index " + index);
                }
                return values.get(index);
            }
        }
        throw new IllegalArgumentException("No field '" + fieldName + "' on entity '" + entityClass + "'");
    }

    @SuppressWarnings("unchecked")
    protected String buildKitchenSinkBody(String entityClass, java.util.Map<String, Object> overrides) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
            if (Boolean.TRUE.equals(col.get("isId"))) continue;
            String relation = (String) col.get("relationKind");
            if (relation != null && !relation.isEmpty()) continue;
            String fieldName = (String) col.get("fieldName");
            Object value = (overrides != null && overrides.containsKey(fieldName))
                    ? overrides.get(fieldName) : _defaultValueFor(col);
            if (value == null) continue;
            if (!first) json.append(",");
            first = false;
            json.append("\"").append(fieldName).append("\":");
            json.append(_jsonSerialize(value));
        }
        json.append("}");
        return json.toString();
    }

    @SuppressWarnings("unchecked")
    private Object _defaultValueFor(java.util.Map<String, Object> col) {
        boolean isEnum = Boolean.TRUE.equals(col.get("isEnum"));
        String javaType = (String) col.get("javaType");
        String fieldName = (String) col.get("fieldName");
        if (isEnum) {
            java.util.List<String> values = enumValues(javaType);
            return (values == null || values.isEmpty()) ? null : values.get(0);
        }
        if (javaType == null) return null;
        switch (javaType) {
            case "String": {
                String fLc = fieldName == null ? "" : fieldName.toLowerCase();
                long nonce = _DEFAULT_NONCE.incrementAndGet();
                if (fLc.contains("email"))  return "sample_" + nonce + "@grader.testgen.io";
                if (fLc.contains("phone") || fLc.contains("mobile"))
                    return "+201" + String.format("%09d", nonce % 1_000_000_000L);
                return "Default " + fieldName;
            }
            case "Long": case "long": case "Integer": case "int": return 1;
            case "Double": case "double": case "BigDecimal": case "Float": case "float": return 4.5;
            case "Boolean": case "boolean": return true;
            case "Instant": case "LocalDateTime": return "2026-04-29T12:00:00";
            case "LocalDate": return "2026-04-29";
            case "JsonNode": case "Map": case "ObjectNode":
                return java.util.Map.of("description", "default " + fieldName);
            default: return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String _jsonSerialize(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof java.util.Map) {
            java.util.Map<String, Object> m = (java.util.Map<String, Object>) value;
            StringBuilder sb = new StringBuilder("{");
            boolean f = true;
            for (java.util.Map.Entry<String, Object> e : m.entrySet()) {
                if (!f) sb.append(",");
                f = false;
                sb.append("\"").append(e.getKey()).append("\":").append(_jsonSerialize(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        return "null";
    }

    // ── Entity helpers ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> _entityRecord(String entityClass) {
        java.util.List<java.util.Map<String, Object>> entities =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("entities");
        if (entities != null) {
            for (java.util.Map<String, Object> e : entities) {
                if (entityClass.equals(e.get("className"))) return e;
            }
            // Second pass: match by derived table name (e.g. "BookingService" → "booking_services")
            String derived = entityClass.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "") + "s";
            for (java.util.Map<String, Object> e : entities) {
                if (derived.equals(e.get("tableName"))) return e;
            }
        }
        throw new IllegalStateException("Entity not in manifest: " + entityClass);
    }

    /** PG table name for an entity (e.g. "User" → "users"). */
    protected String tableName(String entityClass) {
        return (String) _entityRecord(entityClass).get("tableName");
    }

    /** Full column metadata for an entity (fieldName, columnName, javaType, isId, isEnum, etc.). */
    @SuppressWarnings("unchecked")
    protected java.util.List<java.util.Map<String, Object>> entityColumns(String entityClass) {
        return (java.util.List<java.util.Map<String, Object>>) _entityRecord(entityClass).get("columns");
    }

    // ── Enum helpers ────────────────────────────────────────────────

    /** Enum values for a Java enum type (e.g. "Role" → ["ADMIN","CUSTOMER"]).
     *  Falls back to pg_enum if the scanner didn't populate the manifest enums array. */
    @SuppressWarnings("unchecked")
    protected java.util.List<String> enumValues(String enumName) {
        java.util.List<java.util.Map<String, Object>> enums =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("enums");
        if (enums != null) {
            for (java.util.Map<String, Object> e : enums) {
                if (enumName.equals(e.get("enumName"))) {
                    return (java.util.List<String>) e.get("values");
                }
            }
        }
        // PG fallback: scanner may have missed this enum type.
        // Convert Java CamelCase → PG snake_case: "ProviderStatus" → "provider_status".
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enumName.length(); i++) {
            char c = enumName.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        String pgTypeName = sb.toString();
        try {
            java.util.List<String> pgVals = jdbc.queryForList(
                "SELECT enumlabel FROM pg_enum " +
                "WHERE enumtypid = (SELECT oid FROM pg_type WHERE typname = ?) " +
                "ORDER BY enumsortorder", String.class, pgTypeName);
            if (!pgVals.isEmpty()) return pgVals;
        } catch (Exception pgEx) {
            // PG fallback failed — fall through to original error
        }
        throw new IllegalStateException("Enum not in manifest: " + enumName);
    }

    // ── Auth helpers ────────────────────────────────────────────────

    /** Login path from manifest.auth.loginPath. */
    @SuppressWarnings("unchecked")
    protected String loginPath() {
        java.util.Map<String, Object> auth = (java.util.Map<String, Object>) manifest().get("auth");
        if (auth == null) throw new IllegalStateException("manifest.auth missing");
        String p = (String) auth.get("loginPath");
        if (p == null) throw new IllegalStateException("manifest.auth.loginPath missing");
        return p;
    }

    /** Register path — discovered by walking controller endpoints for a POST whose full path ends with "/auth/register". */
    protected String registerPath() {
        java.util.regex.Pattern re = java.util.regex.Pattern.compile(".*/auth/register$");
        for (java.util.Map<String, Object> ep : _allEndpointsWithFullPath()) {
            String verb = (String) ep.get("verb");
            String full = (String) ep.get("_fullPath");
            if ("POST".equalsIgnoreCase(verb) && full != null && re.matcher(full).matches()) {
                return full;
            }
        }
        throw new IllegalStateException("No POST endpoint matching '/auth/register' found in manifest controllers");
    }

    // ── Controller / path resolution ────────────────────────────────

    /**
     * Project all (controller × endpoint × basePath) tuples into a flat list
     * with a synthetic "_fullPath" entry equal to basePath + endpoint.path.
     * Used for path-shape lookups that don't care which controller exposed
     * the endpoint.
     */
    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, Object>> _allEndpointsWithFullPath() {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, Object>> controllers =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("controllers");
        if (controllers == null) return out;
        for (java.util.Map<String, Object> ctrl : controllers) {
            java.util.List<String> basePaths = (java.util.List<String>) ctrl.get("basePaths");
            if (basePaths == null || basePaths.isEmpty()) basePaths = java.util.List.of("");
            java.util.List<java.util.Map<String, Object>> eps =
                (java.util.List<java.util.Map<String, Object>>) ctrl.get("endpoints");
            if (eps == null) continue;
            for (java.util.Map<String, Object> ep : eps) {
                String path = (String) ep.get("path");
                if (path == null) path = "";
                // The scanner already joins controller basePath + method-mapping
                // path when building the endpoint's "path" field. Detect when
                // the path already starts with a basePath (so we don't double-
                // prefix) — fall back to bp + path otherwise.
                boolean hasBasePathBaked = false;
                for (String bp : basePaths) {
                    if (bp != null && !bp.isEmpty() && path.startsWith(bp + "/")) {
                        hasBasePathBaked = true;
                        break;
                    }
                    if (bp != null && bp.equals(path)) {
                        hasBasePathBaked = true;
                        break;
                    }
                }
                if (hasBasePathBaked) {
                    java.util.Map<String, Object> withFull = new java.util.HashMap<>(ep);
                    withFull.put("_fullPath", path);
                    out.add(withFull);
                } else {
                    for (String bp : basePaths) {
                        String full = (bp == null ? "" : bp) + path;
                        java.util.Map<String, Object> withFull = new java.util.HashMap<>(ep);
                        withFull.put("_fullPath", full);
                        out.add(withFull);
                    }
                }
            }
        }
        return out;
    }

    /** Variant URL segments derived from a snake_case table name (kebab, no-sep, camel). */
    private java.util.List<String> _pathSegmentCandidates(String table) {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (table == null) return result;
        result.add(table);                          // menu_items
        if (table.contains("_")) {
            result.add(table.replace('_', '-'));    // menu-items
            result.add(table.replace("_", ""));     // menuitems
            // camelCase
            StringBuilder camel = new StringBuilder();
            boolean upNext = false;
            for (char c : table.toCharArray()) {
                if (c == '_') { upNext = true; continue; }
                camel.append(upNext ? Character.toUpperCase(c) : c);
                upNext = false;
            }
            String c = camel.toString();
            if (!result.contains(c)) result.add(c);
        }
        return result;
    }

    /**
     * Full path TEMPLATE for an entity's "read by id" endpoint, including
     * any parent {placeholders}. Walks every controller's endpoints, picks
     * the GET whose full path (basePath + endpoint.path) ends with
     * "/<segment>/{var}" for any segment variant of the entity's tableName.
     * Among matches, longest path wins (favours nested over collisions).
     *
     * <p>Examples:
     *   crudReadPath("User")     → "/api/users/{id}"
     *   crudReadPath("MenuItem") → "/api/restaurants/{restaurantId}/menu-items/{id}"
     *
     * <p>Throws {@link IllegalStateException} with a clear message if no
     * matching endpoint exists in the manifest.
     */
    protected String crudReadPath(String entityClass) {
        String table = tableName(entityClass);
        java.util.List<String> segs = _pathSegmentCandidates(table);
        String best = null;
        for (java.util.Map<String, Object> ep : _allEndpointsWithFullPath()) {
            String verb = (String) ep.get("verb");
            if (!"GET".equalsIgnoreCase(verb)) continue;
            String full = (String) ep.get("_fullPath");
            if (full == null) continue;
            for (String seg : segs) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                    "/" + java.util.regex.Pattern.quote(seg) + "/\\{[^}]+\\}$");
                if (p.matcher(full).find()) {
                    if (best == null || full.length() > best.length()) best = full;
                }
            }
        }
        if (best == null) {
            throw new IllegalStateException(
                "No CRUD read path found in manifest for entity '" + entityClass
              + "' (table=" + table + "; tried segments=" + segs + ")");
        }
        return best;
    }

    /**
     * Collection-level path template (no trailing /{id}).
     *   crudCollectionPath("User")     → "/api/users"
     *   crudCollectionPath("MenuItem") → "/api/restaurants/{restaurantId}/menu-items"
     */
    protected String crudCollectionPath(String entityClass) {
        return crudReadPath(entityClass).replaceFirst("/\\{[^}]+\\}$", "");
    }

    /** Substitute {placeholder} variables in a path template with their string values. */
    protected String fillPath(String template, java.util.Map<String, Object> vars) {
        String out = template;
        for (java.util.Map.Entry<String, Object> e : vars.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
        }
        return out;
    }

    /**
     * Convenience: full read path for a top-level entity by id. Replaces
     * the trailing {var} only; any parent placeholders remain unfilled
     * (use {@link #fillPath} for nested cases).
     */
    protected String crudReadPathFor(String entityClass, long id) {
        return crudReadPath(entityClass).replaceFirst("\\{[^}]+\\}$", String.valueOf(id));
    }

    /**
     * Read the role column for a user, looked up by email — fully
     * manifest-driven (uses auth.userTable / auth.emailColumn /
     * auth.roleColumn). Returns the role label as a String, with the
     * column cast to text so PG-native ENUM types come back as their
     * label rather than their internal OID.
     *
     * <p>Used by privilege-escalation tests (e.g. "register with
     * role=ADMIN in body, verify role didn't get honored").
     *
     * <p>Returns {@code null} if the row doesn't exist (let the test
     * decide whether that's a failure).
     */
    @SuppressWarnings("unchecked")
    protected String fetchUserRole(String email) {
        java.util.Map<String, Object> auth = (java.util.Map<String, Object>) manifest().get("auth");
        if (auth == null) throw new IllegalStateException("manifest.auth missing");
        String userTable = (String) auth.get("userTable");
        String roleCol = (String) auth.get("roleColumn");
        String emailCol = (String) auth.get("emailColumn");
        // Fallback: scanner couldn't identify the User entity → discover from information_schema.
        if (userTable == null) {
            try {
                userTable = jdbc.queryForObject(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema='public' " +
                    "AND table_name IN ('users','user','app_user','app_users','customers','customer','accounts','account') " +
                    "ORDER BY CASE table_name " +
                    "WHEN 'users' THEN 1 WHEN 'user' THEN 2 WHEN 'app_user' THEN 3 WHEN 'app_users' THEN 4 " +
                    "WHEN 'customers' THEN 5 WHEN 'customer' THEN 6 WHEN 'accounts' THEN 7 WHEN 'account' THEN 8 END " +
                    "LIMIT 1",
                    String.class);
            } catch (DataAccessException ignored) {}
        }
        if (emailCol == null && userTable != null) {
            try {
                emailCol = jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema='public' AND table_name=? " +
                    "AND column_name IN ('email','user_email','username','login') " +
                    "ORDER BY ordinal_position LIMIT 1",
                    String.class, userTable);
            } catch (DataAccessException ignored) {}
        }
        if (roleCol == null && userTable != null) {
            try {
                roleCol = jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema='public' AND table_name=? " +
                    "AND column_name IN ('role','user_role','roles','authority') " +
                    "ORDER BY ordinal_position LIMIT 1",
                    String.class, userTable);
            } catch (DataAccessException ignored) {}
        }
        if (userTable == null || roleCol == null || emailCol == null) {
            throw new IllegalStateException(
                "manifest.auth missing one of {userTable, roleColumn, emailColumn}");
        }
        // Defensive identifier whitelist — manifest values flow into raw SQL.
        java.util.regex.Pattern ident = java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
        if (!ident.matcher(userTable).matches()
            || !ident.matcher(roleCol).matches()
            || !ident.matcher(emailCol).matches()) {
            throw new IllegalStateException(
                "Manifest auth identifiers contain unsafe characters; refusing to build SQL");
        }
        try {
            return jdbc.queryForObject(
                "SELECT " + roleCol + "::text FROM " + userTable + " WHERE " + emailCol + " = ?",
                String.class, email);
        } catch (DataAccessException e) {
            return null;
        }
    }

    // ─── cross-theme generic seed helpers ───
    //
    // s2CatalogFkColumn() walks information_schema to find the FK column on the
    // S3 order table that points to the S2 catalog table — robust to whatever
    // the student named that column. _seedTableRow / _seedS2Catalog /
    // _seedS3Orders / _seedBaselineUser are generic INSERT primitives that work
    // off information_schema metadata + per-type defaults; the 7 non-Talabat
    // themes orchestrate them inside autoSeedBaselineData(). Talabat's own
    // autoSeedBaselineData() has bespoke seeders and DOES NOT call these — but
    // TC49/TC52 in PublicTests use s2CatalogFkColumn() to keep the SQL aligned
    // with whatever Talabat's order-→-restaurant FK is named.

    protected String s2CatalogFkColumn() {
        String orderTable   = tableName(s3OrderEntity());
        String catalogTable = tableName(s2CatalogEntity());
        try {
            return jdbc.queryForObject(
                "SELECT kcu.column_name "
              + "FROM information_schema.table_constraints tc "
              + "JOIN information_schema.key_column_usage kcu "
              + "  ON tc.constraint_name = kcu.constraint_name "
              + " AND tc.table_schema = kcu.table_schema "
              + "JOIN information_schema.constraint_column_usage ccu "
              + "  ON tc.constraint_name = ccu.constraint_name "
              + " AND tc.table_schema = ccu.table_schema "
              + "WHERE tc.constraint_type = 'FOREIGN KEY' "
              + "  AND tc.table_schema = 'public' "
              + "  AND tc.table_name = ? AND ccu.table_name = ? "
              + "ORDER BY kcu.ordinal_position LIMIT 1",
                String.class, orderTable, catalogTable);
        } catch (DataAccessException e) {
            // Fallback: no formal FK constraint — check for conventionally-named column
            // e.g. Booking theme uses Long providerId (no @ManyToOne) → column "provider_id"
            String guessCol = s2CatalogEntity().toLowerCase().replace(" ", "_") + "_id";
            try {
                return jdbc.queryForObject(
                    "SELECT column_name FROM information_schema.columns "
                  + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                    String.class, orderTable, guessCol);
            } catch (DataAccessException e2) { /* fall through */ }
            throw new AssertionError(
                "No FK from order table '" + orderTable
              + "' to catalog table '" + catalogTable
              + "'. The S3 order entity must have a relation to the S2 catalog entity. "
              + e.getMessage());
        }
    }

    private static final java.util.Set<String> _NON_ENUM_UDTS = java.util.Set.of(
        "varchar", "text", "bpchar", "char", "name",
        "int2", "int4", "int8", "float4", "float8", "numeric", "money",
        "bool", "date", "timestamp", "timestamptz", "time", "timetz",
        "uuid", "bytea", "json", "jsonb", "interval", "cidr", "inet"
    );

    private boolean _isPgEnumUdt(String udt) {
        if (udt == null) return false;
        String lc = udt.toLowerCase();
        if (lc.startsWith("_")) return false;
        if (!lc.matches("^[a-z_][a-z0-9_]*$")) return false;
        return !_NON_ENUM_UDTS.contains(lc);
    }

    private String _firstEnumLabel(String udt) {
        if (!_isPgEnumUdt(udt)) return null;
        try {
            return jdbc.queryForObject(
                "SELECT (enum_range(NULL::" + udt + "))[1]::text", String.class);
        } catch (DataAccessException e) { return null; }
    }

    /** Cache: PG enum udt → labels (case-sensitive, in declaration order). */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<String>>
        _ENUM_LABELS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.List<String> _enumLabels(String udt) {
        if (!_isPgEnumUdt(udt)) return java.util.Collections.emptyList();
        return _ENUM_LABELS_CACHE.computeIfAbsent(udt, u -> {
            try {
                @SuppressWarnings("unchecked")
                java.util.List<String> raw = jdbc.queryForObject(
                    "SELECT enum_range(NULL::" + u + ")::text", String.class) == null
                  ? java.util.Collections.<String>emptyList()
                  : (java.util.List<String>) (java.util.List<?>) java.util.Arrays.asList(
                        jdbc.queryForObject(
                            "SELECT enum_range(NULL::" + u + ")::text", String.class)
                          .replaceAll("^\\{|\\}$", "").split(","));
                java.util.List<String> out = new java.util.ArrayList<>(raw.size());
                for (String s : raw) out.add(s.trim().replaceAll("^\"|\"$", ""));
                return out;
            } catch (DataAccessException e) {
                return java.util.Collections.emptyList();
            }
        });
    }

    /** Coerce a string `requested` value to one accepted by the column's domain.
     *  Used for both auto-fills and caller-supplied overrides — when the spec
     *  label (e.g. "DELIVERED") doesn't match the student's actual enum or
     *  CHECK list (e.g. ["NEW","READY","SERVED"]), we substitute the first
     *  accepted label so the seed row lands. Tests that depend on a specific
     *  spec label still get to assert against it (and fail loudly when the
     *  student deviated from spec). Returns the input unchanged for
     *  non-enum/non-CHECK columns. */
    private Object _coerceValue(String table, String column, String dataType, String udt, Object value) {
        if (!(value instanceof String)) return value;
        String s = (String) value;
        // Enum domain check
        if ("USER-DEFINED".equals(dataType) && _isPgEnumUdt(udt)) {
            java.util.List<String> labels = _enumLabels(udt);
            if (!labels.isEmpty() && !labels.contains(s)) {
                // Try case-insensitive match before falling back
                for (String l : labels) if (l.equalsIgnoreCase(s)) return l;
                return labels.get(0);
            }
            return s;
        }
        // CHECK-list domain check
        java.util.List<String> chk = _checkInListsFor(table).get(column);
        if (chk != null && !chk.isEmpty() && !chk.contains(s)) {
            for (String l : chk) if (l.equalsIgnoreCase(s)) return l;
            return chk.get(0);
        }
        return value;
    }

    /** Per-process counter so every text/email/phone auto-default is unique
     *  even within a single transaction (UNIQUE constraint friendly). */
    private static final java.util.concurrent.atomic.AtomicLong _DEFAULT_NONCE =
        new java.util.concurrent.atomic.AtomicLong(System.nanoTime());

    private Object _defaultForPgColumn(String dataType, String udt, String column) {
        if (dataType == null) return null;
        String nameLc = column == null ? "" : column.toLowerCase();
        long nonce = _DEFAULT_NONCE.incrementAndGet();
        switch (dataType) {
            case "character varying": case "character": case "text": {
                // Shape the value so it satisfies common student CHECK / @Email
                // / regex constraints. Uniqueness via the per-process nonce
                // means UNIQUE columns (email, phone, code, slug, username)
                // don't collide across sibling INSERTs in the same seed.
                if (nameLc.contains("email"))    return "sample_" + nonce + "@grader.testgen.io";
                if (nameLc.contains("phone") || nameLc.contains("mobile"))
                                                  return "+201" + String.format("%09d", nonce % 1_000_000_000L);
                if (nameLc.contains("password") || nameLc.contains("hash"))
                                                  return _PRESEED_BCRYPT;
                if (nameLc.contains("url") || nameLc.contains("link") || nameLc.contains("href"))
                                                  return "https://example.com/" + nonce;
                if (nameLc.equals("currency") || nameLc.endsWith("_currency"))
                                                  return "USD";
                if (nameLc.equals("locale") || nameLc.equals("language"))
                                                  return "en";
                if (nameLc.equals("country") || nameLc.endsWith("_country"))
                                                  return "EG";
                return "Sample " + column + " " + nonce;
            }
            case "smallint": case "integer": case "bigint":
                // Counter-shaped columns that semantically start at zero.
                // Defaulting "totalRatings"/"orderCount" to 1 corrupts running-
                // average and totalCount semantics on the very first event the
                // student handles (their math is correct against a 0 baseline).
                if (nameLc.startsWith("total") || nameLc.endsWith("count") || nameLc.endsWith("_count")) return 0;
                return 1;
            case "numeric": case "real": case "double precision":
                // Same logic for averages / aggregates that start at 0.0.
                if (nameLc.equals("rating") || nameLc.endsWith("rating") || nameLc.endsWith("_rating")) return 0.0;
                if (nameLc.startsWith("avg") || nameLc.startsWith("total") || nameLc.startsWith("sum")) return 0.0;
                return 4.5;
            case "money": return new java.math.BigDecimal("0.00");
            case "boolean": return true;
            case "date": return java.sql.Date.valueOf("2026-01-01");
            case "timestamp without time zone":
            case "timestamp with time zone":
                return java.sql.Timestamp.valueOf("2026-01-01 12:00:00");
            case "time without time zone":
            case "time with time zone":
                return java.sql.Time.valueOf("12:00:00");
            case "interval": return "0 seconds";
            case "json": case "jsonb": return "{\"note\":\"true\"}";
            case "uuid": return java.util.UUID.randomUUID().toString();
            case "bytea": return new byte[0];
            case "inet": return "127.0.0.1";
            case "cidr": return "127.0.0.0/24";
            case "macaddr": return "08:00:2b:01:02:03";
            case "bit": case "bit varying": return "0";
            case "xml": return "<x/>";
            case "USER-DEFINED": return _firstEnumLabel(udt);
            case "ARRAY": return "{}";
            default: return null;
        }
    }

    /** Cache: table → list of [{column, valid_values}] derived from CHECK constraints. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Map<String, java.util.List<String>>>
        _CHECK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Parse `pg_get_constraintdef` text for the two CHECK shapes Hibernate
     *  emits from {@code @Enumerated(STRING)}:
     *    1.  `(((<col>)::text = ANY ((ARRAY['A'::varchar, 'B'::varchar])::text[])))`
     *    2.  `((<col>)::text = ANY (ARRAY['A'::text, 'B'::text]))`
     *    3.  `(<col> IN ('A', 'B'))`
     *  Returns a {column → [valid labels in declaration order]} map.
     *  Used by {@link #_coerceValue} to pick a value that satisfies the
     *  CHECK both for caller-supplied overrides and for auto-filled defaults. */
    @SuppressWarnings("unchecked")
    private java.util.Map<String, java.util.List<String>> _checkInListsFor(String table) {
        return _CHECK_CACHE.computeIfAbsent(table, t -> {
            java.util.Map<String, java.util.List<String>> out = new java.util.HashMap<>();
            try {
                java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT pg_get_constraintdef(c.oid) AS def "
                  + "FROM pg_constraint c "
                  + "JOIN pg_class cl ON c.conrelid = cl.oid "
                  + "JOIN pg_namespace n ON cl.relnamespace = n.oid "
                  + "WHERE c.contype = 'c' AND n.nspname = 'public' AND cl.relname = ?",
                    t);
                // 1. ANY-form (`<col> = ANY ((ARRAY[...])::text[])` or `ANY (ARRAY[...])`)
                java.util.regex.Pattern anyForm = java.util.regex.Pattern.compile(
                    "\\(\\s*(\\w+)\\s*\\)\\s*::\\s*\\w+\\s*=\\s*ANY\\s*\\(+\\s*ARRAY\\s*\\[(.+?)\\]",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                // 2. Plain IN-form (`<col> IN ('A', 'B', …)`)
                java.util.regex.Pattern inForm = java.util.regex.Pattern.compile(
                    "\\b(\\w+)\\s+IN\\s*\\((.+?)\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);
                java.util.regex.Pattern litExtract = java.util.regex.Pattern.compile("'([^']*)'");
                for (java.util.Map<String, Object> r : rows) {
                    String def = String.valueOf(r.get("def"));
                    String col = null;
                    String body = null;
                    java.util.regex.Matcher m = anyForm.matcher(def);
                    if (m.find()) {
                        col = m.group(1);
                        body = m.group(2);
                    } else {
                        m = inForm.matcher(def);
                        if (m.find()) {
                            col = m.group(1);
                            body = m.group(2);
                        }
                    }
                    if (col == null || body == null) continue;
                    java.util.List<String> labels = new java.util.ArrayList<>();
                    java.util.regex.Matcher lm = litExtract.matcher(body);
                    while (lm.find()) labels.add(lm.group(1));
                    if (!labels.isEmpty()) out.put(col, labels);
                }
            } catch (DataAccessException ignored) { }
            return out;
        });
    }

    /** Cache for FK-target lookups: junction table → list of [{column, refTable}]. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.List<java.util.Map<String, String>>>
        _FK_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Walks information_schema for the FK columns on `table` and the tables they reference. */
    @SuppressWarnings("unchecked")
    private java.util.List<java.util.Map<String, String>> _fkColumnsOf(String table) {
        return _FK_CACHE.computeIfAbsent(table, t -> {
            try {
                java.util.List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT kcu.column_name AS column, ccu.table_name AS ref "
                  + "FROM information_schema.table_constraints tc "
                  + "JOIN information_schema.key_column_usage kcu "
                  + "  ON tc.constraint_name = kcu.constraint_name "
                  + " AND tc.table_schema = kcu.table_schema "
                  + "JOIN information_schema.constraint_column_usage ccu "
                  + "  ON tc.constraint_name = ccu.constraint_name "
                  + " AND tc.table_schema = ccu.table_schema "
                  + "WHERE tc.constraint_type = 'FOREIGN KEY' "
                  + "  AND tc.table_schema = 'public' "
                  + "  AND tc.table_name = ?",
                    t);
                java.util.List<java.util.Map<String, String>> out = new java.util.ArrayList<>();
                for (java.util.Map<String, Object> r : rows) {
                    java.util.Map<String, String> m = new java.util.HashMap<>();
                    m.put("column", String.valueOf(r.get("column")));
                    m.put("ref",    String.valueOf(r.get("ref")));
                    out.add(m);
                }
                return out;
            } catch (DataAccessException e) {
                return java.util.Collections.emptyList();
            }
        });
    }

    /** Find the FK reference target for a column on `table`, or null if not a FK. */
    private String _fkRefTable(String table, String column) {
        for (java.util.Map<String, String> fk : _fkColumnsOf(table)) {
            if (column.equals(fk.get("column"))) return fk.get("ref");
        }
        return null;
    }

    /** Pick any existing id from `refTable` so an auto-filled FK column points at a real row.
     *  Returns null if the referenced table is empty. */
    private Long _anyExistingId(String refTable) {
        try {
            return jdbc.queryForObject(
                "SELECT id FROM \"" + refTable + "\" ORDER BY id LIMIT 1", Long.class);
        } catch (DataAccessException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    protected void _seedTableRow(String table, java.util.Map<String, Object> overrides) {
        if (jdbc == null) return;
        java.util.List<java.util.Map<String, Object>> cols;
        try {
            cols = jdbc.queryForList(
                "SELECT column_name, data_type, udt_name, is_nullable, column_default "
              + "FROM information_schema.columns "
              + "WHERE table_schema = 'public' AND table_name = ? "
              + "ORDER BY ordinal_position",
                table);
        } catch (DataAccessException e) { return; }
        if (cols == null || cols.isEmpty()) return;
        StringBuilder sql  = new StringBuilder("INSERT INTO \"").append(table).append("\" (");
        StringBuilder vals = new StringBuilder(") VALUES (");
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (java.util.Map<String, Object> c : cols) {
            String name = (String) c.get("column_name");
            if (name == null) continue;
            if ("id".equalsIgnoreCase(name)) continue;
            String dataType = (String) c.get("data_type");
            String udt      = (String) c.get("udt_name");
            Object value;
            if (overrides != null && overrides.containsKey(name)) {
                value = overrides.get(name);
                if (value == null) continue;
            } else {
                value = _defaultForPgColumn(dataType, udt, name);
                if (value == null) continue;
            }
            // Coerce to a value the student's enum / CHECK list actually accepts
            // (spec labels like "DELIVERED" → student's first valid label).
            value = _coerceValue(table, name, dataType, udt, value);
            if (!first) { sql.append(", "); vals.append(", "); }
            first = false;
            sql.append("\"").append(name).append("\"");
            if ("USER-DEFINED".equals(dataType) && _isPgEnumUdt(udt) && value instanceof String s) {
                vals.append("'").append(s.replace("'", "''")).append("'::").append(udt);
            } else if ("json".equals(dataType) || "jsonb".equals(dataType)) {
                vals.append("?::").append(dataType);
                params.add(value);
            } else {
                vals.append("?");
                params.add(value);
            }
        }
        sql.append(vals).append(")");
        try { jdbc.update(sql.toString(), params.toArray()); }
        catch (DataAccessException ignored) { }
    }

    /** Schema-driven INSERT that returns the new row's id. Walks
     *  information_schema for the table; for each column:
     *    - If overrides has a non-null entry → use it (with native enum /
     *      jsonb cast as appropriate).
     *    - If overrides has an explicit null → skip (let DB default or null).
     *    - Else if column has a DB default → skip (let DB use default).
     *    - Else if column is nullable → skip (let null).
     *    - Else (NOT NULL with no default + no override) → generate a
     *      type-appropriate default via {@link #_defaultForPgColumn}.
     *
     *  This is the lenient pattern: callers specify only the columns they
     *  care about; any spec-mandated NOT NULL columns the test doesn't
     *  know about are auto-filled with sensible defaults so the INSERT
     *  doesn't fail. Returns the inserted row's id (RETURNING id), which
     *  is the canonical pattern for downstream FK references. */
    @SuppressWarnings("unchecked")
    protected Long insertRowReturningId(String table, java.util.Map<String, Object> overrides) {
        if (jdbc == null) {
            throw new AssertionError("jdbc not initialized");
        }
        java.util.List<java.util.Map<String, Object>> cols;
        try {
            cols = jdbc.queryForList(
                "SELECT column_name, data_type, udt_name, is_nullable, column_default "
              + "FROM information_schema.columns "
              + "WHERE table_schema = 'public' AND table_name = ? "
              + "ORDER BY ordinal_position",
                table);
        } catch (DataAccessException e) {
            throw new AssertionError("Cannot read schema for table '" + table + "': " + e.getMessage(), e);
        }
        if (cols == null || cols.isEmpty()) {
            throw new AssertionError("No columns found for table: " + table);
        }
        StringBuilder sql  = new StringBuilder("INSERT INTO \"").append(table).append("\" (");
        StringBuilder vals = new StringBuilder(") VALUES (");
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (java.util.Map<String, Object> c : cols) {
            String name = (String) c.get("column_name");
            if (name == null) continue;
            if ("id".equalsIgnoreCase(name)) continue;
            String dataType = (String) c.get("data_type");
            String udt      = (String) c.get("udt_name");
            String isNullable = (String) c.get("is_nullable");
            String columnDefault = (String) c.get("column_default");
            boolean nullable = "YES".equalsIgnoreCase(isNullable);
            boolean hasDbDefault = columnDefault != null && !columnDefault.toString().isBlank();

            Object value;
            if (overrides != null && overrides.containsKey(name)) {
                value = overrides.get(name);
                if (value == null) continue;            // explicit null → skip
            } else if (!nullable) {
                // NOT NULL column with no override: always supply a value.
                // Don't trust column_default — Hibernate emits DEFAULT NULL
                // for nullable=false columns (contradictory but real), which
                // would silently insert null and fail the constraint.
                String fkRef = _fkRefTable(table, name);
                if (fkRef != null) {
                    // FK NOT NULL: must point at a real row in the parent table.
                    // Auto-fill with id=1 fails the FK constraint if the parent
                    // is empty. Probe for any existing id; throw clearly if none.
                    Long existing = _anyExistingId(fkRef);
                    if (existing == null) {
                        if (hasDbDefault) {
                            continue;                   // student-provided default may handle it
                        }
                        throw new AssertionError(
                            "Cannot auto-default NOT NULL FK column " + table + "." + name
                          + " → " + fkRef + " (parent table is empty). "
                          + "Caller must seed " + fkRef + " first or provide an override.");
                    }
                    value = existing;
                } else {
                    // CHECK-constraint awareness: if the column is restricted to
                    // an IN-list (or `= ANY(ARRAY[...])`) of literals, pick the
                    // first valid label so the INSERT doesn't fail on a check
                    // constraint we'd otherwise not know about.
                    java.util.List<String> labels = _checkInListsFor(table).get(name);
                    if (labels != null && !labels.isEmpty()) {
                        value = labels.get(0);
                        if (!first) { sql.append(", "); vals.append(", "); }
                        first = false;
                        sql.append("\"").append(name).append("\"");
                        vals.append("?");
                        params.add(value);
                        continue;
                    }
                    value = _defaultForPgColumn(dataType, udt, name);
                    if (value == null) {
                        if (hasDbDefault) {
                            continue;                   // fall back to whatever DB default the student set
                        }
                        throw new AssertionError(
                            "Cannot auto-default NOT NULL column " + table + "." + name
                          + " (dataType=" + dataType + ", udt=" + udt + "). "
                          + "Caller must provide an override.");
                    }
                }
            } else if (hasDbDefault) {
                continue;                                // nullable + has default → let DB pick
            } else {
                continue;                                // nullable + no default → skip
            }
            // Coerce to a value the student's enum / CHECK list actually accepts
            // (spec labels like "DELIVERED" → student's first valid label).
            value = _coerceValue(table, name, dataType, udt, value);
            if (!first) { sql.append(", "); vals.append(", "); }
            first = false;
            sql.append("\"").append(name).append("\"");
            if ("USER-DEFINED".equals(dataType) && _isPgEnumUdt(udt) && value instanceof String s) {
                vals.append("'").append(s.replace("'", "''")).append("'::").append(udt);
            } else if ("json".equals(dataType) || "jsonb".equals(dataType)) {
                vals.append("?::").append(dataType);
                params.add(value);
            } else {
                vals.append("?");
                params.add(value);
            }
        }
        sql.append(vals).append(") RETURNING id");
        return jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
    }

    private static final String _BASELINE_BCRYPT =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @SuppressWarnings("unchecked")
    protected void _seedBaselineUser(String role, String email) {
        java.util.Map<String, Object> auth = (java.util.Map<String, Object>) manifest().get("auth");
        if (auth == null) return;
        String userTable = (String) auth.get("userTable");
        if (userTable == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put("email", email);
        ov.put("password", _BASELINE_BCRYPT);
        ov.put("name", "Baseline " + role);
        ov.put("phone", "+201" + nonce().substring(0, 9));
        ov.put("role", role);
        ov.put("status", "ACTIVE");
        _seedTableRow(userTable, ov);
    }

    protected void _seedS2Catalog(int count) {
        String table = tableName(s2CatalogEntity());
        for (int i = 1; i <= count; i++) {
            java.util.Map<String, Object> ov = new java.util.HashMap<>();
            ov.put("name", "Preseed " + s2CatalogEntity() + " " + i);
            ov.put("title", "Preseed " + s2CatalogEntity() + " " + i);
            ov.put("description", "Preseed " + s2CatalogEntity() + " " + i + " description");
            _seedTableRow(table, ov);
        }
    }

    protected void _seedS3Orders(long userId) {
        String table = tableName(s3OrderEntity());
        String fk;
        try { fk = s2CatalogFkColumn(); }
        catch (AssertionError e) { return; }
        long[] catalogIds = {1L, 1L, 2L, 4L};
        for (long cId : catalogIds) {
            java.util.Map<String, Object> ov = new java.util.HashMap<>();
            ov.put(fk, cId);
            ov.put("user_id", userId);
            ov.put("total_amount", 50.0);
            ov.put("amount", 50.0);
            ov.put("price", 50.0);
            _seedTableRow(table, ov);
        }
    }
    // ─── end cross-theme generic seed helpers ───

    /**
     * Pick the first manifest entity (in declared order) that is NOT
     * "User" AND has a top-level CRUD collection path (no unfilled
     * parent {placeholders}). Useful for auth/CRUD tests that want a
     * non-User endpoint but mustn't hardcode a specific entity (so the
     * same test code maps cleanly across all 8 themes — each theme's
     * "first non-User" differs).
     */
    @SuppressWarnings("unchecked")
    protected String firstTopLevelNonUserEntity() {
        java.util.List<java.util.Map<String, Object>> entities =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("entities");
        if (entities != null) {
            for (java.util.Map<String, Object> e : entities) {
                String cls = (String) e.get("className");
                if (cls == null || "User".equals(cls)) continue;
                try {
                    String collPath = crudCollectionPath(cls);
                    if (collPath != null && !collPath.contains("{")) return cls;
                } catch (IllegalStateException ignored) {
                    // Entity has no resolvable CRUD path — skip.
                }
            }
        }
        throw new IllegalStateException("No top-level non-User CRUD entity in manifest");
    }

    @BeforeAll
    protected void initBase() {
        BASE_URL = envOr("APP_BASE_URL", "http://localhost:8080");
        // Per-service URLs — each test method picks the right one for its service.
        // Defaults match the canonical Talabat docker-compose port allocation.
        userServiceUrl     = envOr("USER_SERVICE_URL",     "http://localhost:8081");
        catalogServiceUrl  = envOr("CATALOG_SERVICE_URL",  envOr("RESTAURANT_SERVICE_URL", "http://localhost:8082"));
        orderServiceUrl    = envOr("ORDER_SERVICE_URL",    "http://localhost:8083");
        deliveryServiceUrl = envOr("DELIVERY_SERVICE_URL", "http://localhost:8084");
        checkoutServiceUrl = envOr("CHECKOUT_SERVICE_URL", "http://localhost:8085");
        String dbUrl = envOr("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/fooddeliverydb");
        String dbUser = envOr("SPRING_DATASOURCE_USERNAME", "postgres");
        String dbPass = envOr("SPRING_DATASOURCE_PASSWORD", "postgres");

        // HttpClient is heavyweight (own thread pool). JVM-singleton
        // so we don't spawn one per test class.
        if (http == null) {
            synchronized (_HTTP_INIT_LOCK) {
                if (http == null) {
                    http = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .build();
                }
            }
        }
        // JdbcTemplate is thread-safe; wraps a DriverManagerDataSource.
        // JVM-singleton — one DataSource for the whole test run.
        if (jdbc == null) {
            synchronized (_JDBC_INIT_LOCK) {
                if (jdbc == null) {
                    jdbc = new JdbcTemplate(new DriverManagerDataSource(dbUrl, dbUser, dbPass));
                }
            }
        }

        // ── MongoDB (soft, JVM-singleton) ────────────────────────────
        // MongoClient owns a connection pool — one per JVM, not per
        // test class. Shutdown hook closes it on JVM exit.
        if (mongoClient == null) {
            synchronized (_MONGO_INIT_LOCK) {
                if (mongoClient == null) {
                    String mongoUri = envOr("SPRING_DATA_MONGODB_URI", "mongodb://localhost:27017");
                    try {
                        com.mongodb.ConnectionString cs = new com.mongodb.ConnectionString(mongoUri);
                        mongoClient = com.mongodb.client.MongoClients.create(cs);
                        String dbName = cs.getDatabase();
                        if (dbName == null || dbName.isBlank()) {
                            dbName = envOr("SPRING_DATA_MONGODB_DATABASE", "talabat");
                        }
                        mongo = mongoClient.getDatabase(dbName);
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { mongoClient.close(); } catch (Exception ignored) {}
                        }, "m2-tests-mongo-close"));
                    } catch (Exception ignored) {
                        mongoClient = null;
                        mongo = null;
                    }
                }
            }
        }

        // ── Elasticsearch (soft) ────────────────────────────────────
        String esUris = envOr("SPRING_ELASTICSEARCH_URIS", "http://localhost:9200");
        String firstUri = esUris.split(",")[0].trim();
        if (firstUri.endsWith("/")) firstUri = firstUri.substring(0, firstUri.length() - 1);
        this.esBaseUrl = firstUri;

        // ── Neo4j (soft, JVM-singleton) ──────────────────────────────
        // Neo4j Driver is explicitly designed as one-per-application.
        // Singleton + shutdown hook for the close.
        if (neo4j == null) {
            synchronized (_NEO4J_INIT_LOCK) {
                if (neo4j == null) {
                    String neoUri  = envOr("SPRING_NEO4J_URI",                     "bolt://localhost:7687");
                    String neoUser = envOr("SPRING_NEO4J_AUTHENTICATION_USERNAME", "neo4j");
                    String neoPass = envOr("SPRING_NEO4J_AUTHENTICATION_PASSWORD", "neo4j");
                    try {
                        neo4j = org.neo4j.driver.GraphDatabase.driver(
                            neoUri,
                            org.neo4j.driver.AuthTokens.basic(neoUser, neoPass));
                        neo4j.verifyConnectivity();  // fail-fast on bad creds
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { neo4j.close(); } catch (Exception ignored) {}
                        }, "m2-tests-neo4j-close"));
                    } catch (Exception ignored) {
                        neo4j = null;
                    }
                }
            }
        }

        // ── Redis (soft, JVM-singleton) ──────────────────────────────
        // Jedis is single-connection and NOT thread-safe. Singleton works
        // under surefire's default serial execution; revisit if parallel
        // tests are ever enabled (use JedisPool then). Shutdown hook
        // closes the connection on JVM exit.
        if (redis == null) {
            synchronized (_REDIS_INIT_LOCK) {
                if (redis == null) {
                    String redisHost = envOr("SPRING_DATA_REDIS_HOST", "localhost");
                    int    redisPort = Integer.parseInt(envOr("SPRING_DATA_REDIS_PORT", "6379"));
                    String redisPass = envOr("SPRING_DATA_REDIS_PASSWORD", "");
                    try {
                        redis = new redis.clients.jedis.Jedis(redisHost, redisPort);
                        if (!redisPass.isBlank()) {
                            redis.auth(redisPass);
                        }
                        redis.ping();
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { redis.close(); } catch (Exception ignored) {}
                        }, "m2-tests-redis-close"));
                    } catch (Exception e) {
                        System.err.println("[TestBase] Redis init FAILED at " + redisHost + ":" + redisPort
                            + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        Throwable cause = e.getCause();
                        while (cause != null) {
                            System.err.println("[TestBase]   caused by: " + cause.getClass().getName() + ": " + cause.getMessage());
                            cause = cause.getCause();
                        }
                        redis = null;
                    }
                }
            }
        }

        // ── Cassandra (soft, JVM-singleton) ──────────────────────────
        if (cassandra == null) {
            synchronized (_CASSANDRA_INIT_LOCK) {
                if (cassandra == null) {
                    String casHost = envOr("SPRING_CASSANDRA_CONTACT_POINTS", "localhost:9042");
                    String casDc   = envOr("SPRING_CASSANDRA_LOCAL_DATACENTER", "datacenter1");
                    String casKs   = envOr("SPRING_CASSANDRA_KEYSPACE_NAME", "fooddeliveryks");
                    try {
                        String[] hp = casHost.split(":");
                        int port = hp.length > 1 ? Integer.parseInt(hp[1]) : 9042;
                        cassandra = com.datastax.oss.driver.api.core.CqlSession.builder()
                                .addContactPoint(new java.net.InetSocketAddress(hp[0], port))
                                .withLocalDatacenter(casDc)
                                .withKeyspace(casKs)
                                .build();
                        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                            try { cassandra.close(); } catch (Exception ignored) {}
                        }, "m2-tests-cassandra-close"));
                    } catch (Exception ignored) {
                        cassandra = null;
                    }
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Elasticsearch helpers — REST-API based, use the existing `http`
    // client. All return raw HTTP responses; tests parse JSON via
    // {@link #parseNode}. Callers must handle non-2xx (404 means index
    // missing; some tests assert on that).
    // ────────────────────────────────────────────────────────────────

    protected HttpResponse<String> esGet(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(esBaseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> esPost(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(esBaseUrl + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected boolean esDocExists(String index, Object id) throws Exception {
        HttpResponse<String> r = esGet("/" + index + "/_doc/" + id);
        if (r.statusCode() / 100 == 2) return true;
        if (r.statusCode() == 404) return false;
        throw new AssertionError(
                "esDocExists(" + index + "/" + id + "): unexpected status "
                        + r.statusCode() + " body=" + r.body());
    }

    // ────────────────────────────────────────────────────────────────
    // Multi-DB manifest helpers — strict spec-name lookups for Mongo
    // collections and Elasticsearch indices.
    // ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    protected String mongoCollectionByName(String specCollectionName) {
        java.util.List<java.util.Map<String, Object>> colls =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("mongoCollections");
        if (colls != null) {
            for (java.util.Map<String, Object> c : colls) {
                if (specCollectionName.equals(c.get("collectionName"))) return specCollectionName;
            }
        }
        throw new AssertionError(
            "No @Document class maps to MongoDB collection '" + specCollectionName
          + "'. Per the M2 spec, this collection name is mandatory. Rename your "
          + "Mongo entity's annotation to: @Document(collection = \"" + specCollectionName
          + "\"). Make sure the import is "
          + "org.springframework.data.mongodb.core.mapping.Document.");
    }

    @SuppressWarnings("unchecked")
    protected String esIndexByName(String specIndexName) {
        java.util.List<java.util.Map<String, Object>> idxs =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("esIndices");
        if (idxs != null) {
            for (java.util.Map<String, Object> i : idxs) {
                if (specIndexName.equals(i.get("indexName"))) return specIndexName;
            }
        }
        throw new AssertionError(
            "No @Document class maps to Elasticsearch index '" + specIndexName
          + "'. Per the M2 spec, this index name is mandatory. Rename your "
          + "Elasticsearch entity's annotation to: @Document(indexName = \""
          + specIndexName + "\"). Make sure the import is "
          + "org.springframework.data.elasticsearch.annotations.Document.");
    }

    /** Look up the PG column name backing a Java field on an entity. Strict —
     *  throws AssertionError with a remediation hint if the field isn't on the
     *  entity in the manifest. Use this everywhere a test would otherwise
     *  hard-code a column name; the manifest is regenerated per-student at
     *  grading time so a student who declared @JoinColumn(name="userId") gets
     *  the right column name out the other side. */
    @SuppressWarnings("unchecked")
    protected String columnByField(String entityClass, String... fieldNames) {
        // Lenient field-name resolution. The spec mandates the STRUCTURE
        // (entity has a FK to user, entity has a totalAmount column, etc.);
        // it does NOT mandate which exact Java identifier the student picks
        // for the field. Three-layer resolution — tries the spec name(s)
        // first, falls back to the student's actual naming if the spec
        // names don't match anything:
        //
        //   1. EXACT MATCH on any alias
        //   2. Id-SUFFIX FLIP on any alias (user ↔ userId)
        //   3. FUZZY SUBSTRING MATCH (last resort, must be unique)
        //
        // Each candidate is then validated against the actual PG schema —
        // when the manifest scanner emits {fieldName="restaurant",
        // columnName="restaurant"} for a @ManyToOne field that Hibernate
        // actually maps to "restaurant_id" (because @JoinColumn(name=...)
        // wasn't read), the candidate "restaurant" doesn't exist in PG. We
        // then try "restaurant_id" (Hibernate's default FK column suffix)
        // before falling back. Throws a descriptive error only if NONE of
        // the layers find a column the DB actually has.
        for (String fieldName : fieldNames) {
            for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
                if (fieldName.equals(col.get("fieldName"))) {
                    String c = (String) col.get("columnName");
                    if (c != null) {
                        String resolved = _validateOrFlipFk(entityClass, c);
                        if (resolved != null) return resolved;
                    }
                }
            }
        }
        for (String fieldName : fieldNames) {
            String alt = fieldName.endsWith("Id") && fieldName.length() > 2
                    ? fieldName.substring(0, fieldName.length() - 2)
                    : fieldName + "Id";
            for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
                if (alt.equals(col.get("fieldName"))) {
                    String c = (String) col.get("columnName");
                    if (c != null) {
                        String resolved = _validateOrFlipFk(entityClass, c);
                        if (resolved != null) return resolved;
                    }
                }
            }
        }
        java.util.LinkedHashSet<String> fuzzy = new java.util.LinkedHashSet<>();
        for (java.util.Map<String, Object> col : entityColumns(entityClass)) {
            String fname = (String) col.get("fieldName");
            if (fname == null) continue;
            String fnameLc = fname.toLowerCase();
            for (String alias : fieldNames) {
                String aLc = alias.toLowerCase();
                if (fnameLc.contains(aLc) || aLc.contains(fnameLc)) {
                    String c = (String) col.get("columnName");
                    if (c != null) fuzzy.add(c);
                    break;
                }
            }
        }
        for (String c : fuzzy) {
            String resolved = _validateOrFlipFk(entityClass, c);
            if (resolved != null) return resolved;
        }
        if (fuzzy.size() == 1) return fuzzy.iterator().next();
        throw new AssertionError(
            "Entity '" + entityClass + "' has no field matching any of "
          + java.util.Arrays.toString(fieldNames) + " (also tried Id-suffix flips "
          + "and fuzzy substring match). Fuzzy candidates: " + fuzzy + ". "
          + "Per the M2 spec this field is mandatory. Re-check your @Entity declaration.");
    }

    /** Cache: table → set of actual column names from information_schema. */
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.Set<String>>
        _COLUMN_NAMES_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private java.util.Set<String> _columnsOf(String table) {
        return _COLUMN_NAMES_CACHE.computeIfAbsent(table, t -> {
            try {
                return new java.util.HashSet<>(jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                  + "WHERE table_schema = 'public' AND table_name = ?",
                    String.class, t));
            } catch (DataAccessException e) {
                return java.util.Collections.emptySet();
            }
        });
    }

    /** Post-resolution validator: if the manifest's columnName doesn't exist
     *  in the actual table (typical when the scanner missed @JoinColumn), try
     *  Hibernate's default FK column suffix `<col>_id`. Returns the column
     *  the DB actually has, or null if neither candidate exists.
     *
     *  Why: the L0 scanner reads @Column(name=...) but does NOT always read
     *  @JoinColumn(name=...) on @ManyToOne fields. A student with
     *  `@ManyToOne @JoinColumn(name="restaurant_id") Restaurant restaurant`
     *  ends up in the manifest as {fieldName="restaurant", columnName="restaurant"}
     *  but PG actually has the column as "restaurant_id". This validator
     *  catches that case and remaps. */
    private String _validateOrFlipFk(String entityClass, String candidate) {
        if (candidate == null) return null;
        String table;
        try { table = tableName(entityClass); } catch (Exception e) { return candidate; }
        if (table == null) return candidate;
        java.util.Set<String> cols = _columnsOf(table);
        if (cols.isEmpty()) return candidate;          // schema unreadable; trust manifest
        if (cols.contains(candidate)) return candidate;
        String withId = candidate + "_id";
        if (cols.contains(withId)) return withId;
        return null;                                    // caller will keep searching aliases
    }

    /** Set a single timestamp/date value on every "date column" of `table` (e.g.,
     *  order_date, created_at, transaction_date — but NOT updated_at, which is
     *  Hibernate-managed). Discovers existing date columns via
     *  information_schema.columns. Throws AssertionError if the table has no
     *  date/timestamp columns at all — date-range filters can't possibly work
     *  in that case, so the test cannot proceed. */
    protected void setAllDateColumns(String table, long rowId, java.sql.Timestamp ts) {
        java.util.List<String> dateCols;
        try {
            dateCols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
              + "WHERE table_schema='public' AND table_name=? "
              + "  AND data_type IN ('timestamp without time zone','timestamp with time zone','date') "
              + "  AND column_name <> 'updated_at'",
                String.class, table);
        } catch (DataAccessException e) {
            throw new AssertionError("setAllDateColumns: failed to read information_schema for '"
                + table + "': " + e.getMessage(), e);
        }
        if (dateCols.isEmpty()) {
            throw new AssertionError(
                "Table '" + table + "' has no date/timestamp column (excluding updated_at). "
              + "S3-F10 dashboard date-range filtering needs an orderDate or createdAt column. "
              + "Add @Column private LocalDateTime orderDate; or rely on the baseline createdAt.");
        }
        for (String col : dateCols) {
            jdbc.update("UPDATE \"" + table + "\" SET \"" + col + "\" = ? WHERE id = ?", ts, rowId);
        }
    }

    /** Look up a Neo4j node label by its SPEC-DEFINED name. Strict — throws
     *  AssertionError with a remediation hint if the student's @Node doesn't
     *  declare that label. */
    @SuppressWarnings("unchecked")
    protected String neo4jLabelByName(String specLabel) {
        java.util.List<java.util.Map<String, Object>> nodes =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("neo4jNodes");
        if (nodes != null) {
            for (java.util.Map<String, Object> n : nodes) {
                if (specLabel.equals(n.get("label"))) return specLabel;
            }
        }
        throw new AssertionError(
            "No @Node class declares Neo4j label '" + specLabel
          + "'. Per the M2 spec, this label is mandatory for the recommendation "
          + "graph. Annotate your Neo4j entity with: @Node(\"" + specLabel
          + "\"). Make sure the import is "
          + "org.springframework.data.neo4j.core.schema.Node.");
    }

    /** Look up a Cassandra time-series table by its SPEC-DEFINED name. Strict —
     *  throws AssertionError with a remediation hint if no @Table class declares
     *  that table name. */
    @SuppressWarnings("unchecked")
    protected String cassandraTableByName(String specTableName) {
        java.util.List<java.util.Map<String, Object>> tables =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
        if (tables != null) {
            for (java.util.Map<String, Object> t : tables) {
                if (specTableName.equals(t.get("tableName"))) return specTableName;
            }
        }
        throw new AssertionError(
            "No Spring Data Cassandra @Table class declares table '" + specTableName
          + "'. Per the M2 spec, this table name is mandatory for the time-series tracking. "
          + "Annotate your Cassandra entity with: @Table(\"" + specTableName
          + "\"). Make sure the import is "
          + "org.springframework.data.cassandra.core.mapping.Table.");
    }

    /** Look up the Cassandra column name backing a Java field on a time-series
     *  table. Strict — throws AssertionError if the field isn't on the table
     *  in the manifest. Mirrors {@link #columnByField} but for cassandraTables. */
    @SuppressWarnings("unchecked")
    protected String cassandraColumnByField(String tableClassName, String fieldName) {
        java.util.List<java.util.Map<String, Object>> tables =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
        if (tables != null) {
            for (java.util.Map<String, Object> t : tables) {
                if (!tableClassName.equals(t.get("className"))) continue;
                java.util.List<java.util.Map<String, Object>> cols =
                    (java.util.List<java.util.Map<String, Object>>) t.get("columns");
                if (cols == null) break;
                for (java.util.Map<String, Object> col : cols) {
                    if (fieldName.equals(col.get("fieldName"))) {
                        String c = (String) col.get("columnName");
                        if (c != null) return c;
                    }
                }
            }
        }
        throw new AssertionError(
            "Cassandra table class '" + tableClassName + "' has no field '" + fieldName
          + "' in the manifest. Per the M2 spec this field is mandatory.");
    }

    /** Look up the Cassandra @Table class name (Java class) corresponding to
     *  a SPEC-DEFINED table name. Used to chain into cassandraColumnByField. */
    @SuppressWarnings("unchecked")
    protected String cassandraTableClassByName(String specTableName) {
        java.util.List<java.util.Map<String, Object>> tables =
            (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
        if (tables != null) {
            for (java.util.Map<String, Object> t : tables) {
                if (specTableName.equals(t.get("tableName"))) return (String) t.get("className");
            }
        }
        throw new AssertionError(
            "No Cassandra @Table class declares table name '" + specTableName + "'.");
    }

    // ────────────────────────────────────────────────────────────────
    // Neo4j helpers — bolt-driver based. Tests verify recommendation
    // graph state (User → catalog ORDERED_FROM edges, idempotency).
    // All return null/-1/false on null driver so individual tests can
    // throw a clear "Neo4j required" assertion.
    // ────────────────────────────────────────────────────────────────

    /** Run a write/read Cypher statement, return all records as a list of
     *  Map (column → value). Returns empty list if driver is null. */
    @SuppressWarnings("unchecked")
    protected java.util.List<java.util.Map<String, Object>> neo4jExec(
            String cypher, java.util.Map<String, Object> params) {
        if (neo4j == null) return java.util.List.of();
        try (org.neo4j.driver.Session s = neo4j.session()) {
            org.neo4j.driver.Result r = (params == null || params.isEmpty())
                ? s.run(cypher)
                : s.run(cypher, params);
            java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
            while (r.hasNext()) out.add(r.next().asMap());
            return out;
        }
    }

    protected java.util.List<java.util.Map<String, Object>> neo4jExec(String cypher) {
        return neo4jExec(cypher, java.util.Map.of());
    }

    /** Wipe the entire graph (nodes + relationships). Called from
     *  autoTruncateAllData() so each test starts from an empty graph. */
    protected void neo4jClear() {
        if (neo4j == null) return;
        try { neo4jExec("MATCH (n) DETACH DELETE n"); } catch (Exception ignored) { }
    }

    /** Count nodes with a given label and id property. Returns -1 if driver is null. */
    protected long neo4jNodeCount(String label, long id) {
        if (neo4j == null) return -1L;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (n:`" + label + "` {id: $id}) RETURN count(n) AS c",
            java.util.Map.of("id", id));
        if (rows.isEmpty()) return 0L;
        Object c = rows.get(0).get("c");
        return c instanceof Number n ? n.longValue() : 0L;
    }

    /** Returns true if a relationship of given type exists between two
     *  labelled-id nodes. Returns false if driver is null. */
    protected boolean neo4jRelExists(String fromLabel, long fromId,
                                     String relType,
                                     String toLabel,   long toId) {
        if (neo4j == null) return false;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (a:`" + fromLabel + "` {id: $fromId})-[r:`" + relType
          + "`]->(b:`" + toLabel + "` {id: $toId}) RETURN count(r) AS c",
            java.util.Map.of("fromId", fromId, "toId", toId));
        if (rows.isEmpty()) return false;
        Object c = rows.get(0).get("c");
        return c instanceof Number n && n.longValue() > 0L;
    }

    /** Read the orderCount property on the first matching ORDERED_FROM-style
     *  relationship between (fromLabel:fromId) and (toLabel:toId). Returns
     *  -1 if no edge or driver is null. */
    protected long neo4jRelOrderCount(String fromLabel, long fromId,
                                      String relType,
                                      String toLabel,   long toId) {
        if (neo4j == null) return -1L;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (a:`" + fromLabel + "` {id: $fromId})-[r:`" + relType
          + "`]->(b:`" + toLabel + "` {id: $toId}) RETURN r.orderCount AS oc LIMIT 1",
            java.util.Map.of("fromId", fromId, "toId", toId));
        if (rows.isEmpty()) return -1L;
        Object oc = rows.get(0).get("oc");
        return oc instanceof Number n ? n.longValue() : -1L;
    }

    /** Read a string-typed relationship property (e.g. "lastOrderDate" if
     *  stored as ISO string). Returns null if no edge or property missing. */
    protected String neo4jRelStringProp(String fromLabel, long fromId,
                                        String relType,
                                        String toLabel,   long toId,
                                        String propName) {
        if (neo4j == null) return null;
        java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
            "MATCH (a:`" + fromLabel + "` {id: $fromId})-[r:`" + relType
          + "`]->(b:`" + toLabel + "` {id: $toId}) RETURN r.`" + propName
          + "` AS v LIMIT 1",
            java.util.Map.of("fromId", fromId, "toId", toId));
        if (rows.isEmpty()) return null;
        Object v = rows.get(0).get("v");
        return v == null ? null : v.toString();
    }

    // ────────────────────────────────────────────────────────────────
    // Redis helpers — Jedis client. Tests verify cache key TTL after
    // endpoint calls. Values may be Spring-serialized binary / JSON;
    // tests should use redisExists / redisTtl rather than redisGet for
    // reliable assertions.
    // ────────────────────────────────────────────────────────────────

    /** Wipe the current Redis DB — called from autoTruncateAllData() so each
     *  test starts with an empty cache. */
    protected void redisFlushDb() {
        if (redis == null) return;
        try { redis.flushDB(); } catch (Exception ignored) { }
    }

    /** Match keys via a glob pattern (e.g., "*", "dashboard*").
     *  Returns empty set if driver is null. */
    protected java.util.Set<String> redisKeys(String pattern) {
        if (redis == null) return java.util.Set.of();
        try { return redis.keys(pattern); }
        catch (Exception e) { return java.util.Set.of(); }
    }

    /** TTL in seconds. -1 = key has no expiry; -2 = key doesn't exist;
     *  -3 = driver null. */
    protected long redisTtl(String key) {
        if (redis == null) return -3L;
        try { return redis.ttl(key); }
        catch (Exception e) { return -2L; }
    }

    /** True if the key currently exists in Redis. */
    protected boolean redisExists(String key) {
        if (redis == null) return false;
        try { return redis.exists(key); }
        catch (Exception e) { return false; }
    }

    /** Raw value lookup (string-encoded). May be Spring-serialized binary
     *  on the wire, in which case the returned string is best-effort and
     *  may contain non-printable characters — comparisons should be on
     *  exact equality, not parsing. */
    protected String redisGet(String key) {
        if (redis == null) return null;
        try { return redis.get(key); }
        catch (Exception e) { return null; }
    }

    // ────────────────────────────────────────────────────────────────
    // Cassandra helpers — DataStax driver. S4-F11 verifies tracking-event
    // writes; S4-F12 verifies clustering-ordered reads. Tests must guard
    // with a null check on `cassandra` and fail with a clear "Cassandra
    // required" message.
    // ────────────────────────────────────────────────────────────────

    /** Run a CQL statement with positional bind values. Returns null on
     *  driver-null. */
    protected com.datastax.oss.driver.api.core.cql.ResultSet cassandraExec(String cql, Object... params) {
        if (cassandra == null) return null;
        return cassandra.execute(
            com.datastax.oss.driver.api.core.cql.SimpleStatement.newInstance(cql, params));
    }

    /** Schema-aware Cassandra insert. Walks {@code system_schema.columns} for
     *  the table; for each column:
     *    - If overrides contains a non-null value → use it.
     *    - If column is part of the primary key (partition or clustering) and
     *      no override was provided → throw, because Cassandra cannot insert
     *      a row without a complete PK.
     *    - Else → skip (Cassandra columns outside the PK are nullable).
     *
     *  Mirrors {@link #insertRowReturningId} for PG. No "auto-fill NOT NULL"
     *  for non-PK columns because Cassandra has no NOT NULL constraint on
     *  regular columns — they default to null. The test author decides what
     *  values matter for the assertion. */
    @SuppressWarnings("unchecked")
    protected void cassandraInsertRow(String table, java.util.Map<String, Object> overrides) {
        if (cassandra == null) return;
        String keyspace = cassandra.getKeyspace().map(ks -> ks.asInternal()).orElse(null);
        if (keyspace == null) {
            throw new AssertionError("cassandraInsertRow: session has no keyspace bound");
        }
        com.datastax.oss.driver.api.core.cql.ResultSet rs = cassandra.execute(
            com.datastax.oss.driver.api.core.cql.SimpleStatement.newInstance(
                "SELECT column_name, kind FROM system_schema.columns "
              + "WHERE keyspace_name = ? AND table_name = ?",
                keyspace, table));
        java.util.LinkedHashMap<String, String> kind = new java.util.LinkedHashMap<>();
        for (com.datastax.oss.driver.api.core.cql.Row r : rs) {
            kind.put(r.getString("column_name"), r.getString("kind"));
        }
        if (kind.isEmpty()) {
            throw new AssertionError("cassandraInsertRow: table '" + table
                + "' not found in keyspace '" + keyspace + "'");
        }
        StringBuilder cols  = new StringBuilder();
        StringBuilder marks = new StringBuilder();
        java.util.List<Object> params = new java.util.ArrayList<>();
        boolean first = true;
        for (java.util.Map.Entry<String, String> e : kind.entrySet()) {
            String name = e.getKey();
            String k    = e.getValue();
            boolean isPk = "partition_key".equals(k) || "clustering".equals(k);
            Object v;
            if (overrides != null && overrides.containsKey(name)) {
                v = overrides.get(name);
                if (v == null) {
                    if (isPk) {
                        throw new AssertionError("cassandraInsertRow: PK column '" + name
                            + "' on " + table + " cannot be null (kind=" + k + ")");
                    }
                    continue;
                }
            } else if (isPk) {
                throw new AssertionError("cassandraInsertRow: PK column '" + name
                    + "' on " + table + " missing from overrides (kind=" + k + "). "
                    + "Cassandra requires a complete PK on every insert.");
            } else {
                continue;                              // non-PK column: leave null
            }
            if (!first) { cols.append(", "); marks.append(", "); }
            first = false;
            cols.append("\"").append(name).append("\"");
            marks.append("?");
            params.add(v);
        }
        cassandraExec(
            "INSERT INTO \"" + table + "\" (" + cols + ") VALUES (" + marks + ")",
            params.toArray());
    }

    /** Truncate a Cassandra table (keyspace-qualified is unnecessary —
     *  session is bound to the keyspace). Called from autoTruncateAllData()
     *  so each test starts with an empty time-series. */
    protected void cassandraClear(String tableName) {
        if (cassandra == null) return;
        try { cassandra.execute("TRUNCATE TABLE \"" + tableName + "\""); }
        catch (Exception ignored) { }
    }

    /** Count rows in a partition (e.g., all tracking events for one delivery).
     *  Returns -1 if driver is null. */
    protected long cassandraCount(String tableName, String partitionCol, Object partitionVal) {
        if (cassandra == null) return -1L;
        com.datastax.oss.driver.api.core.cql.ResultSet rs =
            cassandraExec("SELECT count(*) FROM \"" + tableName + "\" WHERE \"" + partitionCol + "\" = ?", partitionVal);
        com.datastax.oss.driver.api.core.cql.Row row = rs == null ? null : rs.one();
        return row == null ? 0L : row.getLong(0);
    }

    /** Read all rows for a partition (returns most-recent-first per the
     *  table's clustering order, which spec mandates as DESC by event-time).
     *  Each row is exposed as a Map<columnName, value>. Returns empty list
     *  on driver-null. */
    protected java.util.List<java.util.Map<String, Object>> cassandraRows(
            String tableName, String partitionCol, Object partitionVal) {
        if (cassandra == null) return java.util.List.of();
        com.datastax.oss.driver.api.core.cql.ResultSet rs = cassandraExec(
            "SELECT * FROM \"" + tableName + "\" WHERE \"" + partitionCol + "\" = ?", partitionVal);
        if (rs == null) return java.util.List.of();
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (com.datastax.oss.driver.api.core.cql.Row r : rs) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            com.datastax.oss.driver.api.core.cql.ColumnDefinitions defs = r.getColumnDefinitions();
            for (int i = 0; i < defs.size(); i++) {
                String col = defs.get(i).getName().asInternal();
                m.put(col, r.getObject(i));
            }
            out.add(m);
        }
        return out;
    }

    /**
     * Search for documents in an ES index where a field exactly matches
     * a value, return the hit count. Robust to mapping variations where
     * ES _id is auto-generated rather than equal to the PG entity id.
     * Returns -1 on transport / parsing failures, 0..N otherwise.
     */
    protected long esSearchCount(String index, String fieldName, String fieldValue) throws Exception {
        // Try keyword sub-field first (explicit mapping with .keyword)
        String body = String.format(
                "{\"query\":{\"term\":{\"%s.keyword\":{\"value\":\"%s\"}}}}",
                fieldName, fieldValue);
        HttpResponse<String> r = esPost("/" + index + "/_search", body);
        if (r.statusCode() / 100 == 2) {
            long count = esParseCount(r.body());
            if (count > 0) return count;
        }
        // Fallback: match_phrase works on analyzed text fields (dynamic mapping without .keyword)
        body = String.format(
                "{\"query\":{\"match_phrase\":{\"%s\":\"%s\"}}}",
                fieldName, fieldValue);
        r = esPost("/" + index + "/_search", body);
        if (r.statusCode() / 100 != 2) return -1L;
        return esParseCount(r.body());
    }

    private long esParseCount(String responseBody) {
        try {
            JsonNode j = parseNode(responseBody);
            if (j.has("hits") && j.get("hits").has("total")) {
                JsonNode total = j.get("hits").get("total");
                if (total.isObject() && total.has("value")) return total.get("value").asLong();
                if (total.isNumber()) return total.asLong();
            }
        } catch (Exception ignored) { }
        return -1L;
    }

    // ────────────────────────────────────────────────────────────────
    // HTTP helpers
    //
    // BASE_URL is the URL for the test currently running. Each test
    // method sets BASE_URL on its first line to one of the per-service
    // URL fields below — userServiceUrl, catalogServiceUrl,
    // orderServiceUrl, deliveryServiceUrl, checkoutServiceUrl — picked
    // per the section comment above the test class's @Tag (e.g.
    // "TC54 — S3-F10 …" → orderServiceUrl).
    //
    // Auth helpers (adminToken, seedAndLoginUser) ALWAYS hit
    // userServiceUrl regardless of the test's primary service.
    // ────────────────────────────────────────────────────────────────

    protected HttpResponse<String> httpGet(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpGetAuth(String path, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPost(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPostAuth(String path, String jsonBody, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPut(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPutAuth(String path, String jsonBody, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPatch(String path, String jsonBody) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpPatchAuth(String path, String jsonBody, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpDelete(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> httpDeleteAuth(String path, String token) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", "Bearer " + token)
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * GET with an arbitrary Authorization header value — useful for malformed-
     * scheme / empty-Bearer negative tests that need to send something other
     * than "Bearer <valid-jwt>".
     */
    protected HttpResponse<String> httpGetWithRawAuth(String path, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", authHeaderValue)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** POST + custom Authorization header. */
    protected HttpResponse<String> httpPostWithRawAuth(String path, String jsonBody, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeaderValue)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    /** PUT + custom Authorization header. */
    protected HttpResponse<String> httpPutWithRawAuth(String path, String jsonBody, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeaderValue)
                        .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** PATCH + custom Authorization header. */
    protected HttpResponse<String> httpPatchWithRawAuth(String path, String jsonBody, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Content-Type", "application/json")
                        .header("Authorization", authHeaderValue)
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** DELETE + custom Authorization header. */
    protected HttpResponse<String> httpDeleteWithRawAuth(String path, String authHeaderValue) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(BASE_URL + path))
                        .header("Authorization", authHeaderValue)
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    // ────────────────────────────────────────────────────────────────
    // JSON helpers
    // ────────────────────────────────────────────────────────────────

    protected Map<String, Object> parseMap(String json) throws Exception {
        return OM.readValue(json, new TypeReference<>() {});
    }

    protected List<Map<String, Object>> parseList(String json) throws Exception {
        return OM.readValue(json, new TypeReference<>() {});
    }

    protected JsonNode parseNode(String json) throws Exception {
        return OM.readTree(json);
    }

    protected String toJson(Object o) throws Exception {
        return OM.writeValueAsString(o);
    }

    /** Decode the unsigned payload of a JWT (header.payload.signature) and
     *  return it as a JsonNode. Spec-compliant per Talabat M2 §5.2 — the
     *  token must carry `sub` (email), `uid` (numeric User.id), `role`,
     *  `iat`, `exp`. We do NOT verify the signature; this is for reading
     *  the user's id post-register/login when the spec response body is
     *  {token, expiresIn} (no `id` field). */
    protected JsonNode decodeJwtPayload(String token) throws Exception {
        if (token == null) throw new AssertionError("decodeJwtPayload: token is null");
        String[] parts = token.split("\\.", -1);
        if (parts.length < 2) {
            throw new AssertionError("decodeJwtPayload: not a JWT (no '.' separator): " + token);
        }
        byte[] payloadBytes = java.util.Base64.getUrlDecoder().decode(parts[1]);
        return OM.readTree(payloadBytes);
    }

    /** Convenience: extract the spec-mandated `uid` claim from a JWT.
     *  Falls back to `sub` if `uid` missing AND sub looks numeric (defensive
     *  cross-theme tolerance). Throws AssertionError if neither yields a Long. */
    protected long uidFromJwt(String token) throws Exception {
        JsonNode payload = decodeJwtPayload(token);
        if (payload.has("uid") && !payload.get("uid").isNull()) {
            return payload.get("uid").asLong();
        }
        if (payload.has("sub")) {
            String sub = payload.get("sub").asText();
            try { return Long.parseLong(sub); } catch (NumberFormatException ignored) { }
        }
        throw new AssertionError(
            "JWT has no `uid` claim and `sub` is not numeric. Spec (M2 §5.2) "
          + "mandates `uid` containing User.id. Payload: " + payload);
    }

    // ────────────────────────────────────────────────────────────────
    // PostgreSQL enum resolution helpers
    //
    // Enums on the student's entities are stored as native PG ENUM types
    // (per feedback_enum_storage: @JdbcTypeCode(NAMED_ENUM)). The type
    // name varies per theme and sometimes per student, so tests must
    // discover it at runtime.
    // ────────────────────────────────────────────────────────────────

    /** Find the PG enum TYPE name whose labels include every given value. */
    protected String findEnumType(String... labels) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT t.typname FROM pg_type t JOIN pg_namespace n ON t.typnamespace = n.oid "
                  + "WHERE n.nspname = 'public' AND t.typtype = 'e'");
            for (String label : labels) {
                sql.append(" AND EXISTS (SELECT 1 FROM pg_enum e WHERE e.enumtypid = t.oid AND e.enumlabel = '")
                   .append(label).append("')");
            }
            sql.append(" LIMIT 1");
            return jdbc.queryForObject(sql.toString(), String.class);
        } catch (DataAccessException e) {
            return null;
        }
    }

    /** Like {@link #findEnumType} but returns {@code VARCHAR(50)} as a VARCHAR fallback. */
    protected String resolveColType(String... labels) {
        String t = findEnumType(labels);
        return t != null ? t : "VARCHAR(50)";
    }

    /** Return a JDBC placeholder with the correct type cast for the column. */
    protected String ec(String table, String col) {
        try {
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND column_name = ?",
                    String.class, table, col);
            if (udt != null && !udt.equals("varchar") && !udt.equals("text") && !udt.startsWith("int")) {
                return "?::" + udt;
            }
        } catch (DataAccessException ignored) { }
        return "?";
    }

    /** Render a literal enum value correctly cast for the column. */
    protected String el(String table, String col, String value) {
        try {
            String udt = jdbc.queryForObject(
                    "SELECT udt_name FROM information_schema.columns "
                  + "WHERE table_name = ? AND column_name = ?",
                    String.class, table, col);
            if (udt != null && !udt.equals("varchar") && !udt.equals("text") && !udt.startsWith("int")) {
                return "'" + value + "'::" + udt;
            }
        } catch (DataAccessException ignored) { }
        return "'" + value + "'";
    }

    // ────────────────────────────────────────────────────────────────
    // Misc
    // ────────────────────────────────────────────────────────────────

    protected static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** Unique-ish suffix based on nanoTime, good enough to avoid cross-test collisions.
     *  Guaranteed to be at least 9 characters so callers can safely
     *  call .substring(0, 9) for phone-suffix construction etc.
     *  System.nanoTime() can return values short enough that
     *  String.valueOf(...).substring(6) yields <9 chars (observed in
     *  fresh-JVM Docker runs as "Range [0, 9) out of bounds for length 7").
     *  Pad on the left first if nanoTime itself is <7 chars, then pad the
     *  tail on the right with zeros so every callsite stays valid. */
    protected static String nonce() {
        String s = String.valueOf(System.nanoTime());
        if (s.length() < 7) {
            s = "0".repeat(7 - s.length()) + s;
        }
        String tail = s.substring(6);
        if (tail.length() < 9) {
            tail = tail + "0".repeat(9 - tail.length());
        }
        return tail;
    }

    protected void assert2xx(HttpResponse<String> r, String ctx) {
        assertTrue(r.statusCode() >= 200 && r.statusCode() < 300,
                ctx + ": expected 2xx but got " + r.statusCode() + " body=" + r.body());
    }

    // ────────────────────────────────────────────────────────────────
    // Auth helpers — cache an admin token for the lifetime of the test
    // class (PER_CLASS lifecycle) so every @Test in the same class pays
    // the register+login cost at most once.
    // ────────────────────────────────────────────────────────────────

    private String _cachedAdminToken;
    private String _cachedAdminEmail;
    private Long   _cachedAdminId;

    /**
     * Lazily resolve an admin JWT, memoise per test-class.
     *
     * <p>Fast path: log in as the pre-seeded admin that
     * {@link #autoSeedBaselineData()} planted at id=1 with email
     * {@link #_PRESEED_ADMIN_EMAIL} and password {@link #_PRESEED_PASSWORD}.
     * One HTTP call, no second admin in the DB.
     *
     * <p>Fallback: if login fails (student uses non-BCrypt hashing,
     * rejects the pre-seeded row, or has a broken login endpoint), fall
     * back to {@link TestAuthHelper#seedAdmin}, which registers a fresh
     * user via HTTP and promotes via JDBC. Same behaviour as before this
     * rewire, so admin-required tests still resolve a token.
     */
    protected String adminToken() throws Exception {
        if (_cachedAdminToken == null) {
            String loginBody = String.format(
                    "{\"email\":\"%s\",\"password\":\"%s\"}",
                    _PRESEED_ADMIN_EMAIL, _PRESEED_PASSWORD);
            // Auth always hits user-service, regardless of which service the
            // current test exercises. Bypass BASE_URL-based httpPost.
            HttpResponse<String> r = http.send(
                    HttpRequest.newBuilder(URI.create(userServiceUrl + "/api/auth/login"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                    HttpResponse.BodyHandlers.ofString());
            String token = null;
            if (r.statusCode() / 100 == 2) {
                try {
                    JsonNode j = parseNode(r.body());
                    if (j.has("token") && !j.get("token").asText().isBlank()) {
                        token = j.get("token").asText();
                    }
                } catch (Exception ignored) { /* fall through to fallback */ }
            }
            if (token != null) {
                _cachedAdminToken = token;
                _cachedAdminId   = 1L;                  // pre-seeded admin is always id=1
                _cachedAdminEmail = _PRESEED_ADMIN_EMAIL;
            } else {
                _cachedAdminEmail = "admin_" + nonce() + "@testgen.io";
                String _userTable = tableName("User");
                String _roleCol   = columnByField("User", "role");
                String _emailCol  = columnByField("User", "email");
                _cachedAdminId    = TestAuthHelper.seedAdmin(
                        http, jdbc, userServiceUrl, _cachedAdminEmail,
                        _userTable, _roleCol, _emailCol);
                _cachedAdminToken = TestAuthHelper.loginAsAdmin(http, userServiceUrl, _cachedAdminEmail);
            }
        }
        return _cachedAdminToken;
    }

    /** Id of the cached admin; triggers {@link #adminToken} on first call. */
    protected long adminId() throws Exception {
        adminToken();
        return _cachedAdminId;
    }

    /**
     * Register a fresh non-admin user, log them in, return {token, id, email}.
     * Each call creates a new user — use this when a test needs two distinct
     * identities (e.g. ownership / IDOR tests).
     */
    protected Map<String, Object> seedAndLoginUser(String emailPrefix) throws Exception {
        String email = emailPrefix + "_" + nonce() + "@testgen.io";
        String pwd = "UserPwd!2026";
        String phone = "+2010" + nonce().substring(0, 9);
        String regBody = String.format("""
                {"name":"%s","email":"%s","password":"%s","phone":"%s"}
                """, emailPrefix, email, pwd, phone);
        // Auth always hits user-service.
        HttpResponse<String> reg = http.send(
                HttpRequest.newBuilder(URI.create(userServiceUrl + "/api/auth/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(regBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (reg.statusCode() / 100 != 2) {
            // /api/auth/register not public — insert user row directly via JDBC.
            // Pre-computed BCrypt($2b$10) of pwd; compatible with Spring BCryptPasswordEncoder.
            final String BCRYPT_HASH = "$2b$10$QiR2cIYwL.uKEqlGcok/4.US4vlLXopOrlHBIpCfDs1eMrYrmNoIS";
            String uTable = tableName("User");
            String eCol = columnByField("User", "email");
            java.util.List<String> cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = ? AND column_name != 'id' ORDER BY ordinal_position",
                String.class, uTable);
            StringBuilder colSql = new StringBuilder();
            StringBuilder valSql = new StringBuilder();
            java.util.List<Object> params = new java.util.ArrayList<>();
            for (String c : cols) {
                Object val = null; String castType = null;
                switch (c.toLowerCase()) {
                    case "email"                             -> val = email;
                    case "password","password_hash","passwd" -> val = BCRYPT_HASH;
                    case "name","full_name","username"       -> val = emailPrefix;
                    case "phone","phone_number","mobile"     -> val = phone;
                    case "status","user_status"              -> {
                        val = "ACTIVE";
                        castType = TestAuthHelper.discoverEnumTypeForColumn(jdbc, uTable, c);
                    }
                    case "role","user_role"                  -> {
                        val = TestAuthHelper.discoverDefaultRole(jdbc, uTable, c);
                        castType = TestAuthHelper.discoverEnumTypeForColumn(jdbc, uTable, c);
                    }
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
            jdbc.update("INSERT INTO \"" + uTable + "\" (" + colSql + ") VALUES (" + valSql + ")",
                params.toArray());
            // fall through to login — BCrypt hash matches pwd, so login will succeed
        }
        String loginBody = String.format("""
                {"email":"%s","password":"%s"}
                """, email, pwd);
        HttpResponse<String> login = http.send(
                HttpRequest.newBuilder(URI.create(userServiceUrl + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody)).build(),
                HttpResponse.BodyHandlers.ofString());
        assert2xx(login, "seedAndLoginUser login");
        String token = parseNode(login.body()).get("token").asText();
        Map<String, Object> claims = decodeJwtClaims(token);
        long uid = ((Number) claims.get("uid")).longValue();
        return Map.of("token", token, "id", uid, "email", email);
    }

    /**
     * Decode the payload segment of a JWT without verifying the signature.
     * Purpose is test-side inspection only (uid/role claims for assertions
     * or tampered-signature golden construction).
     */
    protected Map<String, Object> decodeJwtClaims(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a 3-segment JWT: " + token);
        }
        byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
        return OM.readValue(payload, new TypeReference<>() {});
    }

    /**
     * Produce a JWT whose signature segment is garbage — same header + payload,
     * tampered signature. The server must reject with 401 without distinguishing
     * "bad sig" from "malformed".
     */
    protected String tamperSignature(String validToken) {
        String[] parts = validToken.split("\\.");
        if (parts.length != 3) {
            return validToken + "tampered";
        }
        String bogus = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("tampered-signature-does-not-verify".getBytes());
        return parts[0] + "." + parts[1] + "." + bogus;
    }


    // ─── auto-truncate hooks (per-test wipe) ───
    //
    // Why: tests that hit /api/<entity>/<id> assume data exists at
    // that id, but the grader DB starts empty. Tests that seed via
    // JDBC leave rows behind, so the NEXT test sees stale state.
    // The fix: wipe row data BEFORE and AFTER every @Test method.
    //
    // Schema discovery (student-friendly):
    //   * We ask the student's own DB which tables exist
    //     (information_schema.tables, public schema). The M2 spec
    //     defines the HTTP contract and entity model, but doesn't
    //     mandate @Table(name=...) values — students may legitimately
    //     name their tables differently. Hardcoding "users" would
    //     skip a student who used "user_account", letting rows
    //     accumulate. Discovery wipes whatever they actually built.
    //   * Migration-tracker tables (Flyway/Liquibase) are skipped so
    //     we don't trigger re-migration on app restart.
    //
    // Safety:
    //   * TRUNCATE ... RESTART IDENTITY CASCADE deletes rows + resets
    //     the id sequence; it does NOT touch the schema, indexes, FKs,
    //     OR PG-native enum types — so enum-typed columns stay intact.
    //   * Per-table try/catch: a single failing TRUNCATE (locked, FK
    //     weirdness) doesn't break the rest of the wipe.
    //   * CASCADE wipes student-added child tables that FK back to a
    //     spec entity, even if we never list them.
    //   * Cached admin token is invalidated so the next adminToken()
    //     call lazily re-seeds the admin row.
    private static final java.util.Set<String> _AUTO_TRUNCATE_SKIP = java.util.Set.of(
        "flyway_schema_history",
        "databasechangelog",
        "databasechangeloglock",
        "schema_version"
    );

    @org.junit.jupiter.api.BeforeEach
    protected void truncateBeforeEach(org.junit.jupiter.api.TestInfo info) {
        routeBaseUrl(info);
        autoTruncateAllData();
        _cachedAdminToken = null;
        _cachedAdminId = null;
        // Baseline seed is now opt-in: only test classes/methods tagged
        // @Tag("with-baseline") get the 50+ row preseed (admin + customers +
        // restaurants + menu items + orders + deliveries + payments + offers).
        // The vast majority of tests seed exactly the rows they need inline,
        // so paying ~1.5s for the baseline on every test was pure overhead
        // (and caused real false positives — e.g., baseline EGYPTIAN restaurant
        // matching a test's narrow filter assertion).
        if (info.getTags().contains("with-baseline")) {
            autoSeedBaselineData();
        }
    }

    /**
     *  Auto-route BASE_URL by test-class prefix. The hand-written M2 SXFXX
     *  test classes don't set BASE_URL per method, so we infer it from the
     *  service the class targets:
     *    S1F* → user-service       (auth, profile, addresses, activity feed)
     *    S2F* → catalog-service    (restaurants / menu items)
     *    S3F* → order-service
     *    S4F* → delivery-service
     *    S5F* → checkout-service   (payments, offers, refunds)
     *    CC*  → user-service       (cross-cutting auth chain tests)
     *    TC*  → user-service       (M1 tests override per method)
     *  Per-method assignments to BASE_URL still win — this only fills the
     *  default so tests don't hit the bogus 8080 fallback.
     */
    private void routeBaseUrl(org.junit.jupiter.api.TestInfo info) {
        String cls = info.getTestClass().map(Class::getSimpleName).orElse("");
        // S{X}F* prefix wins (M1-section pure-logic tests)
        if      (cls.startsWith("S2F")) { BASE_URL = catalogServiceUrl;  return; }
        else if (cls.startsWith("S3F")) { BASE_URL = orderServiceUrl;    return; }
        else if (cls.startsWith("S4F")) { BASE_URL = deliveryServiceUrl; return; }
        else if (cls.startsWith("S5F")) { BASE_URL = checkoutServiceUrl; return; }
        else if (cls.startsWith("S1F") || cls.startsWith("CC")) {
            BASE_URL = userServiceUrl; return;
        }
        // TC{N} prefix fallback — covers any test the agent didn't manually
        // assign BASE_URL in. Range mirrors PublicTests.java sectioning:
        //   TC01-34   user (S1)
        //   TC35-53   catalog (S2)
        //   TC54-99   order   (S3)
        //   TC100-135 delivery (S4)
        //   TC136-190 checkout (S5)
        int tc = -1;
        if (cls.startsWith("TC")) {
            int j = 2;
            while (j < cls.length() && Character.isDigit(cls.charAt(j))) j++;
            try { tc = Integer.parseInt(cls.substring(2, j)); } catch (Exception ignored) {}
        }
        if      (tc >= 1   && tc <= 34)  BASE_URL = userServiceUrl;
        else if (tc >= 35  && tc <= 53)  BASE_URL = catalogServiceUrl;
        else if (tc >= 54  && tc <= 99)  BASE_URL = orderServiceUrl;
        else if (tc >= 100 && tc <= 135) BASE_URL = deliveryServiceUrl;
        else if (tc >= 136 && tc <= 190) BASE_URL = checkoutServiceUrl;
        else                             BASE_URL = userServiceUrl;
    }

    @org.junit.jupiter.api.AfterEach
    protected void truncateAfterEach() {
        autoTruncateAllData();
        _cachedAdminToken = null;
        _cachedAdminId = null;
    }

    private void autoTruncateAllData() {
        if (jdbc == null) return;     // initBase() hasn't run yet
        java.util.List<String> tables;
        try {
            tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
              + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        } catch (DataAccessException e) {
            return;     // DB not ready — let the test's own seed surface the issue
        }
        // Single compound TRUNCATE — PG handles the comma-separated list
        // atomically and ~10× faster than 30 individual round-trips. CASCADE
        // covers any student-added child tables that FK back to spec entities.
        java.util.List<String> wipe = new java.util.ArrayList<>();
        for (String t : tables) {
            if (!_AUTO_TRUNCATE_SKIP.contains(t)) wipe.add("\"" + t + "\"");
        }
        if (!wipe.isEmpty()) {
            try {
                jdbc.execute("TRUNCATE TABLE " + String.join(", ", wipe) + " RESTART IDENTITY CASCADE");
            } catch (DataAccessException compound) {
                // If the compound truncate failed (e.g., one table locked),
                // fall back to per-table so other tables still get wiped.
                for (String t : tables) {
                    if (_AUTO_TRUNCATE_SKIP.contains(t)) continue;
                    try {
                        jdbc.execute("TRUNCATE TABLE \"" + t + "\" RESTART IDENTITY CASCADE");
                    } catch (DataAccessException ignored) { }
                }
            }
        }
        // Also wipe auxiliary stores so each test starts from a clean slate.
        // Soft-fail: if the driver is null (store unreachable) the helpers no-op.
        neo4jClear();
        redisFlushDb();
        // Truncate every Cassandra table declared in the manifest.
        if (cassandra != null) {
            try {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> casTables =
                    (java.util.List<java.util.Map<String, Object>>) manifest().get("cassandraTables");
                if (casTables != null) {
                    for (java.util.Map<String, Object> t : casTables) {
                        String name = (String) t.get("tableName");
                        if (name != null) cassandraClear(name);
                    }
                }
            } catch (Exception ignored) { }
        }
    }
    // ─── end auto-truncate hooks ───

    // ─── auto-seed hooks (per-test baseline data) — Talabat ───
    //
    // Why: tests query /api/<entity>/<id>, /search, /metadata/search,
    // /preferences/dietary, /driver/{name}/summary etc. with an
    // assertion shape like ``assertTrue(family == 2 || code == 404)``
    // — they pass silently when the DB is empty (404 path) and never
    // exercise the 2xx happy path. By pre-seeding rich, FK-wired
    // data that aligns with the URL substitutions every test uses,
    // the 2xx path becomes the actual path.
    //
    // Coverage targets — driven from a static scan of every public
    // test's HTTP URL (tools/scan_test_data_needs.py):
    //
    //   * Users — 5 rows: admin (id=1) + 4 customers. Admin has 1
    //     order so /orders/recommendations?userId=1 finds data. User
    //     id=4 has 5 orders so /users/preferences/dietary?minOrders=5
    //     matches. User id=3 has dietary=VEGETARIAN preferences.
    //   * Restaurants — 4 rows spanning ITALIAN/EGYPTIAN × OPEN/CLOSED
    //     with ratings 3.5..4.8 so minRating/maxRating filters match.
    //   * Orders — 8 rows: dates spread Jan–Feb 2026, statuses spanning
    //     PLACED/DELIVERED/CONFIRMED, total_amount varied for analytics.
    //   * Deliveries — 8 rows: 5 attributed to driver "Hassan"
    //     (matching the S4-F8 spec scenario) with metadata speeds
    //     20/30/40/50/0 per spec, so /deliveries/driver/Hassan/summary
    //     returns totalDeliveries=5, averageSpeed=28, maxSpeed=50.
    //   * Payments — 8 rows with statuses spanning COMPLETED / REFUNDED
    //     / PENDING for /payments/search?status=... filters.
    //   * Offers — 3 rows: PERCENTAGE × 2 + FIXED × 1.
    //   * payment_offers junction — usage spread 4/2/1 across the 3
    //     offers so /payments/offers/top-used?limit=N returns ranked
    //     non-empty data.
    //   * JSONB metadata / details / preferences / transaction_details
    //     — populated with ``{"note":"true",...}`` so
    //     /metadata/search?key=note&value=true returns matches.
    //
    // Robustness:
    //   * Each INSERT/UPDATE in its own try/catch via _tryUpdate() —
    //     if a student renamed a column, omitted an entity, or has a
    //     tighter constraint, that single statement skips and the
    //     rest of the seed continues.
    //   * Optional columns (rating, driver_name, jsonb metadata,
    //     order_date, delivery_address, ...) live in separate UPDATE
    //     statements after the basic INSERT — if the column doesn't
    //     exist, the UPDATE fails silently and the row stays without
    //     that field. Tests that don't depend on the optional field
    //     still work.
    //   * Enum casts via ``el(table, col, value)`` — works against
    //     PG-native enum types and VARCHAR columns interchangeably.
    //
    // BCrypt hash: a known-valid hash whose plaintext we don't track
    // — tests don't try to log in as preseed users (``adminToken()``
    // and ``seedAndLoginUser()`` create their own users with their
    // own passwords). The hash just needs to satisfy the
    // ``^\$2[ayb]\$\d{2}\$`` format check that the M1 amendment test
    // applies to the password column.
    private static final String _PRESEED_BCRYPT =
        "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
    /** Plaintext that {@link #_PRESEED_BCRYPT} encodes (cost-10 BCrypt of "password").
     *  Used by {@link #adminToken()} to log in as the pre-seeded admin without
     *  registering a second admin row. */
    private static final String _PRESEED_PASSWORD    = "password";
    private static final String _PRESEED_ADMIN_EMAIL = "_preseed_admin@grader.testgen.io";
    private static final String _PRESEED_USER_EMAIL  = "_preseed_user@grader.testgen.io";

    /** Run the SQL update; swallow any DB error so one failure doesn't block the rest of the seed. */
    private void _tryUpdate(String sql, Object... params) {
        try { jdbc.update(sql, params); } catch (DataAccessException ignored) { }
    }

    // ─── manifest-aware seed helpers ───
    // Every literal table/column in the seeders below resolves through the
    // student's manifest so a renamed @Entity / @Column still gets seeded.

    /** Soft-resolve a table name from the manifest; null if the entity is missing. */
    private String _tn(String entityClass) {
        try { return tableName(entityClass); } catch (Exception e) { return null; }
    }

    /** Soft-resolve a column name from the manifest; null if no alias matches. */
    private String _cf(String entityClass, String... fieldAliases) {
        try { return columnByField(entityClass, fieldAliases); } catch (Throwable e) { return null; }
    }

    /** Add to the override map iff the column resolved (silently skip otherwise). */
    private static void _put(java.util.Map<String, Object> m, String key, Object value) {
        if (key != null) m.put(key, value);
    }

    /** Run an UPDATE on a single column resolved through the manifest. No-op if entity/field missing. */
    private void _tryUpdateCol(String entity, String[] fieldAliases, Object value, long rowId) {
        String t = _tn(entity);
        String c = _cf(entity, fieldAliases);
        if (t != null && c != null) {
            _tryUpdate("UPDATE \"" + t + "\" SET \"" + c + "\" = ? WHERE id = ?", value, rowId);
        }
    }

    /** Same as {@link #_tryUpdateCol} but casts the value to jsonb. */
    private void _tryUpdateJsonb(String entity, String[] fieldAliases, String json, long rowId) {
        String t = _tn(entity);
        String c = _cf(entity, fieldAliases);
        if (t != null && c != null) {
            _tryUpdate("UPDATE \"" + t + "\" SET \"" + c + "\" = ?::jsonb WHERE id = ?", json, rowId);
        }
    }

    // Path B → fully manifest-driven. Each seeder resolves the table and
    // every column through the student's manifest (tableName / columnByField
    // with aliases that cover spec naming + common student renames) and then
    // delegates to _seedTableRow, which walks information_schema and auto-
    // fills any remaining NOT NULL columns (created_at / applied_at / etc.).
    // Result: students who renamed an @Entity field still get seeded.

    private void _seedUser(String role, String email, String name, String phone) {
        String t = _tn("User"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("User", "name"),     name);
        _put(ov, _cf("User", "email"),    email);
        _put(ov, _cf("User", "phone", "phoneNumber"), phone);
        _put(ov, _cf("User", "password", "passwordHash"), _PRESEED_BCRYPT);
        _put(ov, _cf("User", "role"),     role);
        _put(ov, _cf("User", "status"),   "ACTIVE");
        _seedTableRow(t, ov);
    }

    private void _seedRestaurant(String name, String cuisine, String status) {
        String t = _tn("Restaurant"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("Restaurant", "name"),                              name);
        _put(ov, _cf("Restaurant", "cuisineType", "cuisine", "type"),    cuisine);
        _put(ov, _cf("Restaurant", "status"),                            status);
        _seedTableRow(t, ov);
    }

    private void _seedOrder(long userId, long restaurantId, String status, double amount) {
        String t = _tn("Order"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("Order", "user", "customer"),               userId);
        _put(ov, _cf("Order", "restaurant", "eatery"),           restaurantId);
        _put(ov, _cf("Order", "totalAmount", "amount", "total"), amount);
        _put(ov, _cf("Order", "status"),                         status);
        _seedTableRow(t, ov);
    }

    private void _seedDelivery(long orderId, String status) {
        String t = _tn("Delivery"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("Delivery", "order"),  orderId);
        _put(ov, _cf("Delivery", "status"), status);
        _seedTableRow(t, ov);
    }

    private void _seedPayment(long orderId, long userId, double amount, String status) {
        String t = _tn("Payment"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("Payment", "order"),                        orderId);
        _put(ov, _cf("Payment", "user", "customer"),             userId);
        _put(ov, _cf("Payment", "amount", "totalAmount"),        amount);
        _put(ov, _cf("Payment", "method", "paymentMethod"),      "CREDIT_CARD");
        _put(ov, _cf("Payment", "status"),                       status);
        _seedTableRow(t, ov);
    }

    private void _seedOffer(String code, String type, double value, int maxUses) {
        String t = _tn("Offer"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("Offer", "code"),                               code);
        _put(ov, _cf("Offer", "discountType", "type"),               type);
        _put(ov, _cf("Offer", "discountValue", "value", "amount"),   value);
        _put(ov, _cf("Offer", "maxUses", "maxUsage", "usageLimit"),  maxUses);
        _put(ov, _cf("Offer", "expiryDate", "validUntil"),           java.sql.Date.valueOf("2030-12-31"));
        _seedTableRow(t, ov);
    }

    private void _seedAddress(long userId, String addressLine, String city, boolean isDefault) {
        String t = _tn("Address"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("Address", "user", "customer"),                  userId);
        _put(ov, _cf("Address", "addressLine", "line", "street"),     addressLine);
        _put(ov, _cf("Address", "city"),                              city);
        _put(ov, _cf("Address", "isDefault", "defaultAddress"),       isDefault);
        _seedTableRow(t, ov);
    }

    private void _seedMenuItem(long restaurantId, String name, double price, String status) {
        String t = _tn("MenuItem"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("MenuItem", "restaurant", "eatery"), restaurantId);
        _put(ov, _cf("MenuItem", "name"),                 name);
        _put(ov, _cf("MenuItem", "price"),                price);
        _put(ov, _cf("MenuItem", "status"),               status);
        _seedTableRow(t, ov);
    }

    private void _seedOrderItem(long orderId, long menuItemId, int quantity, double price, String status) {
        String t = _tn("OrderItem"); if (t == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        _put(ov, _cf("OrderItem", "order"),                     orderId);
        _put(ov, _cf("OrderItem", "menuItem", "item", "dish"),  menuItemId);
        _put(ov, _cf("OrderItem", "quantity"),                  quantity);
        _put(ov, _cf("OrderItem", "price"),                     price);
        _put(ov, _cf("OrderItem", "status"),                    status);
        _seedTableRow(t, ov);
    }

    /** payment_offers junction. Discovered by walking information_schema for a
     *  table that has BOTH a payment FK column and an offer FK column —
     *  resilient to students naming the join table payments_offers,
     *  payment_offer, etc. */
    private void _seedPaymentOffer(long paymentId, long offerId) {
        String pTable = _tn("Payment");
        String oTable = _tn("Offer");
        if (pTable == null || oTable == null) return;
        String junction = _findJoinTable(pTable, oTable);
        if (junction == null) return;
        String pCol = _findFkColumn(junction, pTable);
        String oCol = _findFkColumn(junction, oTable);
        if (pCol == null || oCol == null) return;
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(pCol, paymentId);
        ov.put(oCol, offerId);
        _seedTableRow(junction, ov);
    }

    /** Find a join table that has FKs to both side-tables. Returns null if none. */
    private String _findJoinTable(String tableA, String tableB) {
        try {
            return jdbc.queryForObject(
                "SELECT a.table_name FROM ( "
              + "  SELECT tc.table_name, ccu.table_name AS ref "
              + "  FROM information_schema.table_constraints tc "
              + "  JOIN information_schema.constraint_column_usage ccu "
              + "    ON tc.constraint_name = ccu.constraint_name "
              + "  WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' "
              + ") a JOIN ( "
              + "  SELECT tc.table_name, ccu.table_name AS ref "
              + "  FROM information_schema.table_constraints tc "
              + "  JOIN information_schema.constraint_column_usage ccu "
              + "    ON tc.constraint_name = ccu.constraint_name "
              + "  WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' "
              + ") b ON a.table_name = b.table_name "
              + "WHERE a.ref = ? AND b.ref = ? AND a.table_name <> a.ref AND a.table_name <> b.ref "
              + "LIMIT 1",
                String.class, tableA, tableB);
        } catch (DataAccessException e) { return null; }
    }

    /** Find the FK column on `junction` that references `refTable`. Returns null if none. */
    private String _findFkColumn(String junction, String refTable) {
        try {
            return jdbc.queryForObject(
                "SELECT kcu.column_name "
              + "FROM information_schema.table_constraints tc "
              + "JOIN information_schema.key_column_usage kcu "
              + "  ON tc.constraint_name = kcu.constraint_name "
              + "JOIN information_schema.constraint_column_usage ccu "
              + "  ON tc.constraint_name = ccu.constraint_name "
              + "WHERE tc.constraint_type = 'FOREIGN KEY' "
              + "  AND tc.table_schema = 'public' "
              + "  AND tc.table_name = ? AND ccu.table_name = ? "
              + "LIMIT 1",
                String.class, junction, refTable);
        } catch (DataAccessException e) { return null; }
    }

    private void autoSeedBaselineData() {
        if (jdbc == null) return;

        // ── Users (5) ─────────────────────────────────────────────
        // id=1 admin; id=2..5 customers; id=3 vegetarian; id=4 active
        _seedUser("ADMIN",    _PRESEED_ADMIN_EMAIL,                "Preseed Admin",  "+201000000001");
        _seedUser("CUSTOMER", _PRESEED_USER_EMAIL,                 "Preseed User",   "+201000000002");
        _seedUser("CUSTOMER", "_preseed_veg@grader.testgen.io",    "Vegetarian Usr", "+201000000003");
        _seedUser("CUSTOMER", "_preseed_active@grader.testgen.io", "Active User",    "+201000000004");
        _seedUser("CUSTOMER", "_preseed_extra@grader.testgen.io",  "Extra User",     "+201000000005");
        // Optional preferences (jsonb) — covers /preferences/dietary?diet=VEGETARIAN.
        // Admin (id=1) gets the rich profile so tests querying /users/1 find data.
        String _userPrefs = "{\"dietary\":\"VEGETARIAN\",\"language\":\"en\",\"note\":\"true\"}";
        String[] _prefAliases = {"preferences", "userPreferences", "settings"};
        _tryUpdateJsonb("User", _prefAliases, _userPrefs, 1L);
        _tryUpdateJsonb("User", _prefAliases, _userPrefs, 3L);
        _tryUpdateJsonb("User", _prefAliases, _userPrefs, 4L);

        // ── Addresses (5) ─────────────────────────────────────────
        // User 1 (admin, id=1) has 3 addresses (one default) so /users/1/profile
        // finds the rich shape S1-F8 expects (user + preferences + 3 delivery
        // addresses, one default). Also covers S1-F7 (toggle default).
        _seedAddress(1L, "1 Tahrir Sq",   "Cairo", true);   // address 1 — user 1 default
        _seedAddress(1L, "10 Maadi St",   "Cairo", false);  // address 2 — user 1 second
        _seedAddress(1L, "20 Heliopolis", "Cairo", false);  // address 3 — user 1 third
        _seedAddress(2L, "5 Zamalek St",  "Cairo", true);   // address 4 — user 2 default
        _seedAddress(4L, "30 Dokki St",   "Giza",  true);   // address 5 — user 4 default

        // ── Restaurants (4) ───────────────────────────────────────
        _seedRestaurant("Preseed Pizza Place", "ITALIAN",  "OPEN");
        _seedRestaurant("Preseed Koshari",     "EGYPTIAN", "OPEN");
        _seedRestaurant("Preseed Pasta House", "ITALIAN",  "CLOSED");
        _seedRestaurant("Preseed Falafel Hub", "EGYPTIAN", "OPEN");
        // ── S2 catalog fallback for non-Talabat/FoodDelivery themes ─────────
        // If "Restaurant" entity doesn't exist (e.g. Booking → Provider), seed 4
        // generic S2 catalog entries so TC48/TC49/TC50/TC52 have data.
        if (_tn("Restaurant") == null) {
            _seedS2Catalog(4);
            _seedS3Orders(1L); // bookings pointing to catalog ids 1,1,2,4
            String[] _cat2RatingAliases = {"rating", "averageRating", "avgRating"};
            _tryUpdateCol(s2CatalogEntity(), _cat2RatingAliases, 4.5, 1L);
            _tryUpdateCol(s2CatalogEntity(), _cat2RatingAliases, 3.5, 2L);
            _tryUpdateCol(s2CatalogEntity(), _cat2RatingAliases, 4.0, 3L);
            _tryUpdateCol(s2CatalogEntity(), _cat2RatingAliases, 4.8, 4L);
        }
        // Optional rating column (covers minRating / maxRating filters)
        String[] _ratingAliases = {"rating", "averageRating", "avgRating"};
        _tryUpdateCol("Restaurant", _ratingAliases, 4.5, 1L);
        _tryUpdateCol("Restaurant", _ratingAliases, 3.5, 2L);
        _tryUpdateCol("Restaurant", _ratingAliases, 4.0, 3L);
        _tryUpdateCol("Restaurant", _ratingAliases, 4.8, 4L);
        // Optional details jsonb (covers /restaurants/details/search?key=note&value=true)
        String[] _detailsAliases = {"details", "metadata", "extra", "info"};
        _tryUpdateJsonb("Restaurant", _detailsAliases,
            "{\"note\":\"true\",\"spicy\":\"true\",\"vegetarian\":\"available\"}", 1L);
        _tryUpdateJsonb("Restaurant", _detailsAliases,
            "{\"note\":\"true\",\"halal\":\"true\"}", 2L);

        // ── MenuItems (10) ────────────────────────────────────────
        // Distribution covers S2-F9 (unavailable list): restaurant 1 has
        // 2 AVAILABLE + 1 UNAVAILABLE; restaurant 2 has all AVAILABLE;
        // restaurant 3 has 3 UNAVAILABLE; restaurant 4 has 1 AVAILABLE.
        // Also referenced by S2-F8 (toggle), S3-F8 (add to order), S3-F9
        // (order details with item list).
        _seedMenuItem(1L, "Margherita Pizza",   80.0, "AVAILABLE");    //  1 — rest 1
        _seedMenuItem(1L, "Pepperoni Pizza",   100.0, "AVAILABLE");    //  2 — rest 1
        _seedMenuItem(1L, "Garlic Bread",       30.0, "UNAVAILABLE");  //  3 — rest 1
        _seedMenuItem(2L, "Koshari",            50.0, "AVAILABLE");    //  4 — rest 2
        _seedMenuItem(2L, "Mahshi",             60.0, "AVAILABLE");    //  5 — rest 2
        _seedMenuItem(2L, "Foul Sandwich",      30.0, "AVAILABLE");    //  6 — rest 2
        _seedMenuItem(3L, "Closed Item 1",      90.0, "UNAVAILABLE");  //  7 — rest 3
        _seedMenuItem(3L, "Closed Item 2",     100.0, "UNAVAILABLE");  //  8 — rest 3
        _seedMenuItem(3L, "Closed Item 3",     110.0, "UNAVAILABLE");  //  9 — rest 3
        _seedMenuItem(4L, "Falafel Sandwich",   25.0, "AVAILABLE");    // 10 — rest 4

        // ── Orders (8) ────────────────────────────────────────────
        // Distribution: user 1 (admin) = 1 order; user 2 = 2; user 4 = 5
        // (so /preferences/dietary?minOrders=5 matches user 4)
        _seedOrder(1L, 1L, "PLACED",    100.00);  // order 1 — admin
        _seedOrder(2L, 1L, "DELIVERED",  50.00);  // order 2 — user 2
        _seedOrder(2L, 2L, "CONFIRMED",  75.00);  // order 3 — user 2
        _seedOrder(4L, 1L, "DELIVERED",  30.00);  // order 4 — user 4
        _seedOrder(4L, 2L, "DELIVERED",  40.00);  // order 5 — user 4
        _seedOrder(4L, 1L, "DELIVERED",  50.00);  // order 6 — user 4
        _seedOrder(4L, 2L, "DELIVERED",  60.00);  // order 7 — user 4
        _seedOrder(4L, 4L, "PLACED",     70.00);  // order 8 — user 4
        // Optional order_date (covers ?startDate=&endDate= filters)
        String[] _orderDateAliases = {"orderDate", "createdAt", "placedAt", "date"};
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-01-01"), 1L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-01-15"), 2L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-02-01"), 3L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-01-05"), 4L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-01-10"), 5L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-01-20"), 6L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-02-05"), 7L);
        _tryUpdateCol("Order", _orderDateAliases, java.sql.Date.valueOf("2026-02-15"), 8L);
        // Optional metadata jsonb (covers /orders/metadata/search?key=note&value=true)
        String[] _orderMetaAliases = {"metadata", "details", "extra"};
        _tryUpdateJsonb("Order", _orderMetaAliases,
            "{\"note\":\"true\",\"priority\":\"HIGH\",\"channel\":\"app\"}", 1L);
        _tryUpdateJsonb("Order", _orderMetaAliases,
            "{\"note\":\"true\",\"channel\":\"web\"}", 2L);
        // Optional delivery_address column (text variant, if student stores as varchar)
        String[] _delAddrTextAliases = {"deliveryAddress", "shippingAddress", "address"};
        _tryUpdateCol("Order", _delAddrTextAliases, "1 Tahrir Sq, Cairo", 1L);
        _tryUpdateCol("Order", _delAddrTextAliases, "5 Zamalek St, Cairo", 2L);
        // Optional delivery_address_id column (FK variant). Address ownership:
        // addresses 1-3 → user 1; address 4 → user 2; address 5 → user 4.
        String[] _delAddrFkAliases = {"deliveryAddressId", "deliveryAddress", "shippingAddressId", "addressId"};
        _tryUpdateCol("Order", _delAddrFkAliases, 1L, 1L);  // order 1 → addr 1 (user 1)
        _tryUpdateCol("Order", _delAddrFkAliases, 4L, 2L);  // order 2 → addr 4 (user 2)
        _tryUpdateCol("Order", _delAddrFkAliases, 4L, 3L);  // order 3 → addr 4 (user 2)
        _tryUpdateCol("Order", _delAddrFkAliases, 5L, 4L);  // order 4 → addr 5 (user 4)
        _tryUpdateCol("Order", _delAddrFkAliases, 5L, 5L);  // order 5 → addr 5 (user 4)
        _tryUpdateCol("Order", _delAddrFkAliases, 5L, 6L);  // order 6 → addr 5 (user 4)
        _tryUpdateCol("Order", _delAddrFkAliases, 5L, 7L);  // order 7 → addr 5 (user 4)
        _tryUpdateCol("Order", _delAddrFkAliases, 5L, 8L);  // order 8 → addr 5 (user 4)

        // ── OrderItems (8) ────────────────────────────────────────
        // Order 1 has 4 items (2 PREPARED, 2 PENDING) — covers S3-F9
        // (order details with item-status breakdown). Other orders get 1
        // item each so /orders/{id}/details and /orders/{id}/items return
        // non-empty for any positive id in 1..5.
        _seedOrderItem(1L, 1L, 1,  80.0, "PREPARED");   // 1 — order 1
        _seedOrderItem(1L, 2L, 1, 100.0, "PREPARED");   // 2 — order 1
        _seedOrderItem(1L, 3L, 2,  60.0, "PENDING");    // 3 — order 1
        _seedOrderItem(1L, 4L, 1,  50.0, "PENDING");    // 4 — order 1
        _seedOrderItem(2L, 4L, 1,  50.0, "DELIVERED");  // 5 — order 2
        _seedOrderItem(3L, 5L, 1,  60.0, "DELIVERED");  // 6 — order 3
        _seedOrderItem(4L, 4L, 1,  50.0, "DELIVERED");  // 7 — order 4
        _seedOrderItem(5L, 5L, 1,  60.0, "DELIVERED");  // 8 — order 5

        // ── Deliveries (8) ────────────────────────────────────────
        // Statuses spread; FK to orders 1..8.
        _seedDelivery(1L, "PENDING");
        _seedDelivery(2L, "DELIVERED");
        _seedDelivery(3L, "IN_TRANSIT");
        _seedDelivery(4L, "DELIVERED");
        _seedDelivery(5L, "DELIVERED");
        _seedDelivery(6L, "DELIVERED");
        _seedDelivery(7L, "DELIVERED");
        _seedDelivery(8L, "PENDING");
        // Optional driver_name column — Hassan is attributed 5 deliveries
        // (matching S4-F8 spec scenario "5 deliveries by driver Hassan")
        String[] _driverAliases = {"driverName", "driver", "courierName", "rider"};
        _tryUpdateCol("Delivery", _driverAliases, "Ahmed",  1L);
        _tryUpdateCol("Delivery", _driverAliases, "Sara",   2L);
        _tryUpdateCol("Delivery", _driverAliases, "Omar",   3L);
        _tryUpdateCol("Delivery", _driverAliases, "Hassan", 4L);
        _tryUpdateCol("Delivery", _driverAliases, "Hassan", 5L);
        _tryUpdateCol("Delivery", _driverAliases, "Hassan", 6L);
        _tryUpdateCol("Delivery", _driverAliases, "Hassan", 7L);
        _tryUpdateCol("Delivery", _driverAliases, "Hassan", 8L);
        // Optional metadata jsonb — Hassan's deliveries with the spec's speeds
        String[] _delMetaAliases = {"metadata", "details", "extra"};
        _tryUpdateJsonb("Delivery", _delMetaAliases, "{\"driverName\":\"Hassan\",\"speed\":20,\"note\":\"true\"}", 4L);
        _tryUpdateJsonb("Delivery", _delMetaAliases, "{\"driverName\":\"Hassan\",\"speed\":30,\"note\":\"true\"}", 5L);
        _tryUpdateJsonb("Delivery", _delMetaAliases, "{\"driverName\":\"Hassan\",\"speed\":40,\"note\":\"true\"}", 6L);
        _tryUpdateJsonb("Delivery", _delMetaAliases, "{\"driverName\":\"Hassan\",\"speed\":50,\"note\":\"true\"}", 7L);
        _tryUpdateJsonb("Delivery", _delMetaAliases, "{\"driverName\":\"Hassan\",\"speed\":0,\"note\":\"true\"}",  8L);

        // ── Payments (8) ──────────────────────────────────────────
        // Status spread: COMPLETED (×6), REFUNDED (×1), PENDING (×1).
        _seedPayment(1L, 1L, 100.00, "COMPLETED");
        _seedPayment(2L, 2L,  50.00, "COMPLETED");
        _seedPayment(3L, 2L,  75.00, "COMPLETED");
        _seedPayment(4L, 4L,  30.00, "COMPLETED");
        _seedPayment(5L, 4L,  40.00, "COMPLETED");
        _seedPayment(6L, 4L,  50.00, "REFUNDED");
        _seedPayment(7L, 4L,  60.00, "COMPLETED");
        _seedPayment(8L, 4L,  70.00, "PENDING");
        // Optional transaction_details jsonb
        String[] _txDetailAliases = {"transactionDetails", "details", "metadata", "gatewayResponse"};
        _tryUpdateJsonb("Payment", _txDetailAliases,
            "{\"receipt\":\"abc-123\",\"note\":\"true\",\"gateway\":\"stripe\"}", 1L);
        _tryUpdateJsonb("Payment", _txDetailAliases,
            "{\"receipt\":\"def-456\",\"note\":\"true\"}", 2L);

        // ── Offers (3) ────────────────────────────────────────────
        _seedOffer("PRESEED10", "PERCENTAGE", 10.0, 100);
        _seedOffer("PRESEED20", "FIXED",      20.0,  50);
        _seedOffer("PRESEED5",  "PERCENTAGE",  5.0, 200);

        // ── payment_offers junction ───────────────────────────────
        // Usage 4/2/1 across offers 1/2/3 — covers /payments/offers/top-used.
        _seedPaymentOffer(1L, 1L);
        _seedPaymentOffer(2L, 1L);
        _seedPaymentOffer(3L, 1L);
        _seedPaymentOffer(4L, 1L);
        _seedPaymentOffer(5L, 2L);
        _seedPaymentOffer(6L, 2L);
        _seedPaymentOffer(7L, 3L);
    }
    // ─── end auto-seed hooks ───

    // ─── CRUD path discovery (manifest-sourced, spelling-tolerant) ───
    //
    // Why: CRUD pattern tests need to hit "the /api/<entity> endpoint"
    // without assuming the student spelled it exactly the way the
    // manifest declares. The manifest gives the canonical path
    // (rest_path_singular for each entity in seed_manifests/<theme>.json);
    // we probe that first, fall back to a singular form, then fall
    // back to the canonical itself. The discovered path is cached at
    // JVM scope so we probe at most once per entity per test run.

    /** Canonical CRUD paths from seed_manifests/Talabat_M2.json. */
    private static final Map<String, String> CRUD_PATHS = Map.of(
            "User",       "/api/users",
            "Restaurant", "/api/restaurants",
            "Order",      "/api/orders",
            "Delivery",   "/api/deliveries",
            "Payment",    "/api/payments",
            "Offer",      "/api/offers"
    );

    /** JVM-wide cache so different test classes don't re-probe. */
    private static final Map<String, String> _DISCOVERED_CRUD_PATHS = new ConcurrentHashMap<>();

    /**
     * Discover the actual CRUD base path the student wired up for an entity.
     * Probes the manifest's canonical path first, then a singular variant.
     * Returns the first candidate that doesn't 404 for an admin GET, or
     * the canonical path as a known-bad fallback so the calling test
     * fails meaningfully against it.
     *
     * @param entitySingular manifest entity key, e.g. "Order"
     * @return discovered base path, e.g. "/api/orders" or a student variant
     */
    protected String discoverCrudPath(String entitySingular) throws Exception {
        String cached = _DISCOVERED_CRUD_PATHS.get(entitySingular);
        if (cached != null) return cached;

        String canonical = CRUD_PATHS.get(entitySingular);
        if (canonical == null) {
            throw new IllegalArgumentException(
                    "Unknown CRUD entity '" + entitySingular + "'. "
                  + "Expected one of " + CRUD_PATHS.keySet());
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(canonical);
        if (canonical.endsWith("ies")) {
            candidates.add(canonical.substring(0, canonical.length() - 3) + "y");
        } else if (canonical.endsWith("s")) {
            candidates.add(canonical.substring(0, canonical.length() - 1));
        }

        String token = adminToken();
        for (String candidate : candidates) {
            HttpResponse<String> r = httpGetAuth(candidate, token);
            if (r.statusCode() != 404) {
                _DISCOVERED_CRUD_PATHS.put(entitySingular, candidate);
                return candidate;
            }
        }
        _DISCOVERED_CRUD_PATHS.put(entitySingular, canonical);
        return canonical;
    }
    // ─── end CRUD path discovery ───

    // ─── Source-scan helpers for design-pattern tests (TC379–TC425) ───
    protected static final String REPO_PATH_ENV = System.getenv("REPO_PATH");
    private static final java.util.Set<String> _SCAN_PRUNE_DIRS = java.util.Set.of(
        "target", "node_modules", ".git", ".idea", ".gradle", ".mvn",
        "build", "out", "dist", "bin", ".next", ".vscode",
        ".m2", "logs", "tmp", "temp", ".cache", "coverage", ".nyc_output"
    );
    private static volatile java.util.List<java.nio.file.Path> _ALL_JAVA_FILES_CACHE;
    private static volatile java.util.Map<String, String> _SOURCE_BY_NAME_CACHE;
    private static volatile String _ALL_SOURCES_CONCAT_CACHE;
    private static final Object _ALL_JAVA_FILES_LOCK = new Object();

    protected java.util.List<java.nio.file.Path> allJavaFiles() {
        if (REPO_PATH_ENV == null || REPO_PATH_ENV.isEmpty())
            return java.util.Collections.emptyList();
        java.util.List<java.nio.file.Path> cached = _ALL_JAVA_FILES_CACHE;
        if (cached != null) return cached;
        synchronized (_ALL_JAVA_FILES_LOCK) {
            if (_ALL_JAVA_FILES_CACHE != null) return _ALL_JAVA_FILES_CACHE;
            java.util.List<java.nio.file.Path> out = new java.util.ArrayList<>();
            try {
                java.nio.file.Files.walkFileTree(
                    java.nio.file.Path.of(REPO_PATH_ENV),
                    java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class),
                    Integer.MAX_VALUE,
                    new java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                        @Override public java.nio.file.FileVisitResult preVisitDirectory(
                                java.nio.file.Path dir, java.nio.file.attribute.BasicFileAttributes a) {
                            java.nio.file.Path nm = dir.getFileName();
                            if (nm != null && _SCAN_PRUNE_DIRS.contains(nm.toString()))
                                return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        @Override public java.nio.file.FileVisitResult visitFile(
                                java.nio.file.Path file, java.nio.file.attribute.BasicFileAttributes a) {
                            String s = file.toString();
                            if (s.endsWith(".java") && s.contains("/src/main/java/"))
                                out.add(file);
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                        @Override public java.nio.file.FileVisitResult visitFileFailed(
                                java.nio.file.Path file, java.io.IOException exc) {
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    }
                );
            } catch (java.io.IOException ignored) { }
            _ALL_JAVA_FILES_CACHE = java.util.Collections.unmodifiableList(out);
            return _ALL_JAVA_FILES_CACHE;
        }
    }

    /** Lazily build per-fileName content map AND a single concatenated-all-sources string.
     *  Both built in one pass — each file is read at most once per JVM. */
    private void _ensureSourceCaches() {
        if (_SOURCE_BY_NAME_CACHE != null && _ALL_SOURCES_CONCAT_CACHE != null) return;
        synchronized (_ALL_JAVA_FILES_LOCK) {
            if (_SOURCE_BY_NAME_CACHE != null && _ALL_SOURCES_CONCAT_CACHE != null) return;
            java.util.Map<String, String> byName = new java.util.HashMap<>();
            StringBuilder concat = new StringBuilder(2_000_000);
            for (java.nio.file.Path p : allJavaFiles()) {
                String fname = p.getFileName().toString();
                String content;
                try { content = java.nio.file.Files.readString(p); }
                catch (java.io.IOException e) { continue; }
                String existing = byName.get(fname);
                byName.put(fname, existing == null
                    ? content
                    : existing + "\n// --- next file ---\n" + content);
                concat.append("\n// --- ").append(fname).append(" ---\n").append(content);
            }
            _SOURCE_BY_NAME_CACHE = java.util.Collections.unmodifiableMap(byName);
            _ALL_SOURCES_CONCAT_CACHE = concat.toString();
        }
    }

    protected String readClassSource(String simpleClassName) {
        _ensureSourceCaches();
        return _SOURCE_BY_NAME_CACHE.getOrDefault(simpleClassName + ".java", "");
    }

    protected String readAllSourcesNamed(String simpleClassName) {
        _ensureSourceCaches();
        return _SOURCE_BY_NAME_CACHE.getOrDefault(simpleClassName + ".java", "");
    }

    protected long countImplementors(String interfaceName) {
        _ensureSourceCaches();
        String needle = "implements " + interfaceName;
        String selfFile = interfaceName + ".java";
        long count = 0;
        for (java.util.Map.Entry<String, String> e : _SOURCE_BY_NAME_CACHE.entrySet()) {
            if (e.getKey().equals(selfFile)) continue;
            if (e.getValue().contains(needle)) count++;
        }
        return count;
    }

    protected boolean anySourceContains(String pattern) {
        _ensureSourceCaches();
        return _ALL_SOURCES_CONCAT_CACHE.contains(pattern);
    }

    protected boolean noSourceContains(String pattern) { return !anySourceContains(pattern); }
    // ─── end source-scan helpers ───


    // ─── BSON tree-walk helpers for design-pattern tests (TC379–TC425) ───

    /** True if a target string literal appears as ANY leaf value in the
     *  decoded BSON tree under {@code node}. Walks Document / Map /
     *  Iterable / scalar uniformly. Use to match action-type literals
     *  like "REFUND_DENIED" / "INTERACTION_RECORDED" without pinning
     *  the field name. */
    protected boolean bsonContainsString(Object node, String target) {
        if (node == null) return false;
        if (node instanceof String s) return target.equals(s);
        if (node instanceof org.bson.Document d) {
            for (String k : d.keySet()) {
                if (bsonContainsString(d.get(k), target)) return true;
            }
            return false;
        }
        if (node instanceof java.util.Map<?, ?> m) {
            for (Object v : m.values()) {
                if (bsonContainsString(v, target)) return true;
            }
            return false;
        }
        if (node instanceof Iterable<?> it) {
            for (Object v : it) {
                if (bsonContainsString(v, target)) return true;
            }
            return false;
        }
        return false;
    }

    /** True if a target numeric appears as ANY leaf value in the decoded
     *  BSON tree. Tolerates Long / Integer / Double / stringified-id
     *  storage. Use to match identifiers (invoiceId / bookingId /
     *  providerId) without pinning the field name. */
    protected boolean bsonContainsLong(Object node, long target) {
        if (node == null) return false;
        if (node instanceof Number n) return n.longValue() == target;
        if (node instanceof String s) {
            try { return Long.parseLong(s) == target; } catch (NumberFormatException e) { return false; }
        }
        if (node instanceof org.bson.Document d) {
            for (String k : d.keySet()) {
                if (bsonContainsLong(d.get(k), target)) return true;
            }
            return false;
        }
        if (node instanceof java.util.Map<?, ?> m) {
            for (Object v : m.values()) {
                if (bsonContainsLong(v, target)) return true;
            }
            return false;
        }
        if (node instanceof Iterable<?> it) {
            for (Object v : it) {
                if (bsonContainsLong(v, target)) return true;
            }
            return false;
        }
        return false;
    }
    // ─── end BSON tree-walk helpers ───

}
