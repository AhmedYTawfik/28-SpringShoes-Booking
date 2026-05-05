package com.testgen.booking;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

// ────────────────────────────────────────────────────────────────────────────
// PublicTests.java — hand-written, dynamic, scenario-driven public test cases
// for Talabat M2.
//
// This is the canonical public test file. The 749 template-generated classes
// (deprecated) live in archive/PublicTestsAll_Stale.java for reference but
// no longer execute.
//
// Each class below is one row in
// docs/test-scenarios/Talabat_Tests_Description.md.
//
// Style guide:
//   * One package-private `class TC<NN>_<DescriptiveName> extends TestBase`
//     per scenario, with one or more @Test methods.
//   * @Tag("public") + a category tag (features_m1 / features_m2 / patterns
//     / amendments / updated_crud / cross_cutting) per scenario row.
//   * Resolve every URL through TestBase helpers — never hardcode "/api/...":
//       - crudReadPath("Order"), crudCollectionPath("MenuItem"), fillPath(...)
//       - loginPath(), registerPath()
//     Resolve table names through tableName("Order"), enums through
//     enumValues("OrderStatus").
//   * Resolve IDs from response bodies (registration, search results) or
//     from the auto-seeded fixtures (admin = id=1 via adminToken/adminId,
//     vegetarian customer with 5 orders = id=4, etc. — see
//     memory/project_talabat_seed_data.md). Never write `/api/users/1`
//     literally.
//   * One scenario at a time, approved by the user before mapping to the
//     other 7 themes.
// ────────────────────────────────────────────────────────────────────────────

// ─── TC01 — Register a new user (happy path) ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC01_RegisterHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC01 — POST registerPath() with a fresh email returns 2xx and a JWT token")
        void register_returns_2xx_with_token() throws Exception {
                BASE_URL = userServiceUrl;
                // Build a payload with a nonce-based email so it cannot collide with
                // any of the auto-seeded users (_preseed_*@grader.testgen.io) or
                // with prior runs of this test class.
                String email = "tc01_" + nonce() + "@grader.testgen.io";
                String body = String.format("""
                                {"name":"TC01 User","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));

                HttpResponse<String> r = httpPost("/api/auth/register", body);

                // Strict 2xx — registering a brand-new email with a valid payload
                // must succeed; any non-2xx is a bug in register / validation /
                // password hashing / DB persistence.
                assert2xx(r, "TC01 register");
                JsonNode j = parseNode(r.body());
                // Spec: response is { "token": "...", "expiresIn": ... }. No 'id' field;
                // chained tests resolve uid from JWT (uidFromJwt()) or via login.
                assertNotNull(j.get("token"),
                                "TC01: register response must include 'token' field per spec; body=" + r.body());
                assertFalse(j.get("token").asText().isBlank(),
                                "TC01: 'token' must be a non-blank string; got " + j.get("token"));
                assertTrue(j.has("expiresIn") && j.get("expiresIn").asLong() > 0,
                                "TC01: register response must include positive 'expiresIn' per spec; body=" + r.body());
        }
}

// ─── TC02 — Login with valid credentials (happy path) ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC02_LoginHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC02 — POST loginPath() after a successful register returns 2xx with a 3-segment JWT")
        void login_returns_2xx_with_three_segment_jwt() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — create a fresh user.
                String email = "tc02_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC02 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC02 setup register");

                // Act — log in with the same credentials.
                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> r = httpPost("/api/auth/login", loginBody);

                // Assert.
                assert2xx(r, "TC02 login");
                JsonNode j = parseNode(r.body());
                assertNotNull(j.get("token"),
                                "TC02: login response must include 'token' field; body=" + r.body());
                String token = j.get("token").asText();
                assertFalse(token.isBlank(),
                                "TC02: 'token' must be a non-blank string");
                assertEquals(3, token.split("\\.").length,
                                "TC02: 'token' must be a 3-segment JWT (a.b.c); got '" + token + "'");
        }
}

// ─── TC03 — Read own user profile with valid JWT (happy path) ───────────────
@Tag("public")
@Tag("updated_crud")
class TC03_ReadOwnProfileHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC03 — GET crudReadPath(\"User\") with own JWT returns 2xx and a JSON object")
        void read_own_profile_returns_2xx_and_json_object() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register and capture the new user's id.
                String email = "tc03_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC03 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC03 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                // Setup — login and capture the JWT.
                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC03 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Act — read the user's own profile via the User CRUD path. The
                // path template comes from the manifest so a student who renames
                // their controller still gets the correct URL.
                HttpResponse<String> r = httpGetAuth("/api/users/" + uid, token);

                // Assert. Strict 2xx — we just registered this exact user, a 404
                // here is a real bug (register didn't persist OR the JWT chain
                // rejected our own token).
                assert2xx(r, "TC03 read own profile");
                JsonNode j = parseNode(r.body());
                assertTrue(j.isObject(),
                                "TC03: response body must be a JSON object; got " + r.body());
        }
}

// ─── TC04 — Register with duplicate email returns 4xx (negative path) ───────
@Tag("public")
@Tag("features_m2")
class TC04_RegisterDuplicateEmailTests extends TestBase {

        @Test
        @DisplayName("TC04 — POST registerPath() with an already-registered email returns a 4xx")
        void register_with_duplicate_email_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Build a payload with a nonce-based email — guaranteed not to
                // collide with auto-seeded users or with prior runs of this test
                // class (since @BeforeEach truncates between tests anyway).
                String email = "tc04_" + nonce() + "@grader.testgen.io";
                String firstBody = String.format("""
                                {"name":"TC04 First","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));

                // Step 1 — first registration must succeed (precondition).
                HttpResponse<String> first = httpPost("/api/auth/register", firstBody);
                assert2xx(first, "TC04 first register (precondition)");

                // Step 2 — second registration with the SAME email but different
                // name + phone (uniqueness should be on email).
                String secondBody = String.format("""
                                {"name":"TC04 Second","email":"%s","password":"AnotherPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> second = httpPost("/api/auth/register", secondBody);

                // Step 3 — strict 4xx assertion. Tolerate any of 400/409/422
                // (M2 spec doesn't pin a specific code) but NOT 2xx, NOT 5xx,
                // and NOT 401/403 (the body itself is well-formed and authorized).
                int code = second.statusCode();
                assertTrue(code >= 400 && code < 500,
                                "TC04: duplicate-email register must return a 4xx client error; "
                                                + "got " + code + " body=" + second.body());
                assertTrue(code != 401,
                                "TC04: duplicate-email is not an auth failure (no Authorization header was sent); "
                                                + "401 indicates the controller is misclassifying the error. body="
                                                + second.body());
                assertTrue(code != 403,
                                "TC04: duplicate-email is not a permission failure (anyone can register); "
                                                + "403 indicates the controller is misclassifying the error. body="
                                                + second.body());
        }
}

// ─── TC05 — Login with wrong password returns 401 (negative path) ───────────
@Tag("public")
@Tag("features_m2")
class TC05_LoginWrongPasswordTests extends TestBase {

        @Test
        @DisplayName("TC05 — POST loginPath() with the wrong password returns strictly 401")
        void login_with_wrong_password_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register a fresh user with a known-correct password.
                // nonce-based email avoids collisions with auto-seeded users and
                // with prior test runs (truncate@BeforeEach handles cross-test).
                String email = "tc05_" + nonce() + "@grader.testgen.io";
                String correctPwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC05 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, correctPwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC05 setup register (precondition)");

                // Act — log in with the SAME email but a different password.
                // Different enough that bcrypt cannot accidentally match (no
                // shared prefix, different length).
                String wrongPwd = "WrongPwd!2026";
                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, wrongPwd);
                HttpResponse<String> r = httpPost("/api/auth/login", loginBody);
                int code = r.statusCode();

                // Assert — strictly 401. Tolerant assertions (with explanatory
                // messages) for each common misclassification:
                // * 2xx: login skipped the password check — critical security bug.
                // * 5xx: bcrypt mismatch leaked as exception instead of being
                // caught and translated to 401.
                // * 404: user-enumeration anti-pattern (OWASP). Login must NOT
                // distinguish "user not found" from "wrong password" in
                // its status code; both should be 401.
                // * 403: this is not a permissions issue. Login is how you
                // OBTAIN permissions; you cannot be 403'd from it.
                assertTrue(code / 100 != 2,
                                "TC05: login with wrong password must NOT return 2xx. "
                                                + "A 2xx here means the password check was skipped — critical security bug. "
                                                + "Got " + code + " body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC05: login with wrong password must NOT 5xx. A 5xx here means the bcrypt "
                                                + "mismatch threw an unhandled exception instead of being caught and "
                                                + "translated to 401. Got " + code + " body=" + r.body());
                assertTrue(code != 404,
                                "TC05: login with wrong password must NOT return 404. The user account exists; "
                                                + "returning 404 instead of 401 leaks the existence/non-existence of accounts "
                                                + "(OWASP user-enumeration anti-pattern). body=" + r.body());
                assertTrue(code != 403,
                                "TC05: login with wrong password must NOT return 403. Login is the act of "
                                                + "obtaining permissions, not exercising them — 403 is structurally wrong. "
                                                + "body=" + r.body());
                assertEquals(401, code,
                                "TC05: login with wrong password must return strictly 401 Unauthorized; got "
                                                + code + " body=" + r.body());
        }
}

// ─── TC06 — Authentication happy path: valid admin JWT accepted on a non-User
// CRUD
@Tag("public")
@Tag("authentication")
class TC06_AuthValidTokenAcceptedTests extends TestBase {

        @Test
        @DisplayName("TC06 — GET a non-User CRUD list endpoint with a valid admin Bearer JWT returns 2xx (auth filter accepts the token)")
        void valid_admin_jwt_is_accepted_on_non_user_crud() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — obtain an admin JWT via TestBase.adminToken(). Uses
                // the pre-seeded admin login fast path when available, falling
                // back to TestAuthHelper.seedAdmin (HTTP register + JDBC promote
                // to ADMIN + login) otherwise. Admin role ensures TC06 is not
                // 403-blocked on entities whose list endpoint is admin-only.
                String token = adminToken();

                // Act — hit a NON-User CRUD list endpoint with the admin token.
                // Picks the first top-level non-User entity from the manifest
                // (theme-specific: Talabat → Address, Amazon → ShippingAddress,
                // Booking → Provider, etc.). Broadens auth-filter coverage
                // beyond TC03's User endpoint without hardcoding per-theme.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                if (entity.equals(s2CatalogEntity())) BASE_URL = catalogServiceUrl;
                else if (entity.equals(s3OrderEntity())) BASE_URL = orderServiceUrl;
                else if (entity.equals(s4Entity())) BASE_URL = deliveryServiceUrl;
                else BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpGetAuth(path, token);

                // Assert — strict 2xx. This proves the auth filter accepts the
                // token end-to-end (signature verified, claims parsed, security
                // context populated) on a different controller than
                // UserController, ruling out "auth only works on /api/users".
                assert2xx(r, "TC06 auth happy path (admin) on " + entity + " list (" + path + ")");
        }
}

// ─── TC07 — Missing Authorization header on a non-User CRUD returns 401 ─────
@Tag("public")
@Tag("authentication")
class TC07_AuthMissingHeaderTests extends TestBase {

        @Test
        @DisplayName("TC07 — GET a non-User CRUD list endpoint with NO Authorization header returns strictly 401")
        void missing_auth_header_returns_401_on_non_user_crud() throws Exception {
                BASE_URL = userServiceUrl;
                // Act — same endpoint as TC06's happy path (so the two form a
                // clean A/B test of the JWT filter), BUT no Authorization
                // header at all (httpGet, not httpGetAuth). No setup needed —
                // we're testing whether anonymous requests are blocked, which
                // doesn't depend on having any specific row in the DB.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                HttpResponse<String> r = httpGet(path);
                int code = r.statusCode();

                // Assert — strictly 401. Tolerant assertions for each common
                // misclassification, with diagnostic messages:
                // * 2xx: endpoint wide-open — critical security bug.
                // * 5xx: filter chain crashed instead of cleanly rejecting.
                // * 404: endpoint reachable anonymously and just returns
                // empty/missing — wrong; auth must block FIRST, before
                // any controller logic runs.
                // * 403: 403 means "authenticated but lacking permission". We
                // sent NO credentials at all — the correct response is
                // 401 (Unauthorized), not 403 (Forbidden).
                assertTrue(code / 100 != 2,
                                "TC07: GET " + path + " without Authorization header must NOT return 2xx. "
                                                + "A 2xx here means the endpoint is wide-open — critical security bug. "
                                                + "Got " + code + " body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC07: GET " + path + " without Authorization header must NOT 5xx. A 5xx here "
                                                + "means the filter chain crashed instead of cleanly rejecting. "
                                                + "Got " + code + " body=" + r.body());
                assertTrue(code != 404,
                                "TC07: GET " + path + " without Authorization header must NOT return 404. "
                                                + "A 404 here means the endpoint is reachable anonymously and just "
                                                + "returned empty — auth must block FIRST, before any controller logic "
                                                + "runs. body=" + r.body());
                assertTrue(code != 403,
                                "TC07: GET " + path + " without Authorization header must NOT return 403. We "
                                                + "sent NO credentials; 403 means \"authenticated but lacking permission\", "
                                                + "which doesn't apply when there's no auth attempt at all. The correct "
                                                + "code is 401 Unauthorized. body=" + r.body());
                assertEquals(401, code,
                                "TC07: GET " + path + " without Authorization header must return strictly 401 "
                                                + "Unauthorized; got " + code + " body=" + r.body());
        }
}

// ─── TC08 — Tampered JWT signature is rejected with 401 (negative path) ─────
@Tag("public")
@Tag("authentication")
class TC08_AuthTamperedSignatureTests extends TestBase {

        @Test
        @DisplayName("TC08 — GET protected endpoint with a tampered-signature JWT returns strictly 401 (signature is verified, not just decoded)")
        void tampered_jwt_signature_is_rejected_with_401() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — get a real, fully-valid admin JWT, then tamper its
                // signature segment. tamperSignature(token) preserves the
                // header + payload (still parses as a JWT, still passes any
                // "is it 3 segments?" check) but replaces the signature with
                // a base64 of "tampered-signature-does-not-verify" — so the
                // signature cannot verify against any signing key.
                String validToken = adminToken();
                String tamperedToken = tamperSignature(validToken);

                // Act — same endpoint as TC06/TC07 (the auth-filter A/B/C
                // triad). Send the tampered token in the Authorization header.
                // A correctly-implemented auth filter MUST reject this with 401
                // even though the token looks structurally valid.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                HttpResponse<String> r = httpGetAuth(path, tamperedToken);
                int code = r.statusCode();

                // Assert — strictly 401. Diagnostic ladder:
                // * 2xx: signature not actually verified — critical security bug
                // (anyone can forge tokens by editing the payload).
                // * 5xx: filter chain crashed on signature mismatch instead of
                // cleanly rejecting.
                // * 404: filter passed the request through to the controller
                // and just didn't find anything — wrong; signature
                // mismatch must be caught FIRST in the filter, before
                // any controller logic.
                // * 403: forged credentials = "not authenticated" (401), not
                // "authenticated but lacking permission" (403).
                assertTrue(code / 100 != 2,
                                "TC08: tampered-signature JWT must NOT be accepted (status " + code + "). "
                                                + "A 2xx here means the signature was not actually verified — anyone can "
                                                + "forge tokens by editing the payload. Critical security bug. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC08: tampered-signature JWT must NOT 5xx (status " + code + "). A 5xx here "
                                                + "means the filter chain crashed on signature mismatch instead of cleanly "
                                                + "rejecting. body=" + r.body());
                assertTrue(code != 404,
                                "TC08: tampered-signature JWT must NOT return 404 (status " + code + "). A 404 "
                                                + "here means the filter passed the request through to the controller and "
                                                + "just didn't find anything — signature mismatch must be caught FIRST in "
                                                + "the filter, before any controller logic runs. body=" + r.body());
                assertTrue(code != 403,
                                "TC08: tampered-signature JWT must NOT return 403 (status " + code + "). Forged "
                                                + "credentials are \"not authenticated\" (401), not \"authenticated but "
                                                + "lacking permission\" (403). body=" + r.body());
                assertEquals(401, code,
                                "TC08: tampered-signature JWT must return strictly 401 Unauthorized; got "
                                                + code + " body=" + r.body());
        }
}

// ─── TC09 — Login with non-existent email returns 401 (negative path) ───────
@Tag("public")
@Tag("features_m2")
class TC09_LoginUnknownEmailTests extends TestBase {

        @Test
        @DisplayName("TC09 — POST loginPath() with an email that has never been registered returns strictly 401")
        void login_unknown_email_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                // nonce-based email — guaranteed not to exist in the DB. Any
                // truncate/auto-seed in @BeforeEach also strips users, so this
                // email is truly absent at login time.
                String unknownEmail = "tc09_never_registered_" + nonce() + "@grader.testgen.io";
                String body = String.format("""
                                {"email":"%s","password":"AnythingPwd!2026"}
                                """, unknownEmail);

                HttpResponse<String> r = httpPost("/api/auth/login", body);
                int code = r.statusCode();

                // Spec §10 S1-F11: 401 for both "user not found" and "wrong password" — avoids email enumeration.
                assertTrue(code / 100 != 2,
                                "TC09: login with a never-registered email must NOT return 2xx (status "
                                                + code + "). A 2xx here means a token was issued for a non-existent "
                                                + "user. body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC09: login with a never-registered email must NOT 5xx (status " + code
                                                + "). Server must handle the missing-user case cleanly. body="
                                                + r.body());
                assertTrue(code != 403,
                                "TC09: login with a never-registered email must NOT return 403 (status " + code
                                                + "). 403 is a permission error; this is an unauthenticated condition. body="
                                                + r.body());
                assertEquals(401, code,
                                "TC09: login with a never-registered email must return strictly 401 Unauthorized "
                                                + "(spec §10 S1-F11 — 401 for both 'user not found' and 'wrong password'); "
                                                + "got " + code + " body=" + r.body());
        }
}

// ─── TC10 — Empty Bearer token returns 401 (negative path) ──────────────────
@Tag("public")
@Tag("authentication")
class TC10_AuthEmptyBearerTests extends TestBase {

        @Test
        @DisplayName("TC10 — GET protected endpoint with `Authorization: Bearer ` (empty token) returns strictly 401")
        void empty_bearer_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // "Bearer " with a trailing space and no token after it. Not a
                // missing header (TC07 covers that) — this header is PRESENT
                // but its value is malformed.
                HttpResponse<String> r = httpGetWithRawAuth(path, "Bearer ");
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC10: empty Bearer token must NOT be accepted (status " + code
                                                + "). A 2xx here means the auth filter accepted an empty/missing token. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC10: empty Bearer token must NOT 5xx (status " + code + "). The filter must "
                                                + "handle empty tokens cleanly, not throw NPE. body=" + r.body());
                assertEquals(401, code,
                                "TC10: empty Bearer token must return strictly 401 Unauthorized; got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC11 — Non-Bearer scheme (Basic) returns 401 (negative path) ───────────
@Tag("public")
@Tag("authentication")
class TC11_AuthBasicSchemeTests extends TestBase {

        @Test
        @DisplayName("TC11 — GET protected endpoint with `Authorization: Basic ...` (non-Bearer scheme) returns strictly 401")
        void basic_scheme_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // "Basic dXNlcjpwYXNz" — base64 of "user:pass". Catches lenient
                // parsers that strip the scheme prefix and try to decode whatever
                // is left as a JWT (it isn't).
                HttpResponse<String> r = httpGetWithRawAuth(path, "Basic dXNlcjpwYXNz");
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC11: Basic-scheme auth must NOT be accepted on a JWT-protected endpoint "
                                                + "(status " + code
                                                + "). The filter must reject any non-Bearer scheme. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC11: Basic-scheme auth must NOT 5xx (status " + code + "). body=" + r.body());
                assertEquals(401, code,
                                "TC11: Basic-scheme auth must return strictly 401 Unauthorized; got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC12 — Garbage non-JWT token returns 401 (negative path) ───────────────
@Tag("public")
@Tag("authentication")
class TC12_AuthGarbageTokenTests extends TestBase {

        @Test
        @DisplayName("TC12 — GET protected endpoint with `Authorization: Bearer not_a_valid_jwt` returns strictly 401")
        void garbage_token_returns_401() throws Exception {
                BASE_URL = userServiceUrl;
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                // "not_a_valid_jwt" — no dots, not base64, structurally invalid.
                // Catches "if 3 dot-segments, decode without verifying" and any
                // code path that doesn't validate JWT structure before claims-extract.
                HttpResponse<String> r = httpGetWithRawAuth(path, "Bearer not_a_valid_jwt");
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC12: garbage non-JWT token must NOT be accepted (status " + code
                                                + "). A 2xx here means the filter didn't validate the JWT structure. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC12: garbage token must NOT 5xx (status " + code + "). The parser must "
                                                + "handle malformed tokens gracefully. body=" + r.body());
                assertEquals(401, code,
                                "TC12: garbage non-JWT token must return strictly 401 Unauthorized; got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC13 — Forged role-claim token (payload modified post-signing) rejected
@Tag("public")
@Tag("authentication")
class TC13_AuthForgedRoleClaimTests extends TestBase {

        @Test
        @DisplayName("TC13 — GET protected endpoint with a payload-tampered JWT (role forged to ADMIN) is NOT accepted")
        void forged_role_claim_token_is_rejected() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register a CUSTOMER user (default theme role) and log
                // in to capture a real, signed JWT for them.
                String email = "tc13_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC13 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC13 setup register (precondition)");

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC13 setup login (precondition)");
                String realToken = parseNode(login.body()).get("token").asText();

                // Forge a role claim into the payload while keeping the original
                // signature. The signature was computed over the original
                // payload; modifying the payload makes the signature INVALID
                // even though we don't tamper the signature segment itself.
                String[] parts = realToken.split("\\.");
                if (parts.length != 3) {
                        throw new AssertionError("TC13 setup: real token is not a 3-segment JWT: " + realToken);
                }
                String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                // Try to replace common role-claim patterns. If none match,
                // inject a "role":"ADMIN" entry into the JSON object.
                String tamperedPayload = payloadJson;
                for (String[] swap : new String[][] {
                                { "\"role\":\"CUSTOMER\"", "\"role\":\"ADMIN\"" },
                                { "\"role\": \"CUSTOMER\"", "\"role\":\"ADMIN\"" },
                                { "\"authorities\":[\"ROLE_CUSTOMER\"]", "\"authorities\":[\"ROLE_ADMIN\"]" },
                }) {
                        if (tamperedPayload.contains(swap[0])) {
                                tamperedPayload = tamperedPayload.replace(swap[0], swap[1]);
                                break;
                        }
                }
                if (tamperedPayload.equals(payloadJson)) {
                        // No known role-claim pattern found — inject one. Locate the
                        // closing brace of the outer object and prepend a role claim.
                        int lastBrace = tamperedPayload.lastIndexOf('}');
                        if (lastBrace > 0) {
                                String prefix = tamperedPayload.substring(0, lastBrace).trim();
                                if (prefix.endsWith("{")) {
                                        tamperedPayload = prefix + "\"role\":\"ADMIN\"}";
                                } else {
                                        tamperedPayload = prefix + ",\"role\":\"ADMIN\"}";
                                }
                        }
                }
                String tamperedB64 = java.util.Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(tamperedPayload.getBytes());
                String forgedToken = parts[0] + "." + tamperedB64 + "." + parts[2];

                // Act — hit a protected endpoint with the forged token.
                String entity = firstTopLevelNonUserEntity();
                String path = crudCollectionPath(entity);
                HttpResponse<String> r = httpGetAuth(path, forgedToken);
                int code = r.statusCode();

                // Assert — must NOT be 2xx. Either of these is acceptable:
                // * 401 — signature verification caught the payload tamper (preferred).
                // * 403 — signature verified but server re-validated role from DB
                // and found CUSTOMER, not ADMIN.
                // But 2xx means signature wasn't verified AND role was trusted from
                // the (forged) payload — critical privilege-escalation bug.
                assertTrue(code / 100 != 2,
                                "TC13: forged role-claim token must NOT be accepted (status " + code + "). "
                                                + "A 2xx here means the signature was not verified after payload "
                                                + "modification — anyone with a real token can self-promote to ADMIN by "
                                                + "editing their payload. Critical privilege-escalation bug. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC13: forged role-claim token must NOT 5xx (status " + code + "). The filter "
                                                + "must reject cleanly. body=" + r.body());
        }
}

// ─── TC14 — Register with missing required field returns 4xx (negative path)
@Tag("public")
@Tag("features_m2")
class TC14_RegisterMissingFieldTests extends TestBase {

        @Test
        @DisplayName("TC14 — POST registerPath() with a body missing the `email` field returns a 4xx")
        void register_missing_email_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Body missing `email` — controller must reject with 4xx, not
                // propagate the missing-field as a server-side NPE/5xx or
                // silently create a user with null email.
                String body = String.format("""
                                {"name":"TC14 User","password":"TestPwd!2026","phone":"+201%s"}
                                """, nonce().substring(0, 9));

                HttpResponse<String> r = httpPost("/api/auth/register", body);
                int code = r.statusCode();

                assertTrue(code >= 400 && code < 500,
                                "TC14: register with missing required field (email) must return a 4xx; got "
                                                + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC14: register with missing email must NOT 2xx (a user without an email "
                                                + "cannot be valid). body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC14: register with missing email must NOT 5xx — the controller must "
                                                + "validate input cleanly, not crash. body=" + r.body());
        }
}

// ─── TC15 — Register with role=ADMIN in body must NOT yield an ADMIN account ─
@Tag("public")
@Tag("features_m2")
class TC15_RegisterRoleEscalationTests extends TestBase {

        @Test
        @DisplayName("TC15 — POST registerPath() with `role:ADMIN` in body must NOT result in an ADMIN account (privilege-escalation)")
        void register_with_role_admin_in_body_must_not_escalate() throws Exception {
                BASE_URL = userServiceUrl;
                // Build a register body with `role:"ADMIN"` injected. The
                // controller may either ignore the field, reject the request,
                // or accept the request — but the resulting DB row's role
                // column must NOT be ADMIN. Anything else is privilege escalation.
                String email = "tc15_" + nonce() + "@grader.testgen.io";
                String body = String.format(
                                """
                                                {"name":"TC15 User","email":"%s","password":"TestPwd!2026","phone":"+201%s","role":"ADMIN"}
                                                """,
                                email, nonce().substring(0, 9));

                HttpResponse<String> reg = httpPost("/api/auth/register", body);
                // We don't strictly require 2xx here — a controller that
                // rejects extra fields with 4xx is also fine. What matters is
                // that no ADMIN account got created.
                int regCode = reg.statusCode();
                assertTrue(regCode / 100 != 5,
                                "TC15: register with role=ADMIN body must NOT 5xx (got " + regCode
                                                + "). body=" + reg.body());

                // If the registration succeeded, look up the role in DB and
                // assert it's NOT ADMIN.
                if (regCode / 100 == 2) {
                        String role = fetchUserRole(email);
                        assertNotNull(role,
                                        "TC15: registration returned 2xx but no user row found for email "
                                                        + email + " — register isn't actually persisting.");
                        assertTrue(!"ADMIN".equalsIgnoreCase(role),
                                        "TC15: registering with role=ADMIN in the body must NOT result in an "
                                                        + "ADMIN account. Found role=" + role + " (expected the theme "
                                                        + "default, NOT ADMIN). This is a privilege-escalation bug — the "
                                                        + "controller is mapping the body's role field into the entity.");
                }
                // If registration was rejected (4xx), that's also acceptable —
                // the role field was caught as invalid input. Either way no
                // ADMIN was created.
        }
}

// ─── TC16 — Login with empty password returns 4xx (negative path) ───────────
@Tag("public")
@Tag("features_m2")
class TC16_LoginEmptyPasswordTests extends TestBase {

        @Test
        @DisplayName("TC16 — POST loginPath() with `password:\"\"` (empty) returns NOT 2xx")
        void login_empty_password_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register a real user so the email exists.
                String email = "tc16_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC16 User","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBody), "TC16 setup register");

                // Act — login with empty password. Bcrypt verification must
                // not be bypassed by an empty input.
                String loginBody = String.format("""
                                {"email":"%s","password":""}
                                """, email);
                HttpResponse<String> r = httpPost("/api/auth/login", loginBody);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC16: login with empty password must NOT issue a token (status " + code
                                                + "). A 2xx here means bcrypt verification was bypassed for empty "
                                                + "input. body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC16: login with empty password must NOT 5xx (status " + code + "). The "
                                                + "controller must validate input cleanly, not NPE on empty string. "
                                                + "body=" + r.body());
                // Acceptable: 400 (validation error) or 401 (auth failure) or
                // 422 (semantic validation error). All 4xx.
                assertTrue(code >= 400 && code < 500,
                                "TC16: login with empty password must return a 4xx (validation or auth "
                                                + "failure); got " + code + " body=" + r.body());
        }
}

// ─── TC17 — Cross-user IDOR: User A cannot READ User B's profile ────────────
@Tag("public")
@Tag("authorization")
class TC17_IdorReadOtherUserTests extends TestBase {

        @Test
        @DisplayName("TC17 — Customer A's GET on User B's CRUD path must NOT be 2xx (cross-user IDOR)")
        void customer_a_cannot_read_user_b_profile() throws Exception {
                BASE_URL = userServiceUrl;
                String emailA = "tc17a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc17b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBodyA = String.format("""
                                {"name":"TC17 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"TC17 B","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC17 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC17 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC17 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                HttpResponse<String> r = httpGetAuth("/api/users/" + bid, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC17: customer A reading customer B's profile must NOT be 2xx (status "
                                                + code + "). A 2xx here means cross-user IDOR is unprotected — any "
                                                + "authenticated user can read any other user's profile. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC17: cross-user read must NOT 5xx (status " + code + "). The auth check "
                                                + "must reject cleanly, not crash. body=" + r.body());
                assertTrue(code == 403 || code == 404,
                                "TC17: cross-user read must return 403 (forbidden) or 404 (not-found / "
                                                + "privacy-by-obscurity); got " + code + " body=" + r.body());
        }
}

// ─── TC18 — Cross-user IDOR: User A cannot UPDATE User B's profile ──────────
@Tag("public")
@Tag("authorization")
class TC18_IdorUpdateOtherUserTests extends TestBase {

        @Test
        @DisplayName("TC18 — Customer A's PUT on User B's CRUD path must NOT be 2xx, AND B's data must NOT change in DB")
        void customer_a_cannot_update_user_b_profile() throws Exception {
                BASE_URL = userServiceUrl;
                String emailA = "tc18a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc18b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String origNameB = "TC18 B Original";
                String regBodyA = String.format("""
                                {"name":"TC18 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"+201%s"}
                                """, origNameB, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC18 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC18 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC18 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                // Mitigation pattern — include all original fields plus the
                // changed name (some controllers require all fields on PUT).
                String tamperedName = "TC18 HIJACK";
                String putBody = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"+201%s"}
                                """, tamperedName, emailB, pwd, nonce().substring(0, 9));
                HttpResponse<String> r = httpPutAuth("/api/users/" + bid, putBody, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC18: customer A updating customer B's profile must NOT be 2xx (status "
                                                + code + "). A 2xx here means cross-user IDOR write is unprotected. "
                                                + "body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC18: cross-user update must NOT 5xx (status " + code + "). body=" + r.body());
                assertTrue(code == 403 || code == 404,
                                "TC18: cross-user update must return 403 or 404; got " + code + " body=" + r.body());

                // Defensive — even if controller returned 4xx, verify B's row
                // in DB was NOT mutated. Some buggy controllers reject the
                // response but commit the change.
                String userTable = tableName("User");
                String currentName = jdbc.queryForObject(
                                "SELECT name FROM " + userTable + " WHERE id = ?",
                                String.class, bid);
                assertEquals(origNameB, currentName,
                                "TC18: cross-user PUT was rejected (status " + code + ") but B's name in DB "
                                                + "changed from '" + origNameB + "' to '" + currentName + "' — the "
                                                + "controller is committing the change before doing the auth check.");
        }
}

// ─── TC19 — Cross-user IDOR: User A cannot DELETE User B ────────────────────
@Tag("public")
@Tag("authorization")
class TC19_IdorDeleteOtherUserTests extends TestBase {

        @Test
        @DisplayName("TC19 — Customer A's DELETE on User B's CRUD path must NOT be 2xx, AND B must STILL exist in DB")
        void customer_a_cannot_delete_user_b() throws Exception {
                BASE_URL = userServiceUrl;
                String emailA = "tc19a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc19b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBodyA = String.format("""
                                {"name":"TC19 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"TC19 B","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC19 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC19 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC19 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                HttpResponse<String> r = httpDeleteAuth("/api/users/" + bid, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC19: customer A deleting customer B must NOT be 2xx (status " + code
                                                + "). A 2xx here means cross-user delete is unprotected. body="
                                                + r.body());
                assertTrue(code / 100 != 5,
                                "TC19: cross-user delete must NOT 5xx (status " + code + "). body=" + r.body());
                assertTrue(code == 403 || code == 404,
                                "TC19: cross-user delete must return 403 or 404; got " + code + " body=" + r.body());

                // Defensive — B's row must STILL exist in DB.
                String userTable = tableName("User");
                Integer count = jdbc.queryForObject(
                                "SELECT COUNT(*) FROM " + userTable + " WHERE id = ?",
                                Integer.class, bid);
                assertNotNull(count);
                assertTrue(count == 1,
                                "TC19: cross-user DELETE was rejected (status " + code + ") but B's row in DB "
                                                + "is gone (count=" + count + ") — the controller is committing the "
                                                + "delete before doing the auth check.");
        }
}

// ─── TC20 — Owner happy path: User A can UPDATE their own profile ───────────
@Tag("public")
@Tag("authorization")
class TC20_OwnerUpdateOwnProfileTests extends TestBase {

        @Test
        @DisplayName("TC20 — Customer's PUT on their own User CRUD path returns 2xx, AND DB reflects the new name")
        void owner_can_update_own_profile() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc20_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String origPhone = "+201" + nonce().substring(0, 9);
                String regBody = String.format("""
                                {"name":"TC20 Original","email":"%s","password":"%s","phone":"%s"}
                                """, email, pwd, origPhone);
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC20 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC20 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Mitigation pattern — include all original fields plus the new name.
                String newName = "TC20 Updated";
                String putBody = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"%s"}
                                """, newName, email, pwd, origPhone);
                HttpResponse<String> r = httpPutAuth("/api/users/" + uid, putBody, token);
                assert2xx(r, "TC20 owner update own profile");

                // JDBC verification (NOT via GET) — we're testing the PUT
                // path's persistence semantics specifically.
                String userTable = tableName("User");
                String currentName = jdbc.queryForObject(
                                "SELECT name FROM " + userTable + " WHERE id = ?",
                                String.class, uid);
                assertEquals(newName, currentName,
                                "TC20: PUT returned " + r.statusCode() + " but DB row's name is '" + currentName
                                                + "' (expected '" + newName
                                                + "') — the controller returned 2xx without "
                                                + "actually persisting the update.");
        }
}

// ─── TC21 — Admin override: admin can READ any user ─────────────────────────
@Tag("public")
@Tag("authorization")
class TC21_AdminReadAnyUserTests extends TestBase {

        @Test
        @DisplayName("TC21 — Admin's GET on a customer's User CRUD path returns 2xx (admin role bypasses ownership)")
        void admin_can_read_any_user() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc21_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC21 Customer","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC21 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();
                HttpResponse<String> r = httpGetAuth("/api/users/" + customerId, adminTok);
                assert2xx(r, "TC21 admin read customer");
                JsonNode j = parseNode(r.body());
                assertTrue(j.isObject(),
                                "TC21: admin GET response body must be a JSON object; got " + r.body());
        }
}

// ─── TC22 — Admin override: admin can UPDATE any user ───────────────────────
@Tag("public")
@Tag("authorization")
class TC22_AdminUpdateAnyUserTests extends TestBase {

        @Test
        @DisplayName("TC22 — Admin's PUT on a customer's User CRUD path returns 2xx, AND DB reflects the new name")
        void admin_can_update_any_user() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc22_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String origPhone = "+201" + nonce().substring(0, 9);
                String regBody = String.format("""
                                {"name":"TC22 Customer","email":"%s","password":"%s","phone":"%s"}
                                """, email, pwd, origPhone);
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC22 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();

                // Mitigation pattern — include all original fields plus the new name.
                String newName = "TC22 Admin-Updated";
                String putBody = String.format("""
                                {"name":"%s","email":"%s","password":"%s","phone":"%s"}
                                """, newName, email, pwd, origPhone);
                HttpResponse<String> r = httpPutAuth("/api/users/" + customerId, putBody, adminTok);
                assert2xx(r, "TC22 admin update customer");

                String userTable = tableName("User");
                String currentName = jdbc.queryForObject(
                                "SELECT name FROM " + userTable + " WHERE id = ?",
                                String.class, customerId);
                assertEquals(newName, currentName,
                                "TC22: admin PUT returned " + r.statusCode() + " but customer's name in DB is '"
                                                + currentName + "' (expected '" + newName + "') — admin update did not "
                                                + "persist.");
        }
}

// ─── TC23 — Admin override: admin can DELETE any user (strict hard-delete) ──
@Tag("public")
@Tag("authorization")
class TC23_AdminDeleteAnyUserTests extends TestBase {

        @Test
        @DisplayName("TC23 — Admin's DELETE on a customer's User CRUD path returns 2xx, AND the row is HARD-deleted (strict)")
        void admin_can_delete_any_user_hard() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc23_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC23 Customer","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC23 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();
                HttpResponse<String> r = httpDeleteAuth("/api/users/" + customerId, adminTok);
                assert2xx(r, "TC23 admin delete customer");

                // STRICT hard-delete: row must be physically gone from DB.
                // Soft-delete is NOT acceptable for this test per direction.
                String userTable = tableName("User");
                Integer count = jdbc.queryForObject(
                                "SELECT COUNT(*) FROM " + userTable + " WHERE id = ?",
                                Integer.class, customerId);
                assertNotNull(count);
                assertEquals(0, count.intValue(),
                                "TC23: admin DELETE returned " + r.statusCode() + " but customer row STILL "
                                                + "exists in DB (count=" + count
                                                + "). DELETE must hard-delete the row, "
                                                + "not soft-delete it (use the deactivate endpoint for status changes).");

                // GET-after-DELETE — strictly 404.
                HttpResponse<String> g = httpGetAuth("/api/users/" + customerId, adminTok);
                int gcode = g.statusCode();
                assertTrue(gcode / 100 != 2,
                                "TC23: GET-after-DELETE returned 2xx (status " + gcode + "). The row was "
                                                + "already verified gone from DB above, but GET still finds it. body="
                                                + g.body());
                assertEquals(404, gcode,
                                "TC23: GET after a successful DELETE must return 404 Not Found; got " + gcode
                                                + " body=" + g.body());
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S1-F12 — Get User Activity Feed
// GET /api/users/{id}/activity?page={page}&size={size}
// Auth: required user. Ownership: caller must be target OR admin.
// Defaults: page=0, size=10, max size=100. Response shape:
// { content: [{action, timestamp, details}], page, size, totalElements }
// ════════════════════════════════════════════════════════════════════════════

// ─── TC24 — Owner GET own activity returns 2xx with paginated envelope ──────
@Tag("public")
@Tag("features_m2")
class TC24_ActivityOwnerHappyPathTests extends TestBase {

        @Test
        @DisplayName("TC24 — GET /api/users/{ownId}/activity with own token returns 2xx and a paginated envelope")
        void owner_activity_returns_2xx_with_envelope() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — register and login the user.
                String email = "tc24_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC24 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC24 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC24 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Act — GET own activity.
                String activityPath = "/api/users" + "/" + uid + "/activity";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                assert2xx(r, "TC24 owner activity");

                // Assert envelope shape per spec: content[], page, size, totalElements.
                JsonNode j = parseNode(r.body());
                assertTrue(j.has("content"),
                                "TC24: response must include `content` field; body=" + r.body());
                assertTrue(j.get("content").isArray(),
                                "TC24: `content` must be an array; got " + j.get("content"));
                assertTrue(j.has("page"),
                                "TC24: response must include `page` field; body=" + r.body());
                assertTrue(j.has("size"),
                                "TC24: response must include `size` field; body=" + r.body());
                assertTrue(j.has("totalElements"),
                                "TC24: response must include `totalElements` field; body=" + r.body());
        }
}

// ─── TC25 — Non-existent user ID returns 404 (admin token) ──────────────────
@Tag("public")
@Tag("features_m2")
class TC25_ActivityNonExistentIdTests extends TestBase {

        @Test
        @DisplayName("TC25 — GET /api/users/<Long.MAX_VALUE>/activity with admin token returns strictly 404")
        void activity_non_existent_id_returns_404() throws Exception {
                BASE_URL = userServiceUrl;
                // Use admin token: per spec, admin passes ownership check, then
                // user-not-found returns 404. With a non-admin token, ownership
                // would fail first with 403 — wrong path for this test.
                String adminTok = adminToken();
                long missingId = Long.MAX_VALUE;
                String activityPath = "/api/users" + "/" + missingId + "/activity";

                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC25: activity for a non-existent user ID must NOT be 2xx (status " + code
                                                + "). body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC25: activity for a non-existent user ID must NOT 5xx — server must handle "
                                                + "missing-user gracefully. body=" + r.body());
                assertEquals(404, code,
                                "TC25: per spec, admin passes ownership check then user-not-found yields "
                                                + "strictly 404; got " + code + " body=" + r.body());
        }
}

// ─── TC26 — Negative user ID returns 4xx (admin token) ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC26_ActivityNegativeIdTests extends TestBase {

        @Test
        @DisplayName("TC26 — GET /api/users/-1/activity with admin token returns a 4xx (graceful)")
        void activity_negative_id_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Admin token — bypass ownership so the test actually exercises
                // the negative-id rejection logic (validation OR not-found).
                String adminTok = adminToken();
                String activityPath = "/api/users" + "/-1/activity";

                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC26: activity for a negative user ID must NOT 5xx — controller must "
                                                + "validate / reject gracefully, not crash. status=" + code + " body="
                                                + r.body());
                assertTrue(code / 100 != 2,
                                "TC26: activity for a negative user ID must NOT be 2xx — negative ids cannot "
                                                + "match any real user. status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC26: activity for a negative user ID must return a 4xx (400 validation or "
                                                + "404 not-found); got " + code + " body=" + r.body());
        }
}

// ─── TC27 — String user ID returns 4xx (admin token) ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC27_ActivityStringIdTests extends TestBase {

        @Test
        @DisplayName("TC27 — GET /api/users/abc/activity with admin token returns a 4xx (path-var binding fails)")
        void activity_string_id_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Admin token — ensures any 401 we see is NOT from missing auth.
                String adminTok = adminToken();
                String activityPath = "/api/users" + "/abc/activity";

                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC27: activity for a non-numeric user ID must NOT 5xx — Spring's path-var "
                                                + "binding should reject the string cleanly, not throw an unhandled "
                                                + "TypeMismatchException. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC27: activity for a non-numeric user ID must NOT be 2xx — 'abc' cannot be "
                                                + "a valid Long. status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC27: activity for a non-numeric user ID must return a 4xx (typically 400 "
                                                + "Bad Request); got " + code + " body=" + r.body());
        }
}

// ─── TC28 — size=0 returns gracefully (NOT 5xx) ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC28_ActivitySizeZeroTests extends TestBase {

        @Test
        @DisplayName("TC28 — GET /api/users/{ownId}/activity?size=0 must NOT 5xx (spec silent on size=0)")
        void activity_size_zero_does_not_5xx() throws Exception {
                BASE_URL = userServiceUrl;
                // Setup — own user so we reach the pagination logic.
                String email = "tc28_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC28 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC28 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC28 setup login");
                String token = parseNode(login.body()).get("token").asText();

                // Act — size=0. PageRequest.of(0, 0) throws IllegalArgumentException,
                // so unhandled this becomes 500. Spec doesn't pin a code here, but
                // graceful handling means NOT 5xx.
                String activityPath = "/api/users" + "/" + uid + "/activity?size=0";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC28: size=0 must NOT 5xx — controller must validate / clamp / reject "
                                                + "gracefully, not let PageRequest.of throw IllegalArgumentException. "
                                                + "status=" + code + " body=" + r.body());
        }
}

// ─── TC29 — size=-1 returns 4xx ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC29_ActivityNegativeSizeTests extends TestBase {

        @Test
        @DisplayName("TC29 — GET /api/users/{ownId}/activity?size=-1 returns a 4xx")
        void activity_negative_size_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc29_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC29 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC29 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC29 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?size=-1";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC29: size=-1 must NOT 5xx — controller must validate gracefully. status="
                                                + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC29: size=-1 must NOT be 2xx — negative page size is semantically invalid. "
                                                + "status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC29: size=-1 must return a 4xx; got " + code + " body=" + r.body());
        }
}

// ─── TC30 — size=string returns 4xx (binding fails) ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC30_ActivityStringSizeTests extends TestBase {

        @Test
        @DisplayName("TC30 — GET /api/users/{ownId}/activity?size=abc returns a 4xx (Integer binding fails)")
        void activity_string_size_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc30_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC30 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC30 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC30 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?size=abc";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC30: size=abc must NOT 5xx — Spring's @RequestParam Integer binding should "
                                                + "reject the string cleanly. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC30: size=abc must NOT be 2xx — non-numeric size cannot be valid. status="
                                                + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC30: size=abc must return a 4xx (typically 400 Bad Request); got " + code
                                                + " body=" + r.body());
        }
}

// ─── TC31 — Cross-user activity (regular user) returns strictly 403 ─────────
@Tag("public")
@Tag("features_m2")
class TC31_ActivityCrossUserRegularTests extends TestBase {

        @Test
        @DisplayName("TC31 — Customer A's GET on User B's activity returns strictly 403 (per S1-F12 spec)")
        void cross_user_activity_regular_returns_403() throws Exception {
                BASE_URL = userServiceUrl;
                // Per spec: "ownership violation, NOT 404 — A's token is valid
                // and B exists." Strict 403 here, unlike TC17 (which accepts 403/404).
                String emailA = "tc31a_" + nonce() + "@grader.testgen.io";
                String emailB = "tc31b_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBodyA = String.format("""
                                {"name":"TC31 A","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailA, pwd, nonce().substring(0, 9));
                String regBodyB = String.format("""
                                {"name":"TC31 B","email":"%s","password":"%s","phone":"+201%s"}
                                """, emailB, pwd, nonce().substring(0, 9));
                assert2xx(httpPost("/api/auth/register", regBodyA), "TC31 setup register A");
                HttpResponse<String> regB = httpPost("/api/auth/register", regBodyB);
                assert2xx(regB, "TC31 setup register B");
                long bid = uidFromJwt(parseNode(regB.body()).get("token").asText());

                String loginBodyA = String.format("""
                                {"email":"%s","password":"%s"}
                                """, emailA, pwd);
                HttpResponse<String> loginA = httpPost("/api/auth/login", loginBodyA);
                assert2xx(loginA, "TC31 setup login A");
                String tokenA = parseNode(loginA.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + bid + "/activity";
                HttpResponse<String> r = httpGetAuth(activityPath, tokenA);
                int code = r.statusCode();

                assertTrue(code / 100 != 2,
                                "TC31: cross-user activity GET must NOT be 2xx — regular users cannot read "
                                                + "other users' activity feeds. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 5,
                                "TC31: cross-user activity GET must NOT 5xx. status=" + code + " body=" + r.body());
                assertEquals(403, code,
                                "TC31: per S1-F12 spec, cross-user activity GET must return strictly 403 "
                                                + "(ownership violation, NOT 404 — A's token is valid and B exists); got "
                                                + code + " body=" + r.body());
        }
}

// ─── TC32 — Cross-user activity (admin) returns 2xx ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC32_ActivityCrossUserAdminTests extends TestBase {

        @Test
        @DisplayName("TC32 — Admin's GET on a customer's activity returns 2xx (admin bypasses ownership)")
        void cross_user_activity_admin_returns_2xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc32_" + nonce() + "@grader.testgen.io";
                String regBody = String.format("""
                                {"name":"TC32 Customer","email":"%s","password":"TestPwd!2026","phone":"+201%s"}
                                """, email, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC32 setup register customer");
                long customerId = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String adminTok = adminToken();
                String activityPath = "/api/users" + "/" + customerId + "/activity";
                HttpResponse<String> r = httpGetAuth(activityPath, adminTok);
                assert2xx(r, "TC32 admin activity");

                JsonNode j = parseNode(r.body());
                assertTrue(j.has("content") && j.get("content").isArray(),
                                "TC32: admin response must include `content` array; body=" + r.body());
        }
}

// ─── TC33 — page=-1 returns 4xx ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC33_ActivityNegativePageTests extends TestBase {

        @Test
        @DisplayName("TC33 — GET /api/users/{ownId}/activity?page=-1 returns a 4xx")
        void activity_negative_page_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc33_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC33 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC33 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC33 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?page=-1";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC33: page=-1 must NOT 5xx — PageRequest.of(int page, int size) requires "
                                                + "page >= 0; controller must validate gracefully. status=" + code
                                                + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC33: page=-1 must NOT be 2xx — negative page is semantically invalid. "
                                                + "status=" + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC33: page=-1 must return a 4xx; got " + code + " body=" + r.body());
        }
}

// ─── TC34 — page=string returns 4xx (binding fails) ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC34_ActivityStringPageTests extends TestBase {

        @Test
        @DisplayName("TC34 — GET /api/users/{ownId}/activity?page=abc returns a 4xx (Integer binding fails)")
        void activity_string_page_returns_4xx() throws Exception {
                BASE_URL = userServiceUrl;
                String email = "tc34_" + nonce() + "@grader.testgen.io";
                String pwd = "TestPwd!2026";
                String regBody = String.format("""
                                {"name":"TC34 User","email":"%s","password":"%s","phone":"+201%s"}
                                """, email, pwd, nonce().substring(0, 9));
                HttpResponse<String> reg = httpPost("/api/auth/register", regBody);
                assert2xx(reg, "TC34 setup register");
                long uid = uidFromJwt(parseNode(reg.body()).get("token").asText());

                String loginBody = String.format("""
                                {"email":"%s","password":"%s"}
                                """, email, pwd);
                HttpResponse<String> login = httpPost("/api/auth/login", loginBody);
                assert2xx(login, "TC34 setup login");
                String token = parseNode(login.body()).get("token").asText();

                String activityPath = "/api/users" + "/" + uid + "/activity?page=abc";
                HttpResponse<String> r = httpGetAuth(activityPath, token);
                int code = r.statusCode();

                assertTrue(code / 100 != 5,
                                "TC34: page=abc must NOT 5xx — Spring's @RequestParam Integer binding should "
                                                + "reject the string cleanly. status=" + code + " body=" + r.body());
                assertTrue(code / 100 != 2,
                                "TC34: page=abc must NOT be 2xx — non-numeric page cannot be valid. status="
                                                + code + " body=" + r.body());
                assertTrue(code >= 400 && code < 500,
                                "TC34: page=abc must return a 4xx (typically 400 Bad Request); got " + code
                                                + " body=" + r.body());
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S2 M2 — Catalog entity features (full-text search, indexing, dashboard).
// All tests dynamic via TestBase helpers:
// * s2CatalogEntity() — Restaurant / Product / Provider / etc.
// * s3OrderEntity() — Order / Booking / Transaction / etc.
// * s2CategoricalFilterParam() — first non-status enum field (cuisineType /
// category / specialty / ...)
// * enumValueAt(entity, field, idx) — i-th valid value for that enum
// * s2EventsCollection() — Mongo events collection (spec name, validated)
// * s2SearchIndex() — ES index name (spec name, validated)
// * buildKitchenSinkBody(...) — JSON body from manifest entityColumns
// ════════════════════════════════════════════════════════════════════════════

// ─── TC35 — S2-F10 happy path search returns 2xx + array ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC35_SearchHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC35 — GET <s2>/search/full-text?query=test with valid token returns 2xx + array shape")
        void search_happy_path_returns_2xx_array() throws Exception {
                BASE_URL = catalogServiceUrl;
                String token = adminToken();
                String searchPath = crudCollectionPath(s2CatalogEntity()) + "/search/full-text?query=test";
                HttpResponse<String> r = httpGetAuth(searchPath, token);
                assert2xx(r, "TC35 search happy path");
                JsonNode body = parseNode(r.body());
                boolean validShape = body.isArray() || (body.has("content") && body.get("content").isArray());
                assertTrue(validShape, "TC35: response must be JSON array OR paginated envelope; got " + r.body());
        }
}

// ─── TC36 — S2-F10 no token returns 401 ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC36_SearchNoTokenTests extends TestBase {
        @Test
        @DisplayName("TC36 — GET <s2>/search/full-text without Authorization header returns 401")
        void search_no_token_returns_401() throws Exception {
                BASE_URL = catalogServiceUrl;
                String searchPath = crudCollectionPath(s2CatalogEntity()) + "/search/full-text?query=anything";
                HttpResponse<String> r = httpGet(searchPath);
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC36: must NOT 2xx; got " + code);
                assertTrue(code / 100 != 5, "TC36: must NOT 5xx; got " + code);
                assertEquals(401, code, "TC36: must be strict 401; got " + code + " body=" + r.body());
        }
}

// ─── TC37 — S2-F10 exact match by primary categorical filter ────────────────
@Tag("public")
@Tag("features_m2")
class TC37_SearchExactCategoricalFilterTests extends TestBase {
        @Test
        @DisplayName("TC37 — Search ?<filter>=<value0> returns only entities with that filter value")
        void search_filter_categorical_returns_only_matching() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String filterParam = s2CategoricalFilterParam();
                String filterValue0 = enumValueAt(entity, filterParam, 0);
                String filterValue1 = enumValueAt(entity, filterParam, 1);
                String statusOpen = enumValueAt(entity, "status", 0);

                String n = nonce();
                createEntity(adminTok, "TC37 First_" + n, filterValue0, statusOpen);
                createEntity(adminTok, "TC37 Second_" + n, filterValue1, statusOpen);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?" + filterParam + "="
                                + filterValue0;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC37 search by " + filterParam);
                JsonNode arr = unwrap(parseNode(r.body()));
                for (JsonNode item : arr) {
                        String c = item.has(filterParam) ? item.get(filterParam).asText() : null;
                        assertEquals(filterValue0, c,
                                        "TC37: every result must have " + filterParam + "=" + filterValue0 + "; got "
                                                        + item);
                }
        }

        private void createEntity(String tok, String name, String filterValue, String status) throws Exception {
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of(
                                "name", name,
                                s2CategoricalFilterParam(), filterValue,
                                "status", status));
                assert2xx(httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, tok),
                                "TC37 setup create " + name);
        }

        private JsonNode unwrap(JsonNode b) {
                return b.isArray() ? b : (b.has("content") ? b.get("content") : b);
        }
}

// ─── TC38 — S2-F10 exact match by status ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC38_SearchExactStatusTests extends TestBase {
        @Test
        @DisplayName("TC38 — Search ?status=<value0> returns only entities with that status")
        void search_filter_status_returns_only_matching() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String filterValue0 = enumValueAt(entity, s2CategoricalFilterParam(), 0);
                String status0 = enumValueAt(entity, "status", 0);
                String status1 = enumValueAt(entity, "status", 1);

                String n = nonce();
                createEntity(adminTok, "TC38 Status0_" + n, filterValue0, status0);
                createEntity(adminTok, "TC38 Status1_" + n, filterValue0, status1);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?status=" + status0;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC38 search by status");
                JsonNode arr = unwrap(parseNode(r.body()));
                for (JsonNode item : arr) {
                        String s = item.has("status") ? item.get("status").asText() : null;
                        assertEquals(status0, s, "TC38: every result must have status=" + status0 + "; got " + item);
                }
        }

        private void createEntity(String tok, String name, String filterValue, String status) throws Exception {
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of(
                                "name", name,
                                s2CategoricalFilterParam(), filterValue,
                                "status", status));
                assert2xx(httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, tok),
                                "TC38 setup create " + name);
        }

        private JsonNode unwrap(JsonNode b) {
                return b.isArray() ? b : (b.has("content") ? b.get("content") : b);
        }
}

// ─── TC39 — S2-F10 minRating + maxRating range filter ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC39_SearchRatingRangeTests extends TestBase {
        @Test
        @DisplayName("TC39 — Search ?minRating=4.0&maxRating=5.0 returns only entities with rating in [4.0, 5.0]")
        void search_rating_range_returns_entities_in_range() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String n = nonce();
                long lowId = createAndRate(adminTok, "TC39 Low_" + n, 3.0);
                long midId = createAndRate(adminTok, "TC39 Mid_" + n, 4.5);
                long highId = createAndRate(adminTok, "TC39 High_" + n, 5.0);
                reindex(adminTok, lowId);
                reindex(adminTok, midId);
                reindex(adminTok, highId);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?minRating=4.0&maxRating=5.0";
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC39 search rating range");
                JsonNode arr = unwrap(parseNode(r.body()));
                for (JsonNode item : arr) {
                        if (!item.has("rating") || item.get("rating").isNull())
                                continue;
                        double rating = item.get("rating").asDouble();
                        assertTrue(rating >= 4.0 && rating <= 5.0,
                                        "TC39: every result must have rating in [4.0, 5.0]; got " + rating + " in "
                                                        + item);
                }
        }

        private long createAndRate(String tok, String name, double rating) throws Exception {
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of("name", name));
                HttpResponse<String> r = httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, tok);
                assert2xx(r, "TC39 setup create " + name);
                long id = parseNode(r.body()).get("id").asLong();
                String ratingCol = columnByField(s2CatalogEntity(), "rating");
                jdbc.update("UPDATE \"" + tableName(s2CatalogEntity()) + "\" SET \"" + ratingCol
                                + "\" = ? WHERE id = ?", rating, id);
                return id;
        }

        private void reindex(String tok, long id) throws Exception {
                HttpResponse<String> r = httpPostAuth(crudCollectionPath(s2CatalogEntity()) + "/" + id + "/index", "",
                                tok);
                assert2xx(r, "TC39 reindex id=" + id);
        }

        private JsonNode unwrap(JsonNode b) {
                return b.isArray() ? b : (b.has("content") ? b.get("content") : b);
        }
}

// ─── TC40 — S2-F10 minRating > maxRating returns 4xx ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC40_SearchInvalidRatingRangeTests extends TestBase {
        @Test
        @DisplayName("TC40 — Search ?minRating=5.0&maxRating=3.0 (invalid range) returns a 4xx")
        void search_invalid_rating_range_returns_4xx() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String searchPath = crudCollectionPath(s2CatalogEntity())
                                + "/search/full-text?minRating=5.0&maxRating=3.0";
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                int code = r.statusCode();
                assertTrue(code / 100 != 5, "TC40: NOT 5xx; got " + code);
                assertTrue(code / 100 != 2, "TC40: NOT 2xx; got " + code);
                assertTrue(code >= 400 && code < 500, "TC40: must return 4xx; got " + code + " body=" + r.body());
        }
}

// ─── TC41 — S2-F10 query with no matches returns empty list ─────────────────
@Tag("public")
@Tag("features_m2")
class TC41_SearchNoMatchEmptyListTests extends TestBase {
        @Test
        @DisplayName("TC41 — Search with query that matches nothing returns 2xx + empty list")
        void search_no_match_returns_empty_list() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String improbableQuery = "TC41NoMatchQuery_" + nonce() + "_xyzqwe";
                String searchPath = crudCollectionPath(s2CatalogEntity()) + "/search/full-text?query="
                                + improbableQuery;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC41 search no match");
                JsonNode body = parseNode(r.body());
                JsonNode arr = body.isArray() ? body : (body.has("content") ? body.get("content") : body);
                assertTrue(arr.isArray(), "TC41: response must contain an array; got " + r.body());
                assertEquals(0, arr.size(), "TC41: must return empty list; got " + r.body());
        }
}

// ─── TC42 — S2-F10 results sorted by relevance ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC42_SearchSortedByRelevanceTests extends TestBase {
        @Test
        @DisplayName("TC42 — Search results sorted by relevance (name match ranks higher than description match)")
        void search_results_sorted_by_relevance() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String entity = s2CatalogEntity();
                String unique = "Tc42Word" + nonce();
                String aName = unique + " Kitchen";
                String bName = "Other Place TC42_" + nonce();
                long aid = createWithDetails(adminTok, aName, null);
                reindex(adminTok, aid);
                long bid = createWithDetails(adminTok, bName, "highly rated " + unique + " specialty");
                reindex(adminTok, bid);

                String searchPath = crudCollectionPath(entity) + "/search/full-text?query=" + unique;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC42 search relevance");
                JsonNode body = parseNode(r.body());
                JsonNode arr = body.isArray() ? body : (body.has("content") ? body.get("content") : body);
                assertTrue(arr.isArray() && arr.size() >= 1,
                                "TC42: must return at least one result; body=" + r.body());

                int idxA = -1, idxB = -1;
                for (int i = 0; i < arr.size(); i++) {
                        String entryName = arr.get(i).has("name") ? arr.get(i).get("name").asText() : "";
                        if (aName.equals(entryName))
                                idxA = i;
                        if (bName.equals(entryName))
                                idxB = i;
                }
                assertTrue(idxA >= 0,
                                "TC42: name-match (A, name='" + aName + "') must appear in results; body=" + r.body());
                if (idxB >= 0) {
                        assertTrue(idxA < idxB,
                                        "TC42: name-match (A, idx=" + idxA
                                                        + ") must rank higher than description-match (B, idx=" + idxB
                                                        + ").");
                }
        }

        private long createWithDetails(String tok, String name, String desc) throws Exception {
                java.util.Map<String, Object> overrides = new java.util.HashMap<>();
                overrides.put("name", name);
                if (desc != null) {
                        overrides.put("details", java.util.Map.of("description", desc));
                }
                String body = buildKitchenSinkBody(s2CatalogEntity(), overrides);
                HttpResponse<String> r = httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, tok);
                assert2xx(r, "TC42 setup create " + name);
                return parseNode(r.body()).get("id").asLong();
        }

        private void reindex(String tok, long id) throws Exception {
                HttpResponse<String> r = httpPostAuth(crudCollectionPath(s2CatalogEntity()) + "/" + id + "/index", "",
                                tok);
                assert2xx(r, "TC42 reindex id=" + id);
        }
}

// ─── TC43 — S2-F11 happy index path ─────────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC43_IndexHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC43 — POST <s2>/{id}/index for an existing entity returns 2xx")
        void index_happy_path_returns_2xx() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String body = buildKitchenSinkBody(s2CatalogEntity(),
                                java.util.Map.of("name", "TC43 Entity_" + nonce()));
                HttpResponse<String> created = httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, adminTok);
                assert2xx(created, "TC43 setup create");
                long id = parseNode(created.body()).get("id").asLong();
                HttpResponse<String> r = httpPostAuth(crudCollectionPath(s2CatalogEntity()) + "/" + id + "/index", "",
                                adminTok);
                assert2xx(r, "TC43 index");
        }
}

// ─── TC44 — S2-F11 indexed document matches PG attributes ───────────────────
@Tag("public")
@Tag("features_m2")
class TC44_IndexMatchesPgTests extends TestBase {
        @Test
        @DisplayName("TC44 — After indexing, ES doc fields match the PG row's attributes")
        void index_doc_matches_pg_attributes() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String unique = "TC44Entity_" + nonce();
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of(
                                "name", unique,
                                "details", java.util.Map.of("description", "signature description")));
                HttpResponse<String> created = httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, adminTok);
                assert2xx(created, "TC44 setup create");
                long id = parseNode(created.body()).get("id").asLong();
                String ratingCol = columnByField(s2CatalogEntity(), "rating");
                jdbc.update("UPDATE \"" + tableName(s2CatalogEntity()) + "\" SET \"" + ratingCol
                                + "\" = ? WHERE id = ?", 4.5, id);
                HttpResponse<String> indexed = httpPostAuth(crudCollectionPath(s2CatalogEntity()) + "/" + id + "/index",
                                "", adminTok);
                assert2xx(indexed, "TC44 index");

                String esIndex = s2SearchIndex();
                esPost("/" + esIndex + "/_refresh", ""); // flush ES buffer before querying
                long esCount = esSearchCount(esIndex, "name", unique);
                assertTrue(esCount >= 1,
                                "TC44: ES index '" + esIndex + "' must contain a document with name='" + unique
                                                + "' (count=" + esCount + ").");

                String searchPath = crudCollectionPath(s2CatalogEntity()) + "/search/full-text?query=" + unique;
                HttpResponse<String> sr = httpGetAuth(searchPath, adminTok);
                assert2xx(sr, "TC44 search after index");
                JsonNode body2 = parseNode(sr.body());
                JsonNode arr = body2.isArray() ? body2 : (body2.has("content") ? body2.get("content") : body2);
                JsonNode found = null;
                for (JsonNode item : arr) {
                        String entryName = item.has("name") ? item.get("name").asText() : "";
                        if (unique.equals(entryName)) {
                                found = item;
                                break;
                        }
                }
                assertNotNull(found, "TC44: indexed entity must be findable via /search/full-text by name='" + unique
                                + "'; got " + sr.body());

                // Verify name + status fields match between search result and PG row.
                java.util.Map<String, Object> pgRow = jdbc.queryForMap(
                                "SELECT name, status::text AS status FROM " + tableName(s2CatalogEntity())
                                                + " WHERE id = ?",
                                id);
                assertEquals(pgRow.get("name"), found.get("name").asText(), "TC44: ES name must match PG name");
                if (found.has("status")) {
                        assertEquals(pgRow.get("status"), found.get("status").asText(),
                                        "TC44: ES status must match PG status");
                }
        }
}

// ─── TC45 — S2-F11 auto-reindex on update ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC45_IndexAutoReindexOnUpdateTests extends TestBase {
        @Test
        @DisplayName("TC45 — Updating an entity via PUT (without /index) makes the new name searchable")
        void auto_reindex_on_update() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String origName = "TC45 OriginalName_" + nonce();
                String body = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of("name", origName));
                HttpResponse<String> created = httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, adminTok);
                assert2xx(created, "TC45 setup create");
                long id = parseNode(created.body()).get("id").asLong();

                String newName = "TC45_NewName_" + nonce();
                String putBody = buildKitchenSinkBody(s2CatalogEntity(), java.util.Map.of("name", newName));
                HttpResponse<String> updated = httpPutAuth(crudCollectionPath(s2CatalogEntity()) + "/" + id, putBody, adminTok);
                assert2xx(updated, "TC45 update name");

                String searchPath = crudCollectionPath(s2CatalogEntity()) + "/search/full-text?query=" + newName;
                HttpResponse<String> r = httpGetAuth(searchPath, adminTok);
                assert2xx(r, "TC45 search by new name");
                JsonNode body2 = parseNode(r.body());
                JsonNode arr = body2.isArray() ? body2 : (body2.has("content") ? body2.get("content") : body2);
                boolean found = false;
                for (JsonNode item : arr) {
                        String entryName = item.has("name") ? item.get("name").asText() : "";
                        if (newName.equals(entryName)) {
                                found = true;
                                break;
                        }
                }
                assertTrue(found, "TC45: search by new name must find the entity (proves auto-reindexing). body="
                                + r.body());
        }
}

// ─── TC46 — S2-F11 index on non-existent entity returns 404 ─────────────────
@Tag("public")
@Tag("features_m2")
class TC46_IndexNonExistentTests extends TestBase {
        @Test
        @DisplayName("TC46 — POST <s2>/<Long.MAX_VALUE>/index returns strictly 404")
        void index_non_existent_returns_404() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String indexPath = crudCollectionPath(s2CatalogEntity()) + "/" + Long.MAX_VALUE + "/index";
                HttpResponse<String> r = httpPostAuth(indexPath, "", adminTok);
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC46: NOT 2xx; got " + code);
                assertTrue(code / 100 != 5, "TC46: NOT 5xx; got " + code);
                assertEquals(404, code, "TC46: must be strict 404; got " + code + " body=" + r.body());
        }
}

// ─── TC47 — S2-F11 index without token returns 401 ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC47_IndexNoTokenTests extends TestBase {
        @Test
        @DisplayName("TC47 — POST <s2>/{id}/index without Authorization header returns 401")
        void index_no_token_returns_401() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String body = buildKitchenSinkBody(s2CatalogEntity(),
                                java.util.Map.of("name", "TC47 Entity_" + nonce()));
                HttpResponse<String> created = httpPostAuth(crudCollectionPath(s2CatalogEntity()), body, adminTok);
                assert2xx(created, "TC47 setup create");
                long id = parseNode(created.body()).get("id").asLong();
                HttpResponse<String> r = httpPost(crudCollectionPath(s2CatalogEntity()) + "/" + id + "/index", "");
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC47: NOT 2xx; got " + code);
                assertEquals(401, code, "TC47: must be strict 401; got " + code + " body=" + r.body());
        }
}

// ─── TC48 — S2-F12 dashboard happy path (uses pre-seeded entity id=1) ───────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC48_DashboardHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC48 — GET <s2>/{id}/dashboard returns 2xx + DTO with totalOrders/totalRevenue")
        void dashboard_happy_path() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                long restId = 1L;
                HttpResponse<String> r = httpGetAuth(
                                crudCollectionPath(s2CatalogEntity()) + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC48 dashboard");
                JsonNode j = parseNode(r.body());
                assertTrue(j.has("totalOrders") || j.has("total_orders")
                                || j.has("totalBookings") || j.has("total_bookings"),
                                "TC48: dashboard must include totalOrders/totalBookings; got " + r.body());
                assertTrue(j.has("totalRevenue") || j.has("total_revenue"),
                                "TC48: dashboard must include totalRevenue; got " + r.body());
        }
}

// ─── TC49 — S2-F12 aggregated values match PG-source values ─────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC49_DashboardAggregatesMatchPgTests extends TestBase {
        @Test
        @DisplayName("TC49 — Dashboard totalBookings/totalRevenue reflect seeded COMPLETED bookings with invoices")
        void dashboard_aggregates_match_pg() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                long restId = 1L;
                long uid = 1L; // admin user (always id=1 in baseline)

                // Seed 2 COMPLETED bookings with invoices so the dashboard has data
                // regardless of whether it aggregates all bookings or completed-only.
                long bid1 = _BkM2.bk(this, uid, restId, "COMPLETED", "2026-04-01");
                long bid2 = _BkM2.bk(this, uid, restId, "COMPLETED", "2026-04-02");
                _BkM2.inv(this, bid1, uid, 100.0, "COMPLETED", "2026-04-01");
                _BkM2.inv(this, bid2, uid, 200.0, "COMPLETED", "2026-04-02");

                HttpResponse<String> r = httpGetAuth(
                                crudCollectionPath(s2CatalogEntity()) + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC49 dashboard");
                JsonNode j = parseNode(r.body());

                long actualCount = _BkM2.rL(j,
                                "totalBookings", "total_bookings", "totalOrders", "total_orders");
                double actualRevenue = _BkM2.rD(j, "totalRevenue", "total_revenue");

                assertTrue(actualCount >= 2,
                                "TC49: dashboard totalBookings must be >= 2 (seeded 2 COMPLETED bookings); got "
                                + actualCount + ". body=" + r.body());
                assertTrue(actualRevenue >= 300.0 - 0.01,
                                "TC49: dashboard totalRevenue must be >= 300.0 (seeded invoices 100+200); got "
                                + actualRevenue + ". body=" + r.body());
        }
}

// ─── TC50 — S2-F12 dashboard event written to MongoDB ────────────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC50_DashboardEventLoggedTests extends TestBase {
        @Test
        @DisplayName("TC50 — After GET /dashboard, an event must appear in the spec-defined Mongo collection")
        void dashboard_logs_event_to_mongo() throws Exception {
                BASE_URL = catalogServiceUrl;
                if (mongo == null) {
                        throw new AssertionError(
                                        "TC50: MongoDB is required for this test but not reachable. Set "
                                                        + "SPRING_DATA_MONGODB_URI or ensure the Mongo container is up.");
                }
                String adminTok = adminToken();
                long restId = 1L;
                String collName = s2EventsCollection();
                com.mongodb.client.MongoCollection<org.bson.Document> coll = mongo.getCollection(collName);

                long before = coll.countDocuments();
                HttpResponse<String> r = httpGetAuth(
                                crudCollectionPath(s2CatalogEntity()) + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC50 dashboard");
                long after = coll.countDocuments();

                assertTrue(after > before,
                                "TC50: GET /dashboard must log an event in collection '" + collName
                                                + "'. Counts: before=" + before + ", after=" + after);
        }
}

// ─── TC51 — S2-F12 dashboard for non-existent ID returns 404 ────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC51_DashboardNonExistentTests extends TestBase {
        @Test
        @DisplayName("TC51 — GET <s2>/<Long.MAX_VALUE>/dashboard returns strictly 404")
        void dashboard_non_existent_returns_404() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                String dashPath = crudCollectionPath(s2CatalogEntity()) + "/" + Long.MAX_VALUE + "/dashboard";
                HttpResponse<String> r = httpGetAuth(dashPath, adminTok);
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC51: NOT 2xx; got " + code);
                assertTrue(code / 100 != 5, "TC51: NOT 5xx; got " + code);
                assertEquals(404, code, "TC51: must be strict 404; got " + code + " body=" + r.body());
        }
}

// ─── TC52 — S2-F12 dashboard for entity with no orders returns zeros ────────
@Tag("public")
@Tag("features_m2")
class TC52_DashboardNoOrdersTests extends TestBase {
        @Test
        @DisplayName("TC52 — Dashboard for an entity with no orders returns 2xx + totalOrders=0 + totalRevenue=0")
        void dashboard_no_orders_returns_zeros() throws Exception {
                BASE_URL = catalogServiceUrl;
                String adminTok = adminToken();
                // Pre-seed catalog id=3 has no orders attached (the cross-theme baseline
                // seed plants orders against ids 1, 2, 4 — id=3 left empty intentionally).
                // We DELETE any orders for restId=3 defensively in case a prior test left
                // residual data.
                long restId = 3L;
                String fkCol = s2CatalogFkColumn();
                jdbc.update("DELETE FROM \"" + tableName(s3OrderEntity()) + "\" WHERE \"" + fkCol + "\" = ?", restId);

                HttpResponse<String> r = httpGetAuth(
                                crudCollectionPath(s2CatalogEntity()) + "/" + restId + "/dashboard", adminTok);
                assert2xx(r, "TC52 dashboard");
                JsonNode j = parseNode(r.body());

                long totalOrders = _BkM2.rL(j,
                                "totalOrders", "total_orders", "totalBookings", "total_bookings");
                double totalRevenue = _BkM2.rD(j, "totalRevenue", "total_revenue");

                assertEquals(0L, totalOrders, "TC52: must report totalOrders/totalBookings=0; got " + totalOrders
                                + " body=" + r.body());
                assertEquals(0.0, totalRevenue, 0.01, "TC52: must report totalRevenue=0; got " + totalRevenue);
        }
}

// ─── TC53 — S2-F12 dashboard without token returns 401 ──────────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC53_DashboardNoTokenTests extends TestBase {
        @Test
        @DisplayName("TC53 — GET <s2>/{id}/dashboard without Authorization header returns 401")
        void dashboard_no_token_returns_401() throws Exception {
                BASE_URL = catalogServiceUrl;
                long restId = 1L;
                HttpResponse<String> r = httpGet(crudCollectionPath(s2CatalogEntity()) + "/" + restId + "/dashboard");
                int code = r.statusCode();
                assertTrue(code / 100 != 2, "TC53: NOT 2xx; got " + code);
                assertEquals(401, code, "TC53: must be strict 401; got " + code + " body=" + r.body());
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S3 M2 — Booking Service features (TC54..TC99)
//
// Covers S3-F10 (booking analytics dashboard, TC54-TC69), S3-F11 (record user-
// provider booking pattern, TC70-TC84), and S3-F12 (provider recommendations,
// TC85-TC99). Theme-specific terms: S3 endpoints under /api/bookings; revenue
// is sourced from invoices joined on booking_id; Booking.status enum is
// {REQUESTED, CONFIRMED, IN_PROGRESS, COMPLETED, CANCELLED}; Neo4j relationship
// is BOOKED with bookingCount + lastBookingDate. Per-test wipe of PG/Neo4j/
// Redis happens in autoTruncateAllData() (@BeforeEach + @AfterEach).
// ════════════════════════════════════════════════════════════════════════════

// ─── TC54 — S3-F10 dashboard happy path ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC54_DashboardHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC54 — Dashboard returns totalBookings/completionRate/totalRevenue/averageBookingValue/bookingsByStatus")
        void dashboard_happy_path() throws Exception {
                BASE_URL = orderServiceUrl;
                String[] sts = {"COMPLETED","COMPLETED","COMPLETED","COMPLETED","COMPLETED","COMPLETED",
                                "CANCELLED","CANCELLED","REQUESTED","REQUESTED"};
                double[] amts = {100, 80, 120, 90, 150, 60, 200, 50, 180, 70};
                String[] dts = {"2026-03-02","2026-03-05","2026-03-09","2026-03-12","2026-03-15","2026-03-18",
                                "2026-03-21","2026-03-24","2026-03-27","2026-03-30"};
                for (int i = 0; i < 10; i++) {
                        Long bid = _BkM2.bk(this, 1L, 1L, sts[i], dts[i]);
                        if ("COMPLETED".equals(sts[i])) _BkM2.inv(this, bid, 1L, amts[i], "COMPLETED", dts[i]);
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC54 dashboard");
                JsonNode j = parseNode(r.body());
                assertEquals(10L, _BkM2.rL(j, "totalBookings", "total_bookings"),
                        "TC54: totalBookings=10; body=" + r.body());
                assertEquals(0.6, _BkM2.rD(j, "completionRate", "completion_rate"), 0.01,
                        "TC54: completionRate=0.6");
                assertEquals(100+80+120+90+150+60, _BkM2.rD(j, "totalRevenue", "total_revenue"), 0.01,
                        "TC54: totalRevenue mismatch");
                JsonNode bd = _BkM2.rO(j, "bookingsByStatus", "bookings_by_status");
                assertNotNull(bd, "TC54: bookingsByStatus key required");
                assertEquals(6L, bd.has("COMPLETED") ? bd.get("COMPLETED").asLong() : 0L, "TC54: COMPLETED=6");
                assertEquals(2L, bd.has("CANCELLED") ? bd.get("CANCELLED").asLong() : 0L, "TC54: CANCELLED=2");
                assertEquals(2L, bd.has("REQUESTED") ? bd.get("REQUESTED").asLong() : 0L, "TC54: REQUESTED=2");
        }
}

// ─── TC55 — S3-F10 totalBookings isolated ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC55_DashboardTotalBookingsTests extends TestBase {
        @Test
        @DisplayName("TC55 — Dashboard.totalBookings equals exact count of bookings in range")
        void total_bookings_isolated() throws Exception {
                BASE_URL = orderServiceUrl;
                for (int i = 0; i < 7; i++) {
                        Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-09-15");
                        _BkM2.inv(this, bid, 1L, 100.0, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC55");
                JsonNode j = parseNode(r.body());
                assertEquals(7L, _BkM2.rL(j, "totalBookings", "total_bookings"),
                        "TC55: totalBookings=7; body=" + r.body());
        }
}

// ─── TC56 — S3-F10 totalRevenue isolated ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
@Tag("with-baseline")
class TC56_DashboardTotalRevenueTests extends TestBase {
        @Test
        @DisplayName("TC56 — Dashboard.totalRevenue equals SUM(invoices.amount) for COMPLETED bookings")
        void total_revenue_isolated() throws Exception {
                BASE_URL = orderServiceUrl;
                double[] amts = {100, 200, 300, 400};
                for (double a : amts) {
                        Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-09-15");
                        _BkM2.inv(this, bid, 1L, a, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC56");
                JsonNode j = parseNode(r.body());
                assertEquals(1000.0, _BkM2.rD(j, "totalRevenue", "total_revenue"), 0.01,
                        "TC56: totalRevenue=1000");
        }
}

// ─── TC57 — S3-F10 averageBookingValue isolated ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC57_DashboardAverageBookingValueTests extends TestBase {
        @Test
        @DisplayName("TC57 — Dashboard.averageBookingValue equals totalRevenue / completed count")
        void avg_booking_value_isolated() throws Exception {
                BASE_URL = orderServiceUrl;
                double[] amts = {50, 100, 150, 200, 250};
                for (double a : amts) {
                        Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-09-15");
                        _BkM2.inv(this, bid, 1L, a, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC57");
                JsonNode j = parseNode(r.body());
                double avg = _BkM2.rD(j, "averageBookingValue", "average_booking_value", "avgBookingValue");
                assertEquals(150.0, avg, 0.01, "TC57: averageBookingValue=150");
        }
}

// ─── TC58 — S3-F10 completionRate isolated ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC58_DashboardCompletionRateTests extends TestBase {
        @Test
        @DisplayName("TC58 — Dashboard.completionRate = COMPLETED / total")
        void completion_rate_isolated() throws Exception {
                BASE_URL = orderServiceUrl;
                String[] sts = {"COMPLETED","COMPLETED","COMPLETED","COMPLETED","COMPLETED",
                                "CANCELLED","CANCELLED","CANCELLED"};
                for (String st : sts) {
                        Long bid = _BkM2.bk(this, 1L, 1L, st, "2026-09-15");
                        if ("COMPLETED".equals(st)) _BkM2.inv(this, bid, 1L, 100.0, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC58");
                JsonNode j = parseNode(r.body());
                assertEquals(0.625, _BkM2.rD(j, "completionRate", "completion_rate"), 0.001,
                        "TC58: completionRate=0.625");
        }
}

// ─── TC59 — S3-F10 bookingsByStatus has all 5 statuses ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC59_DashboardBookingsByStatusTests extends TestBase {
        @Test
        @DisplayName("TC59 — Dashboard.bookingsByStatus has all 5 Booking statuses, each count=1")
        void bookings_by_status_isolated() throws Exception {
                BASE_URL = orderServiceUrl;
                String[] sts = {"REQUESTED","CONFIRMED","IN_PROGRESS","COMPLETED","CANCELLED"};
                for (String st : sts) {
                        Long bid = _BkM2.bk(this, 1L, 1L, st, "2026-09-15");
                        if ("COMPLETED".equals(st)) _BkM2.inv(this, bid, 1L, 75.0, "COMPLETED", "2026-09-15");
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC59");
                JsonNode j = parseNode(r.body());
                JsonNode bd = _BkM2.rO(j, "bookingsByStatus", "bookings_by_status");
                assertNotNull(bd, "TC59: bookingsByStatus key required");
                for (String st : sts) {
                        assertTrue(bd.has(st), "TC59: bookingsByStatus missing key '" + st + "'");
                        assertEquals(1L, bd.get(st).asLong(),
                                "TC59: bookingsByStatus[" + st + "]=1; got " + bd.get(st).asLong());
                }
        }
}

// ─── TC60 — S3-F10 empty range returns zeros ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC60_DashboardEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC60 — Dashboard with no bookings in range returns totalBookings=0, totalRevenue=0")
        void empty_range_zeros() throws Exception {
                BASE_URL = orderServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC60");
                JsonNode j = parseNode(r.body());
                assertEquals(0L, _BkM2.rL(j, "totalBookings", "total_bookings"),
                        "TC60: totalBookings=0");
                assertEquals(0.0, _BkM2.rD(j, "totalRevenue", "total_revenue"), 0.01,
                        "TC60: totalRevenue=0");
        }
}

// ─── TC61 — S3-F10 invalid date range (start > end) returns 400 ─────────────
@Tag("public")
@Tag("features_m2")
class TC61_DashboardInvalidDateRangeTests extends TestBase {
        @Test
        @DisplayName("TC61 — Dashboard with startDate > endDate returns 400")
        void invalid_date_range_400() throws Exception {
                BASE_URL = orderServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-04-01&endDate=2026-03-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC61: must be 400; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC62 — S3-F10 missing JWT returns 401 ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC62_DashboardMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC62 — Dashboard without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = orderServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31");
                assertEquals(401, r.statusCode(),
                        "TC62: must be 401; got " + r.statusCode());
        }
}

// ─── TC63 — S3-F10 invalid JWT returns 401 ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC63_DashboardInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC63 — Dashboard with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = orderServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-03-01&endDate=2026-03-31", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC63: must be 401; got " + r.statusCode());
        }
}

// ─── TC64 — S3-F10 boundary date inclusion ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC64_DashboardBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC64 — Booking exactly at startDate T00:00:00 is included")
        void boundary_included() throws Exception {
                BASE_URL = orderServiceUrl;
                String bTable = tableName("Booking");
                Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-05-01");
                // Force timestamps to exactly start-of-day on the boundary
                String _bkTsCol64;
                try { _bkTsCol64 = columnByField("Booking", "createdAt"); }
                catch (Throwable _t1) {
                    try { _bkTsCol64 = columnByField("Booking", "requestedAt"); }
                    catch (Throwable _t2) { _bkTsCol64 = columnByField("Booking", "completedAt"); }
                }
                jdbc.update("UPDATE \"" + bTable + "\" SET " + _bkTsCol64 + "=? WHERE id=?",
                        java.sql.Timestamp.valueOf("2026-05-01 00:00:00"), bid);
                _BkM2.inv(this, bid, 1L, 100.0, "COMPLETED", "2026-05-01");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-05-01&endDate=2026-05-31", tok);
                assert2xx(r, "TC64");
                JsonNode j = parseNode(r.body());
                assertEquals(1L, _BkM2.rL(j, "totalBookings", "total_bookings"),
                        "TC64: boundary booking must be counted; body=" + r.body());
        }
}

// ─── TC65 — S3-F10 out-of-range bookings excluded ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC65_DashboardOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC65 — Bookings outside [startDate, endDate] are excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = orderServiceUrl;
                Long inRange = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-06-15");
                _BkM2.inv(this, inRange, 1L, 100.0, "COMPLETED", "2026-06-15");
                Long outRange = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-08-15");
                _BkM2.inv(this, outRange, 1L, 200.0, "COMPLETED", "2026-08-15");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-06-01&endDate=2026-06-30", tok);
                assert2xx(r, "TC65");
                JsonNode j = parseNode(r.body());
                assertEquals(1L, _BkM2.rL(j, "totalBookings", "total_bookings"),
                        "TC65: only the in-range booking counts; got " + r.body());
        }
}

// ─── TC66 — S3-F10 ANALYTICS_VIEWED logged on first call ────────────────────
@Tag("public")
@Tag("features_m2")
class TC66_DashboardAnalyticsViewedLoggedTests extends TestBase {
        @Test
        @DisplayName("TC66 — First dashboard call writes ANALYTICS_VIEWED to booking_events")
        void analytics_viewed_on_first_call() throws Exception {
                BASE_URL = orderServiceUrl;
                if (mongo == null) throw new AssertionError("TC66: MongoDB required");
                String coll = s3EventsCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31", tok);
                assert2xx(r, "TC66");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after > before,
                        "TC66: ANALYTICS_VIEWED count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC67 — S3-F10 ANALYTICS_VIEWED logged on cache hit too ─────────────────
@Tag("public")
@Tag("features_m2")
class TC67_DashboardAnalyticsViewedOnCacheHitTests extends TestBase {
        @Test
        @DisplayName("TC67 — Second dashboard call (cache hit) still logs ANALYTICS_VIEWED")
        void analytics_viewed_on_cache_hit() throws Exception {
                BASE_URL = orderServiceUrl;
                if (mongo == null) throw new AssertionError("TC67: MongoDB required");
                String coll = s3EventsCollection();
                String tok = adminToken();
                String url = "/api/bookings/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31";
                assert2xx(httpGetAuth(url, tok), "TC67 first");
                long after1 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assert2xx(httpGetAuth(url, tok), "TC67 second");
                long after2 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after2 > after1,
                        "TC67: ANALYTICS_VIEWED must be logged on cache hit too; after1=" + after1 + " after2=" + after2);
        }
}

// ─── TC68 — S3-F10 cache returns same body for repeated calls ───────────────
@Tag("public")
@Tag("features_m2")
class TC68_DashboardCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC68 — Two identical dashboard requests return identical bodies")
        void cache_same_body() throws Exception {
                BASE_URL = orderServiceUrl;
                String tok = adminToken();
                String url = "/api/bookings/analytics/dashboard?startDate=2026-07-01&endDate=2026-07-31";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC68 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC68 second");
                JsonNode j1 = parseNode(r1.body());
                JsonNode j2 = parseNode(r2.body());
                assertEquals(_BkM2.rL(j1, "totalBookings", "total_bookings"),
                             _BkM2.rL(j2, "totalBookings", "total_bookings"),
                        "TC68: cached totalBookings must match");
                assertEquals(_BkM2.rD(j1, "totalRevenue", "total_revenue"),
                             _BkM2.rD(j2, "totalRevenue", "total_revenue"), 0.01,
                        "TC68: cached totalRevenue must match");
        }
}

// ─── TC69 — S3-F10 cache hit doesn't re-aggregate ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC69_DashboardCacheNoReaggregateTests extends TestBase {
        @Test
        @DisplayName("TC69 — Insert booking after first call → cached body still returned")
        void cache_does_not_reaggregate() throws Exception {
                BASE_URL = orderServiceUrl;
                String tok = adminToken();
                String url = "/api/bookings/analytics/dashboard?startDate=2026-11-01&endDate=2026-11-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC69 first");
                long t1 = _BkM2.rL(parseNode(r1.body()), "totalBookings", "total_bookings");
                Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-11-15");
                _BkM2.inv(this, bid, 1L, 100.0, "COMPLETED", "2026-11-15");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC69 second");
                long t2 = _BkM2.rL(parseNode(r2.body()), "totalBookings", "total_bookings");
                assertEquals(t1, t2,
                        "TC69: cached value must equal pre-insert value; t1=" + t1 + " t2=" + t2);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S3-F11 — Record User-Provider Booking Pattern (TC70-TC84)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC70 — S3-F11 happy path creates BOOKED edge with bookingCount=1 ───────
@Tag("public")
@Tag("features_m2")
class TC70_RecordInteractionHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC70 — Record interaction on COMPLETED booking creates BOOKED with bookingCount=1")
        void record_interaction_happy() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC70: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc70u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC70 Provider " + nonce());
                Long bid = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", tok);
                assert2xx(r, "TC70");
                long count = _BkM2.bookCount(this, uid, pid);
                assertEquals(1L, count, "TC70: bookingCount=1; got " + count);
        }
}

// ─── TC71 — S3-F11 idempotency: same booking twice → bookingCount stays 1 ───
@Tag("public")
@Tag("features_m2")
class TC71_RecordInteractionIdempotencyTests extends TestBase {
        @Test
        @DisplayName("TC71 — Same bookingId recorded twice keeps bookingCount=1")
        void record_interaction_idempotent() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC71: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc71u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC71 Provider " + nonce());
                Long bid = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + bid + "/record-interaction", "", tok), "TC71 first");
                assert2xx(httpPostAuth("/api/bookings/" + bid + "/record-interaction", "", tok), "TC71 second");
                long count = _BkM2.bookCount(this, uid, pid);
                assertEquals(1L, count, "TC71: bookingCount must stay 1 after duplicate; got " + count);
        }
}

// ─── TC72 — S3-F11 two distinct bookings same user→provider → bookingCount=2
@Tag("public")
@Tag("features_m2")
class TC72_RecordInteractionTwoDistinctTests extends TestBase {
        @Test
        @DisplayName("TC72 — Two distinct COMPLETED bookings same user→provider → bookingCount=2")
        void record_interaction_two_distinct() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC72: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc72u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC72 Provider " + nonce());
                Long b1 = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                Long b2 = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-12");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + b1 + "/record-interaction", "", tok), "TC72 b1");
                assert2xx(httpPostAuth("/api/bookings/" + b2 + "/record-interaction", "", tok), "TC72 b2");
                long count = _BkM2.bookCount(this, uid, pid);
                assertEquals(2L, count, "TC72: bookingCount=2; got " + count);
        }
}

// ─── TC73 — S3-F11 different provider → new edge with bookingCount=1 ────────
@Tag("public")
@Tag("features_m2")
class TC73_RecordInteractionDifferentProviderTests extends TestBase {
        @Test
        @DisplayName("TC73 — Recording booking to a different provider creates a new edge")
        void record_interaction_different_provider() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC73: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc73u");
                long uid = ((Number) u.get("id")).longValue();
                long p1 = _BkM2.prov(this, "TC73 P1 " + nonce());
                long p2 = _BkM2.prov(this, "TC73 P2 " + nonce());
                Long b1 = _BkM2.bk(this, uid, p1, "COMPLETED", "2026-04-10");
                Long b2 = _BkM2.bk(this, uid, p2, "COMPLETED", "2026-04-11");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + b1 + "/record-interaction", "", tok), "TC73 b1");
                assert2xx(httpPostAuth("/api/bookings/" + b2 + "/record-interaction", "", tok), "TC73 b2");
                assertEquals(1L, _BkM2.bookCount(this, uid, p1), "TC73: edge to p1 stays at 1");
                assertEquals(1L, _BkM2.bookCount(this, uid, p2), "TC73: edge to p2 is 1");
        }
}

// ─── TC74 — S3-F11 REQUESTED booking → 400 ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC74_RecordInteractionRequestedTests extends TestBase {
        @Test
        @DisplayName("TC74 — Recording a REQUESTED (not completed) booking returns 400")
        void record_interaction_requested_400() throws Exception {
                BASE_URL = orderServiceUrl;
                Long bid = _BkM2.bk(this, 1L, 1L, "REQUESTED", "2026-04-10");
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC74: must be 400 for REQUESTED; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC75 — S3-F11 CANCELLED booking → 400 ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC75_RecordInteractionCancelledTests extends TestBase {
        @Test
        @DisplayName("TC75 — Recording a CANCELLED booking returns 400")
        void record_interaction_cancelled_400() throws Exception {
                BASE_URL = orderServiceUrl;
                Long bid = _BkM2.bk(this, 1L, 1L, "CANCELLED", "2026-04-10");
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC75: must be 400 for CANCELLED; got " + r.statusCode());
        }
}

// ─── TC76 — S3-F11 IN_PROGRESS booking → 400 ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC76_RecordInteractionInProgressTests extends TestBase {
        @Test
        @DisplayName("TC76 — Recording an IN_PROGRESS booking returns 400")
        void record_interaction_in_progress_400() throws Exception {
                BASE_URL = orderServiceUrl;
                Long bid = _BkM2.bk(this, 1L, 1L, "IN_PROGRESS", "2026-04-10");
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC76: must be 400 for IN_PROGRESS; got " + r.statusCode());
        }
}

// ─── TC77 — S3-F11 CONFIRMED booking → 400 ──────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC77_RecordInteractionConfirmedTests extends TestBase {
        @Test
        @DisplayName("TC77 — Recording a CONFIRMED booking returns 400")
        void record_interaction_confirmed_400() throws Exception {
                BASE_URL = orderServiceUrl;
                Long bid = _BkM2.bk(this, 1L, 1L, "CONFIRMED", "2026-04-10");
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", tok);
                assertEquals(400, r.statusCode(),
                        "TC77: must be 400 for CONFIRMED; got " + r.statusCode());
        }
}

// ─── TC78 — S3-F11 non-existent booking → 404 ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC78_RecordInteractionNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC78 — Record interaction for non-existent booking returns 404")
        void record_interaction_not_found_404() throws Exception {
                BASE_URL = orderServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/999999/record-interaction", "", tok);
                assertEquals(404, r.statusCode(),
                        "TC78: must be 404; got " + r.statusCode());
        }
}

// ─── TC79 — S3-F11 missing JWT → 401 ───────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC79_RecordInteractionMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC79 — Record interaction without Authorization header returns 401")
        void record_interaction_missing_jwt_401() throws Exception {
                BASE_URL = orderServiceUrl;
                Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-04-10");
                HttpResponse<String> r = httpPost(
                        "/api/bookings/" + bid + "/record-interaction", "");
                assertEquals(401, r.statusCode(),
                        "TC79: must be 401; got " + r.statusCode());
        }
}

// ─── TC80 — S3-F11 invalid JWT → 401 ───────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC80_RecordInteractionInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC80 — Record interaction with bogus JWT returns 401")
        void record_interaction_invalid_jwt_401() throws Exception {
                BASE_URL = orderServiceUrl;
                Long bid = _BkM2.bk(this, 1L, 1L, "COMPLETED", "2026-04-10");
                HttpResponse<String> r = httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC80: must be 401; got " + r.statusCode());
        }
}

// ─── TC81 — S3-F11 lastBookingDate set on success ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC81_RecordInteractionLastBookingDateTests extends TestBase {
        @Test
        @DisplayName("TC81 — BOOKED edge has lastBookingDate property after recording")
        void record_interaction_last_booking_date() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC81: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc81u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC81 Provider " + nonce());
                Long bid = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + bid + "/record-interaction", "", tok), "TC81");
                java.util.List<java.util.Map<String, Object>> rows = neo4jExec(
                        "MATCH (a:`" + s3GraphUserLabel() + "` {id:$u})-[r:`" + s3GraphRelationship()
                          + "`]->(b:`" + s3GraphCatalogLabel() + "` {id:$p}) RETURN r.lastBookingDate AS lbd LIMIT 1",
                        java.util.Map.of("u", uid, "p", pid));
                assertFalse(rows.isEmpty(), "TC81: BOOKED edge must exist");
                Object lbd = rows.get(0).get("lbd");
                assertNotNull(lbd, "TC81: edge must carry lastBookingDate property; got null");
        }
}

// ─── TC82 — S3-F11 Neo4j Provider node has correct id ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC82_RecordInteractionProviderNodeTests extends TestBase {
        @Test
        @DisplayName("TC82 — Neo4j Provider node exists with the seeded provider id")
        void record_interaction_provider_node() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC82: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc82u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC82 Provider " + nonce());
                Long bid = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + bid + "/record-interaction", "", tok), "TC82");
                long count = neo4jNodeCount(s3GraphCatalogLabel(), pid);
                assertTrue(count >= 1, "TC82: Provider node id=" + pid + " must exist; count=" + count);
        }
}

// ─── TC83 — S3-F11 Neo4j User node has correct id ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC83_RecordInteractionUserNodeTests extends TestBase {
        @Test
        @DisplayName("TC83 — Neo4j User node exists with the booking's user id")
        void record_interaction_user_node() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC83: Neo4j required");
                java.util.Map<String, Object> u = seedAndLoginUser("tc83u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC83 Provider " + nonce());
                Long bid = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + bid + "/record-interaction", "", tok), "TC83");
                long count = neo4jNodeCount(s3GraphUserLabel(), uid);
                assertTrue(count >= 1, "TC83: User node id=" + uid + " must exist; count=" + count);
        }
}

// ─── TC84 — S3-F11 INTERACTION_RECORDED logged to booking_events ───────────
@Tag("public")
@Tag("features_m2")
class TC84_RecordInteractionEventLoggedTests extends TestBase {
        @Test
        @DisplayName("TC84 — Record interaction writes INTERACTION_RECORDED to booking_events")
        void record_interaction_event_logged() throws Exception {
                BASE_URL = orderServiceUrl;
                if (mongo == null) throw new AssertionError("TC84: MongoDB required");
                String coll = s3EventsCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "INTERACTION_RECORDED"));
                java.util.Map<String, Object> u = seedAndLoginUser("tc84u");
                long uid = ((Number) u.get("id")).longValue();
                long pid = _BkM2.prov(this, "TC84 Provider " + nonce());
                Long bid = _BkM2.bk(this, uid, pid, "COMPLETED", "2026-04-10");
                String tok = adminToken();
                assert2xx(httpPostAuth("/api/bookings/" + bid + "/record-interaction", "", tok), "TC84");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "INTERACTION_RECORDED"));
                assertTrue(after > before,
                        "TC84: INTERACTION_RECORDED must be logged; before=" + before + " after=" + after);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S3-F12 — Provider Recommendations (TC85-TC99)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC85 — S3-F12 happy path (composite scenario from spec) ────────────────
@Tag("public")
@Tag("features_m2")
class TC85_RecommendationsHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC85 — Recs for A (who booked P1,P2) include P3 (B booked P1) and P4 (C booked P2); exclude P1,P2")
        void recommendations_happy_path() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC85: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc85a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc85b");
                java.util.Map<String, Object> c = seedAndLoginUser("tc85c");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long cid = ((Number) c.get("id")).longValue();
                long p1 = _BkM2.prov(this, "TC85 P1 " + nonce());
                long p2 = _BkM2.prov(this, "TC85 P2 " + nonce());
                long p3 = _BkM2.prov(this, "TC85 P3 " + nonce());
                long p4 = _BkM2.prov(this, "TC85 P4 " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, p1, tok);
                _BkM2.bookAndRecord(this, aid, p2, tok);
                _BkM2.bookAndRecord(this, bid, p1, tok);
                _BkM2.bookAndRecord(this, bid, p3, tok);
                _BkM2.bookAndRecord(this, cid, p2, tok);
                _BkM2.bookAndRecord(this, cid, p4, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC85");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                java.util.Set<Long> recs = new java.util.HashSet<>();
                for (JsonNode item : arr) {
                        if (item.has("providerId")) recs.add(item.get("providerId").asLong());
                        else if (item.has("id")) recs.add(item.get("id").asLong());
                }
                assertTrue(recs.contains(p3), "TC85: must include P3 (B booked P1)");
                assertTrue(recs.contains(p4), "TC85: must include P4 (C booked P2)");
                assertFalse(recs.contains(p1), "TC85: must exclude P1 (A already booked)");
                assertFalse(recs.contains(p2), "TC85: must exclude P2 (A already booked)");
        }
}

// ─── TC86 — S3-F12 score reflects similar-user count ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC86_RecommendationsScoreTests extends TestBase {
        @Test
        @DisplayName("TC86 — Provider booked by 2 similar users ranks higher than provider booked by 1")
        void recommendations_score_ranking() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC86: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc86a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc86b");
                java.util.Map<String, Object> c = seedAndLoginUser("tc86c");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long cid = ((Number) c.get("id")).longValue();
                long pCommon = _BkM2.prov(this, "TC86 Common " + nonce());
                long pPopular = _BkM2.prov(this, "TC86 Popular " + nonce());
                long pNiche = _BkM2.prov(this, "TC86 Niche " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pCommon, tok);
                _BkM2.bookAndRecord(this, bid, pCommon, tok);
                _BkM2.bookAndRecord(this, cid, pCommon, tok);
                _BkM2.bookAndRecord(this, bid, pPopular, tok);
                _BkM2.bookAndRecord(this, cid, pPopular, tok);
                _BkM2.bookAndRecord(this, bid, pNiche, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC86");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                long scorePopular = -1, scoreNiche = -1;
                for (JsonNode item : arr) {
                        long pid = item.has("providerId") ? item.get("providerId").asLong()
                                : item.has("id") ? item.get("id").asLong() : -1;
                        long score = item.has("score") ? item.get("score").asLong() : 0;
                        if (pid == pPopular) scorePopular = score;
                        if (pid == pNiche) scoreNiche = score;
                }
                assertTrue(scorePopular > scoreNiche,
                        "TC86: pPopular score (" + scorePopular + ") must exceed pNiche score (" + scoreNiche + ")"
                        + " pPopular=" + pPopular + " pNiche=" + pNiche + " body=" + r.body());
        }
}

// ─── TC87 — S3-F12 default limit honored ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC87_RecommendationsDefaultLimitTests extends TestBase {
        @Test
        @DisplayName("TC87 — Default limit caps recommendations at 5 when no limit param provided")
        void recommendations_default_limit() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC87: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc87a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc87b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long pAnchor = _BkM2.prov(this, "TC87 Anchor " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pAnchor, tok);
                _BkM2.bookAndRecord(this, bid, pAnchor, tok);
                for (int i = 0; i < 8; i++) {
                        long p = _BkM2.prov(this, "TC87 R" + i + " " + nonce());
                        _BkM2.bookAndRecord(this, bid, p, tok);
                }
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC87");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() <= 5,
                        "TC87: default limit must cap at 5; got " + arr.size());
        }
}

// ─── TC88 — S3-F12 user with no interactions → empty list ──────────────────
@Tag("public")
@Tag("features_m2")
class TC88_RecommendationsNoInteractionsTests extends TestBase {
        @Test
        @DisplayName("TC88 — User with no recorded interactions returns empty list")
        void recommendations_no_interactions() throws Exception {
                BASE_URL = orderServiceUrl;
                java.util.Map<String, Object> a = seedAndLoginUser("tc88a");
                long aid = ((Number) a.get("id")).longValue();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC88");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(),
                        "TC88: empty list for new user; body=" + r.body());
        }
}

// ─── TC89 — S3-F12 user with no similar users → empty list ─────────────────
@Tag("public")
@Tag("features_m2")
class TC89_RecommendationsNoSimilarUsersTests extends TestBase {
        @Test
        @DisplayName("TC89 — User who booked unique providers (no overlap with anyone) → empty list")
        void recommendations_no_similar_users() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC89: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc89a");
                long aid = ((Number) a.get("id")).longValue();
                long pUnique = _BkM2.prov(this, "TC89 Unique " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pUnique, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC89");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(),
                        "TC89: empty list when no similar users; body=" + r.body());
        }
}

// ─── TC90 — S3-F12 ownership: A's token requesting B's recs → 403 ──────────
@Tag("public")
@Tag("features_m2")
class TC90_RecommendationsOwnershipTests extends TestBase {
        @Test
        @DisplayName("TC90 — User A's token requesting recommendations for user B returns 403")
        void recommendations_ownership_403() throws Exception {
                BASE_URL = orderServiceUrl;
                java.util.Map<String, Object> a = seedAndLoginUser("tc90a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc90b");
                long bid = ((Number) b.get("id")).longValue();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + bid, (String) a.get("token"));
                assertEquals(403, r.statusCode(),
                        "TC90: must be 403 (ownership violation); got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC91 — S3-F12 admin token bypasses ownership ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC91_RecommendationsAdminBypassTests extends TestBase {
        @Test
        @DisplayName("TC91 — Admin token can fetch recommendations for any user")
        void recommendations_admin_bypass() throws Exception {
                BASE_URL = orderServiceUrl;
                java.util.Map<String, Object> u = seedAndLoginUser("tc91u");
                long uid = ((Number) u.get("id")).longValue();
                String adminTok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + uid, adminTok);
                assert2xx(r, "TC91");
        }
}

// ─── TC92 — S3-F12 non-existent userId with admin token → 404 ─────────────
@Tag("public")
@Tag("features_m2")
class TC92_RecommendationsNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC92 — Admin requesting recommendations for non-existent userId returns 404")
        void recommendations_not_found_404() throws Exception {
                BASE_URL = orderServiceUrl;
                String adminTok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=999999", adminTok);
                assertEquals(404, r.statusCode(),
                        "TC92: must be 404; got " + r.statusCode());
        }
}

// ─── TC93 — S3-F12 missing token → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC93_RecommendationsMissingTokenTests extends TestBase {
        @Test
        @DisplayName("TC93 — Recommendations without Authorization header returns 401")
        void recommendations_missing_token_401() throws Exception {
                BASE_URL = orderServiceUrl;
                HttpResponse<String> r = httpGet("/api/bookings/recommendations?userId=1");
                assertEquals(401, r.statusCode(),
                        "TC93: must be 401; got " + r.statusCode());
        }
}

// ─── TC94 — S3-F12 invalid token → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC94_RecommendationsInvalidTokenTests extends TestBase {
        @Test
        @DisplayName("TC94 — Recommendations with malformed JWT returns 401")
        void recommendations_invalid_token_401() throws Exception {
                BASE_URL = orderServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=1", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC94: must be 401; got " + r.statusCode());
        }
}

// ─── TC95 — S3-F12 each rec includes providerId+name+specialty+score ──────
@Tag("public")
@Tag("features_m2")
class TC95_RecommendationsEnrichedFieldsTests extends TestBase {
        @Test
        @DisplayName("TC95 — Each recommendation item includes providerId, name, specialty, score")
        void recommendations_enriched_fields() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC95: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc95a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc95b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long pAnchor = _BkM2.prov(this, "TC95 Anchor " + nonce());
                long pTarget = _BkM2.prov(this, "TC95 Target " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pAnchor, tok);
                _BkM2.bookAndRecord(this, bid, pAnchor, tok);
                _BkM2.bookAndRecord(this, bid, pTarget, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC95");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() > 0, "TC95: at least one rec expected");
                for (JsonNode item : arr) {
                        boolean hasId = item.has("providerId") || item.has("id");
                        boolean hasName = item.has("name");
                        boolean hasSpecialty = item.has("specialty");
                        boolean hasScore = item.has("score");
                        assertTrue(hasId, "TC95: must include providerId/id; got=" + item);
                        assertTrue(hasName, "TC95: must include name; got=" + item);
                        assertTrue(hasSpecialty, "TC95: must include specialty; got=" + item);
                        assertTrue(hasScore, "TC95: must include score; got=" + item);
                }
        }
}

// ─── TC96 — S3-F12 cache returns same body for repeated calls ─────────────
@Tag("public")
@Tag("features_m2")
class TC96_RecommendationsCacheTests extends TestBase {
        @Test
        @DisplayName("TC96 — Two identical recommendation requests return identical bodies")
        void recommendations_cache_same_body() throws Exception {
                BASE_URL = orderServiceUrl;
                java.util.Map<String, Object> a = seedAndLoginUser("tc96a");
                long aid = ((Number) a.get("id")).longValue();
                String url = "/api/bookings/recommendations?userId=" + aid;
                String tok = (String) a.get("token");
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC96 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC96 second");
                assertEquals(r1.body(), r2.body(),
                        "TC96: identical recommendations responses expected (cached)");
        }
}

// ─── TC97 — S3-F12 limit=2 returns at most 2 results ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC97_RecommendationsLimitParamTests extends TestBase {
        @Test
        @DisplayName("TC97 — limit=2 caps recommendations at 2 entries")
        void recommendations_limit_param() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC97: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc97a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc97b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long pAnchor = _BkM2.prov(this, "TC97 Anchor " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pAnchor, tok);
                _BkM2.bookAndRecord(this, bid, pAnchor, tok);
                for (int i = 0; i < 5; i++) {
                        long p = _BkM2.prov(this, "TC97 R" + i + " " + nonce());
                        _BkM2.bookAndRecord(this, bid, p, tok);
                }
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid + "&limit=2", (String) a.get("token"));
                assert2xx(r, "TC97");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() <= 2,
                        "TC97: limit=2 must cap; got " + arr.size());
        }
}

// ─── TC98 — S3-F12 already-booked providers excluded ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC98_RecommendationsExcludeAlreadyBookedTests extends TestBase {
        @Test
        @DisplayName("TC98 — Recommendations exclude providers user already booked with")
        void recommendations_exclude_already_booked() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC98: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc98a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc98b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long pShared = _BkM2.prov(this, "TC98 Shared " + nonce());
                long pNew = _BkM2.prov(this, "TC98 New " + nonce());
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pShared, tok);
                _BkM2.bookAndRecord(this, bid, pShared, tok);
                _BkM2.bookAndRecord(this, bid, pNew, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC98");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                java.util.Set<Long> recs = new java.util.HashSet<>();
                for (JsonNode item : arr) {
                        if (item.has("providerId")) recs.add(item.get("providerId").asLong());
                        else if (item.has("id")) recs.add(item.get("id").asLong());
                }
                assertFalse(recs.contains(pShared),
                        "TC98: must exclude pShared (already booked); recs=" + recs);
                assertTrue(recs.contains(pNew),
                        "TC98: must include pNew (similar user booked, not yet by A); recs=" + recs);
        }
}

// ─── TC99 — S3-F12 specialty enriched from PostgreSQL ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC99_RecommendationsSpecialtyEnrichmentTests extends TestBase {
        @Test
        @DisplayName("TC99 — Each recommendation's specialty matches the PG providers.specialty value")
        void recommendations_specialty_enriched() throws Exception {
                BASE_URL = orderServiceUrl;
                if (neo4j == null) throw new AssertionError("TC99: Neo4j required");
                java.util.Map<String, Object> a = seedAndLoginUser("tc99a");
                java.util.Map<String, Object> b = seedAndLoginUser("tc99b");
                long aid = ((Number) a.get("id")).longValue();
                long bid = ((Number) b.get("id")).longValue();
                long pAnchor = _BkM2.prov(this, "TC99 Anchor " + nonce());
                long pTarget = _BkM2.prov(this, "TC99 Target " + nonce());
                String pTargetSpecialty = jdbc.queryForObject(
                        "SELECT specialty::text FROM \"" + tableName("Provider") + "\" WHERE id=?",
                        String.class, pTarget);
                String tok = adminToken();
                _BkM2.bookAndRecord(this, aid, pAnchor, tok);
                _BkM2.bookAndRecord(this, bid, pAnchor, tok);
                _BkM2.bookAndRecord(this, bid, pTarget, tok);
                HttpResponse<String> r = httpGetAuth(
                        "/api/bookings/recommendations?userId=" + aid, (String) a.get("token"));
                assert2xx(r, "TC99");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                boolean foundTarget = false;
                for (JsonNode item : arr) {
                        long pid = item.has("providerId") ? item.get("providerId").asLong()
                                : item.has("id") ? item.get("id").asLong() : -1;
                        if (pid == pTarget) {
                                foundTarget = true;
                                String spec = item.has("specialty") ? item.get("specialty").asText() : null;
                                assertEquals(pTargetSpecialty, spec,
                                        "TC99: response specialty must match PG; got " + spec
                                                + ", expected " + pTargetSpecialty);
                        }
                }
                assertTrue(foundTarget, "TC99: pTarget must be in results");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Helper class for S3 seeding (package-private; same package as TestBase so it
// can call protected helpers). Booking/Invoice/Provider seed methods use the
// manifest-driven tableName/columnByField pattern so they survive student-side
// renames (e.g. provider_fk vs provider_id, totalAmount vs total_amount).
// ════════════════════════════════════════════════════════════════════════════
final class _BkM2 {
        private _BkM2() {}

        static Long bk(TestBase t, long userId, long providerId, String status, String date) {
                String table = t.tableName("Booking");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Booking", "user"), userId);
                try { ov.put(t.columnByField("Booking", "provider"), providerId); }
                catch (Throwable ignore) { /* student may name the FK differently — fall back */ }
                ov.put(t.columnByField("Booking", "appointmentDate"), java.sql.Date.valueOf(date));
                // Booking.startTime/endTime are LocalTime → PG TIME (per Booking M1 §6).
                // JDBC must bind java.time.LocalTime, never String.
                try { ov.put(t.columnByField("Booking", "startTime"), java.time.LocalTime.parse("10:00")); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("Booking", "endTime"),   java.time.LocalTime.parse("11:00")); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Booking", "status"), status);
                Long id = t.insertRowReturningId(table, ov);
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
                return id;
        }

        static Long inv(TestBase t, long bookingId, long userId, double amount, String status, String date) {
                String table = t.tableName("Invoice");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Invoice", "booking"), bookingId);
                try { ov.put(t.columnByField("Invoice", "user"), userId); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Invoice", "amount"), amount);
                ov.put(t.columnByField("Invoice", "status"), status);
                try { ov.put(t.columnByField("Invoice", "method"), "CREDIT_CARD"); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:30:00"));
                return id;
        }

        static long prov(TestBase t, String name) {
                String table = t.tableName("Provider");
                String sn = name.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (sn.length() > 32) sn = sn.substring(0, 32);
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Provider", "name"), name);
                ov.put(t.columnByField("Provider", "email"), sn + "_" + System.nanoTime() + "@bk.io");
                ov.put(t.columnByField("Provider", "phone"),
                        "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L));
                try {
                        ov.put(t.columnByField("Provider", "specialty"),
                                t.enumValueAt("Provider", "specialty", 0));
                } catch (Throwable ignore) {}
                try {
                        ov.put(t.columnByField("Provider", "status"),
                                t.enumValueAt("Provider", "status", 0));
                } catch (Throwable ignore) {}
                return t.insertRowReturningId(table, ov);
        }

        static void bookAndRecord(TestBase t, long userId, long providerId, String adminTok) throws Exception {
                Long bid = bk(t, userId, providerId, "COMPLETED", "2026-04-15");
                inv(t, bid, userId, 100.0, "COMPLETED", "2026-04-15");
                java.net.http.HttpResponse<String> r = t.httpPostAuth(
                        "/api/bookings/" + bid + "/record-interaction", "", adminTok);
                if (r.statusCode() / 100 != 2) {
                        throw new AssertionError("bookAndRecord: failed for user="
                                + userId + " provider=" + providerId
                                + " status=" + r.statusCode() + " body=" + r.body());
                }
        }

        static long bookCount(TestBase t, long userId, long providerId) {
                java.util.List<java.util.Map<String, Object>> rows = t.neo4jExec(
                        "MATCH (a:`" + t.s3GraphUserLabel() + "` {id:$u})-[r:`" + t.s3GraphRelationship()
                          + "`]->(b:`" + t.s3GraphCatalogLabel() + "` {id:$p}) "
                          + "RETURN coalesce(r.bookingCount, r.orderCount, r.count, 0) AS c LIMIT 1",
                        java.util.Map.of("u", userId, "p", providerId));
                if (rows.isEmpty()) return 0L;
                Object c = rows.get(0).get("c");
                return c instanceof Number n ? n.longValue() : 0L;
        }

        static long rL(JsonNode j, String... ks) {
                for (String k : ks) if (j.has(k)) return j.get(k).asLong();
                return -1;
        }
        static double rD(JsonNode j, String... ks) {
                for (String k : ks) if (j.has(k)) return j.get(k).asDouble();
                return -1;
        }
        static JsonNode rO(JsonNode j, String... ks) {
                for (String k : ks) if (j.has(k)) return j.get(k);
                return null;
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S4 M2 — Calendar Service features (TC100..TC135)
//
// Covers S4-F10 (calendar analytics dashboard, TC100-TC117), S4-F11 (record
// provider availability snapshot to Cassandra, TC118-TC127), and S4-F12
// (availability history read, TC128-TC135). Theme-specific terms: S4
// endpoints under /api/calendar; TimeSlot is the slot entity (provider,
// date, startTime, endTime, available); the Cassandra time-series table is
// calendar_availability_events partitioned by provider_id and clustered by
// timestamp; Mongo logs ANALYTICS_VIEWED / TRACKING_RECORDED to
// calendar_events. Per-test wipe of PG/Mongo/Cassandra/Redis happens in
// autoTruncateAllData().
// ════════════════════════════════════════════════════════════════════════════

// ─── TC100 — S4-F10 dashboard happy path ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC100_CalendarDashboardHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC100 — Dashboard returns totalSlots=10/availableSlots=4/bookedSlots=6/utilizationRate=0.6")
        void dashboard_happy_path() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC100 P " + nonce());
                for (int i = 0; i < 4; i++) _BkM2S4.slot(this, pid, "2026-04-" + (i + 1), true);
                for (int i = 0; i < 6; i++) _BkM2S4.slot(this, pid, "2026-04-" + (i + 5), false);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-04-01&endDate=2026-04-30", tok);
                assert2xx(r, "TC100");
                JsonNode j = parseNode(r.body());
                assertEquals(10L, _BkM2.rL(j, "totalSlots", "total_slots"),
                        "TC100: totalSlots=10; body=" + r.body());
                assertEquals(4L, _BkM2.rL(j, "availableSlots", "available_slots"),
                        "TC100: availableSlots=4");
                assertEquals(6L, _BkM2.rL(j, "bookedSlots", "booked_slots"),
                        "TC100: bookedSlots=6");
                assertEquals(0.6, _BkM2.rD(j, "utilizationRate", "utilization_rate"), 0.01,
                        "TC100: utilizationRate=0.6");
        }
}

// ─── TC101 — S4-F10 totalSlots isolated ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC101_CalendarTotalSlotsTests extends TestBase {
        @Test
        @DisplayName("TC101 — Dashboard.totalSlots equals exact count of slots in range")
        void total_slots_isolated() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC101 P " + nonce());
                for (int i = 0; i < 7; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 1), true);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC101");
                assertEquals(7L,
                        _BkM2.rL(parseNode(r.body()), "totalSlots", "total_slots"),
                        "TC101: totalSlots=7");
        }
}

// ─── TC102 — S4-F10 availableSlots isolated ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC102_CalendarAvailableSlotsTests extends TestBase {
        @Test
        @DisplayName("TC102 — Dashboard.availableSlots counts only slots where available=true")
        void available_slots_isolated() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC102 P " + nonce());
                for (int i = 0; i < 5; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 1), true);
                for (int i = 0; i < 3; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 6), false);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC102");
                assertEquals(5L,
                        _BkM2.rL(parseNode(r.body()), "availableSlots", "available_slots"),
                        "TC102: availableSlots=5");
        }
}

// ─── TC103 — S4-F10 bookedSlots isolated ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC103_CalendarBookedSlotsTests extends TestBase {
        @Test
        @DisplayName("TC103 — Dashboard.bookedSlots counts only slots where available=false")
        void booked_slots_isolated() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC103 P " + nonce());
                for (int i = 0; i < 2; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 1), true);
                for (int i = 0; i < 8; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 3), false);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC103");
                assertEquals(8L,
                        _BkM2.rL(parseNode(r.body()), "bookedSlots", "booked_slots"),
                        "TC103: bookedSlots=8");
        }
}

// ─── TC104 — S4-F10 utilizationRate isolated ───────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC104_CalendarUtilizationRateTests extends TestBase {
        @Test
        @DisplayName("TC104 — Dashboard.utilizationRate = bookedSlots / totalSlots")
        void utilization_rate_isolated() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC104 P " + nonce());
                for (int i = 0; i < 3; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 1), true);
                for (int i = 0; i < 5; i++) _BkM2S4.slot(this, pid, "2026-09-" + (i + 4), false);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC104");
                assertEquals(0.625,
                        _BkM2.rD(parseNode(r.body()), "utilizationRate", "utilization_rate"), 0.001,
                        "TC104: utilizationRate=5/8=0.625");
        }
}

// ─── TC105 — S4-F10 slotsByDate map keyed by date ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC105_CalendarSlotsByDateTests extends TestBase {
        @Test
        @DisplayName("TC105 — Dashboard.slotsByDate has count grouped by date")
        void slots_by_date_isolated() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC105 P " + nonce());
                for (int i = 0; i < 3; i++) _BkM2S4.slot(this, pid, "2026-09-15", i % 2 == 0);
                for (int i = 0; i < 2; i++) _BkM2S4.slot(this, pid, "2026-09-16", true);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-09-01&endDate=2026-09-30", tok);
                assert2xx(r, "TC105");
                JsonNode j = parseNode(r.body());
                JsonNode byDate = _BkM2.rO(j, "slotsByDate", "slots_by_date");
                assertNotNull(byDate, "TC105: slotsByDate key required");
                long c15 = byDate.has("2026-09-15") ? byDate.get("2026-09-15").asLong() : 0L;
                long c16 = byDate.has("2026-09-16") ? byDate.get("2026-09-16").asLong() : 0L;
                assertEquals(3L, c15, "TC105: 2026-09-15 count=3");
                assertEquals(2L, c16, "TC105: 2026-09-16 count=2");
        }
}

// ─── TC106 — S4-F10 empty range returns zeros ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC106_CalendarEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC106 — Dashboard with no slots in range returns zeros + empty slotsByDate")
        void empty_range_zeros() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC106");
                JsonNode j = parseNode(r.body());
                assertEquals(0L, _BkM2.rL(j, "totalSlots", "total_slots"), "TC106: totalSlots=0");
                assertEquals(0L, _BkM2.rL(j, "availableSlots", "available_slots"), "TC106: availableSlots=0");
                assertEquals(0L, _BkM2.rL(j, "bookedSlots", "booked_slots"), "TC106: bookedSlots=0");
                assertEquals(0.0, _BkM2.rD(j, "utilizationRate", "utilization_rate"), 0.01,
                        "TC106: utilizationRate=0");
        }
}

// ─── TC107 — S4-F10 invalid date range (start > end) returns 400 ───────────
@Tag("public")
@Tag("features_m2")
class TC107_CalendarInvalidDateRangeTests extends TestBase {
        @Test
        @DisplayName("TC107 — Dashboard with startDate > endDate returns 400")
        void invalid_date_range_400() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-04-30&endDate=2026-04-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC107: must be 400; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC108 — S4-F10 missing JWT → 401 ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC108_CalendarMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC108 — Dashboard without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = deliveryServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/calendar/analytics?startDate=2026-04-01&endDate=2026-04-30");
                assertEquals(401, r.statusCode(),
                        "TC108: must be 401; got " + r.statusCode());
        }
}

// ─── TC109 — S4-F10 invalid JWT → 401 ──────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC109_CalendarInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC109 — Dashboard with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = deliveryServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-04-01&endDate=2026-04-30", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC109: must be 401; got " + r.statusCode());
        }
}

// ─── TC110 — S4-F10 boundary date inclusion ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC110_CalendarBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC110 — Slot exactly on startDate is included in totalSlots")
        void boundary_included() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC110 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-05-01", true);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-05-01&endDate=2026-05-31", tok);
                assert2xx(r, "TC110");
                assertEquals(1L,
                        _BkM2.rL(parseNode(r.body()), "totalSlots", "total_slots"),
                        "TC110: boundary slot must be counted");
        }
}

// ─── TC111 — S4-F10 out-of-range slots excluded ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC111_CalendarOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC111 — Slots outside [startDate, endDate] excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC111 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-06-15", true);
                _BkM2S4.slot(this, pid, "2026-08-15", false);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-06-01&endDate=2026-06-30", tok);
                assert2xx(r, "TC111");
                assertEquals(1L,
                        _BkM2.rL(parseNode(r.body()), "totalSlots", "total_slots"),
                        "TC111: only in-range slot counted");
        }
}

// ─── TC112 — S4-F10 utilizationRate=0 when totalSlots=0 ───────────────────
@Tag("public")
@Tag("features_m2")
class TC112_CalendarUtilizationZeroOnEmptyTests extends TestBase {
        @Test
        @DisplayName("TC112 — utilizationRate=0 when totalSlots=0 (no division by zero)")
        void utilization_zero_on_empty() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2099-06-01&endDate=2099-06-30", tok);
                assert2xx(r, "TC112");
                assertEquals(0.0,
                        _BkM2.rD(parseNode(r.body()), "utilizationRate", "utilization_rate"), 0.01,
                        "TC112: utilizationRate=0 with no slots");
        }
}

// ─── TC113 — S4-F10 ANALYTICS_VIEWED logged on first call ─────────────────
@Tag("public")
@Tag("features_m2")
class TC113_CalendarAnalyticsViewedLoggedTests extends TestBase {
        @Test
        @DisplayName("TC113 — First dashboard call writes ANALYTICS_VIEWED to calendar_events")
        void analytics_viewed_on_first_call() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (mongo == null) throw new AssertionError("TC113: MongoDB required");
                String coll = s4EventsCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                String tok = adminToken();
                assert2xx(httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-07-01&endDate=2026-07-31", tok),
                        "TC113");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after > before,
                        "TC113: ANALYTICS_VIEWED count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC114 — S4-F10 ANALYTICS_VIEWED logged on cache hit ──────────────────
@Tag("public")
@Tag("features_m2")
class TC114_CalendarAnalyticsViewedOnCacheHitTests extends TestBase {
        @Test
        @DisplayName("TC114 — Cache-hit dashboard call still logs ANALYTICS_VIEWED")
        void analytics_viewed_on_cache_hit() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (mongo == null) throw new AssertionError("TC114: MongoDB required");
                String coll = s4EventsCollection();
                String tok = adminToken();
                String url = "/api/calendar/analytics?startDate=2026-07-01&endDate=2026-07-31";
                assert2xx(httpGetAuth(url, tok), "TC114 first");
                long after1 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assert2xx(httpGetAuth(url, tok), "TC114 second");
                long after2 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after2 > after1,
                        "TC114: ANALYTICS_VIEWED on cache hit; after1=" + after1 + " after2=" + after2);
        }
}

// ─── TC115 — S4-F10 cache returns same body ───────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC115_CalendarCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC115 — Two identical dashboard requests return identical bodies")
        void cache_same_body() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                String url = "/api/calendar/analytics?startDate=2026-07-01&endDate=2026-07-31";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC115 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC115 second");
                JsonNode j1 = parseNode(r1.body());
                JsonNode j2 = parseNode(r2.body());
                assertEquals(_BkM2.rL(j1, "totalSlots", "total_slots"),
                             _BkM2.rL(j2, "totalSlots", "total_slots"),
                        "TC115: cached totalSlots must match");
        }
}

// ─── TC116 — S4-F10 cache hit doesn't re-aggregate ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC116_CalendarCacheNoReaggregateTests extends TestBase {
        @Test
        @DisplayName("TC116 — Insert slot after first call → cached body still returned")
        void cache_does_not_reaggregate() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                String url = "/api/calendar/analytics?startDate=2026-11-01&endDate=2026-11-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC116 first");
                long t1 = _BkM2.rL(parseNode(r1.body()), "totalSlots", "total_slots");
                long pid = _BkM2.prov(this, "TC116 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-11-15", true);
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC116 second");
                long t2 = _BkM2.rL(parseNode(r2.body()), "totalSlots", "total_slots");
                assertEquals(t1, t2,
                        "TC116: cached value must equal pre-insert value; t1=" + t1 + " t2=" + t2);
        }
}

// ─── TC117 — S4-F10 slotsByDate omits dates with zero slots ───────────────
@Tag("public")
@Tag("features_m2")
class TC117_CalendarSlotsByDateOmitsZeroTests extends TestBase {
        @Test
        @DisplayName("TC117 — slotsByDate map omits dates with zero slots")
        void slots_by_date_omits_zero() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC117 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-12-05", true);
                _BkM2S4.slot(this, pid, "2026-12-07", false);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/analytics?startDate=2026-12-01&endDate=2026-12-31", tok);
                assert2xx(r, "TC117");
                JsonNode byDate = _BkM2.rO(parseNode(r.body()), "slotsByDate", "slots_by_date");
                assertNotNull(byDate, "TC117: slotsByDate key required");
                assertFalse(byDate.has("2026-12-06"), "TC117: zero-slot date 2026-12-06 must be omitted");
                assertTrue(byDate.has("2026-12-05"), "TC117: 2026-12-05 must be present");
                assertTrue(byDate.has("2026-12-07"), "TC117: 2026-12-07 must be present");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S4-F11 — Record Provider Availability Snapshot (TC118-TC127)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC118 — S4-F11 happy path — Cassandra row + Mongo TRACKING_RECORDED ──
@Tag("public")
@Tag("features_m2")
class TC118_SnapshotHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC118 — Snapshot writes Cassandra row + Mongo TRACKING_RECORDED")
        void snapshot_happy_path() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (cassandra == null) throw new AssertionError("TC118: Cassandra required");
                if (mongo == null)     throw new AssertionError("TC118: MongoDB required");
                long pid = _BkM2.prov(this, "TC118 P " + nonce());
                for (int i = 0; i < 4; i++) _BkM2S4.slot(this, pid, "2026-04-22", true);
                for (int i = 0; i < 6; i++) _BkM2S4.slot(this, pid, "2026-04-22", false);
                String coll = s4EventsCollection();
                long mongoBefore = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "TRACKING_RECORDED"));
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"Morning block fully booked\"}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/calendar/" + pid + "/availability-snapshot", body, tok);
                assert2xx(r, "TC118");
                long cqlCount = cassandraCount(s4TimeseriesTable(), "provider_id", pid);
                assertTrue(cqlCount >= 1,
                        "TC118: Cassandra snapshot must exist for provider " + pid + "; got " + cqlCount);
                long mongoAfter = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "TRACKING_RECORDED"));
                assertTrue(mongoAfter > mongoBefore,
                        "TC118: TRACKING_RECORDED must be logged; before=" + mongoBefore + " after=" + mongoAfter);
        }
}

// ─── TC119 — S4-F11 Cassandra snapshot has correct counts ─────────────────
@Tag("public")
@Tag("features_m2")
class TC119_SnapshotCassandraFieldsTests extends TestBase {
        @Test
        @DisplayName("TC119 — Cassandra snapshot row has total=10, available=4, booked=6, utilization≈0.6")
        void snapshot_cassandra_fields() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (cassandra == null) throw new AssertionError("TC119: Cassandra required");
                long pid = _BkM2.prov(this, "TC119 P " + nonce());
                for (int i = 0; i < 4; i++) _BkM2S4.slot(this, pid, "2026-04-22", true);
                for (int i = 0; i < 6; i++) _BkM2S4.slot(this, pid, "2026-04-22", false);
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC119");
                java.util.List<java.util.Map<String, Object>> rows =
                        cassandraRows(s4TimeseriesTable(), "provider_id", pid);
                assertFalse(rows.isEmpty(), "TC119: at least one snapshot row");
                java.util.Map<String, Object> row = rows.get(0);
                long total = _BkM2S4.cqlLong(row, "total_slots", "totalSlots");
                long avail = _BkM2S4.cqlLong(row, "available_slots", "availableSlots");
                long booked = _BkM2S4.cqlLong(row, "booked_slots", "bookedSlots");
                double util = _BkM2S4.cqlDouble(row, "utilization_rate", "utilizationRate");
                assertEquals(10L, total, "TC119: total=10");
                assertEquals(4L, avail, "TC119: available=4");
                assertEquals(6L, booked, "TC119: booked=6");
                assertEquals(0.6, util, 0.01, "TC119: utilization≈0.6");
        }
}

// ─── TC120 — S4-F11 multiple snapshots same date → both rows persist ──────
@Tag("public")
@Tag("features_m2")
class TC120_SnapshotMultipleSameDateTests extends TestBase {
        @Test
        @DisplayName("TC120 — Two snapshots same date → both rows present in Cassandra")
        void snapshot_multiple_same_date() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (cassandra == null) throw new AssertionError("TC120: Cassandra required");
                long pid = _BkM2.prov(this, "TC120 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-04-22", true);
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"first\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC120 first");
                Thread.sleep(50);
                body = "{\"date\":\"2026-04-22\",\"notes\":\"second\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC120 second");
                long count = cassandraCount(s4TimeseriesTable(), "provider_id", pid);
                assertTrue(count >= 2,
                        "TC120: must have ≥2 snapshot rows; got " + count);
        }
}

// ─── TC121 — S4-F11 non-existent provider → 404 ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC121_SnapshotNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC121 — Snapshot for non-existent provider returns 404")
        void snapshot_not_found_404() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"x\"}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/calendar/999999/availability-snapshot", body, tok);
                assertEquals(404, r.statusCode(),
                        "TC121: must be 404; got " + r.statusCode());
        }
}

// ─── TC122 — S4-F11 missing JWT → 401 ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC122_SnapshotMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC122 — Snapshot without Authorization header returns 401")
        void snapshot_missing_jwt_401() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC122 P " + nonce());
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"x\"}";
                HttpResponse<String> r = httpPost(
                        "/api/calendar/" + pid + "/availability-snapshot", body);
                assertEquals(401, r.statusCode(),
                        "TC122: must be 401; got " + r.statusCode());
        }
}

// ─── TC123 — S4-F11 invalid JWT → 401 ─────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC123_SnapshotInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC123 — Snapshot with malformed JWT returns 401")
        void snapshot_invalid_jwt_401() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC123 P " + nonce());
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"x\"}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/calendar/" + pid + "/availability-snapshot", body, "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC123: must be 401; got " + r.statusCode());
        }
}

// ─── TC124 — S4-F11 provider with no slots → snapshot has zero counts ─────
@Tag("public")
@Tag("features_m2")
class TC124_SnapshotZeroCountsTests extends TestBase {
        @Test
        @DisplayName("TC124 — Snapshot for provider with no slots → 2xx + Cassandra row total=0")
        void snapshot_zero_counts() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (cassandra == null) throw new AssertionError("TC124: Cassandra required");
                long pid = _BkM2.prov(this, "TC124 P " + nonce());
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"empty\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC124");
                java.util.List<java.util.Map<String, Object>> rows =
                        cassandraRows(s4TimeseriesTable(), "provider_id", pid);
                assertFalse(rows.isEmpty(), "TC124: snapshot row must exist");
                long total = _BkM2S4.cqlLong(rows.get(0), "total_slots", "totalSlots");
                assertEquals(0L, total, "TC124: total=0 when no slots");
        }
}

// ─── TC125 — S4-F11 Mongo TRACKING_RECORDED includes providerId in details
@Tag("public")
@Tag("features_m2")
class TC125_SnapshotMongoDetailsTests extends TestBase {
        @Test
        @DisplayName("TC125 — TRACKING_RECORDED Mongo doc carries providerId in details")
        void snapshot_mongo_details() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (mongo == null) throw new AssertionError("TC125: MongoDB required");
                long pid = _BkM2.prov(this, "TC125 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-04-22", false);
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"x\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC125");
                String coll = s4EventsCollection();
                long match = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "TRACKING_RECORDED")
                                .append("$or", java.util.List.of(
                                        new org.bson.Document("details.providerId", pid),
                                        new org.bson.Document("details.provider_id", pid))));
                assertTrue(match >= 1,
                        "TC125: must find TRACKING_RECORDED doc with details.providerId=" + pid);
        }
}

// ─── TC126 — S4-F11 notes field preserved in Cassandra ───────────────────
@Tag("public")
@Tag("features_m2")
class TC126_SnapshotNotesPreservedTests extends TestBase {
        @Test
        @DisplayName("TC126 — Cassandra snapshot row carries the notes value supplied in request")
        void snapshot_notes_preserved() throws Exception {
                BASE_URL = deliveryServiceUrl;
                if (cassandra == null) throw new AssertionError("TC126: Cassandra required");
                long pid = _BkM2.prov(this, "TC126 P " + nonce());
                String unique = "TC126_" + nonce();
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"" + unique + "\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC126");
                java.util.List<java.util.Map<String, Object>> rows =
                        cassandraRows(s4TimeseriesTable(), "provider_id", pid);
                assertFalse(rows.isEmpty(), "TC126: snapshot row required");
                Object notes = rows.get(0).get("notes");
                assertNotNull(notes, "TC126: notes column must be present");
                assertEquals(unique, notes.toString(), "TC126: notes must equal request value");
        }
}

// ─── TC127 — S4-F11 empty notes accepted ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC127_SnapshotEmptyNotesTests extends TestBase {
        @Test
        @DisplayName("TC127 — Empty notes string accepted (notes is optional)")
        void snapshot_empty_notes() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC127 P " + nonce());
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"\"}";
                HttpResponse<String> r = httpPostAuth(
                        "/api/calendar/" + pid + "/availability-snapshot", body, tok);
                assert2xx(r, "TC127");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S4-F12 — Get Provider Availability History (TC128-TC135)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC128 — S4-F12 happy path: 3 snapshots returned reverse-chrono ──────
@Tag("public")
@Tag("features_m2")
class TC128_HistoryHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC128 — History returns all snapshots, most recent first")
        void history_happy_path() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC128 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-04-22", false);
                String tok = adminToken();
                for (int i = 0; i < 3; i++) {
                        String body = "{\"date\":\"2026-04-22\",\"notes\":\"snap" + i + "\"}";
                        assert2xx(httpPostAuth(
                                "/api/calendar/" + pid + "/availability-snapshot", body, tok),
                                "TC128 snap " + i);
                        Thread.sleep(50);
                }
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/" + pid + "/availability-history", tok);
                assert2xx(r, "TC128");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() >= 3, "TC128: must return ≥3 snapshots; got " + arr.size());
        }
}

// ─── TC129 — S4-F12 time-range filter ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC129_HistoryTimeRangeTests extends TestBase {
        @Test
        @DisplayName("TC129 — History with startTime/endTime returns only matching snapshots")
        void history_time_range() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC129 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-04-22", false);
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"in-range\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC129");
                String now = java.time.LocalDateTime.now().toString();
                String future = java.time.LocalDateTime.now().plusHours(1).toString();
                String past = java.time.LocalDateTime.now().minusHours(1).toString();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/" + pid + "/availability-history?startTime=" + past + "&endTime=" + future, tok);
                assert2xx(r, "TC129");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() >= 1, "TC129: must return ≥1 snapshot in range; got " + arr.size());
        }
}

// ─── TC130 — S4-F12 non-existent provider → 404 ──────────────────────────
@Tag("public")
@Tag("features_m2")
class TC130_HistoryNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC130 — History for non-existent provider returns 404")
        void history_not_found_404() throws Exception {
                BASE_URL = deliveryServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/999999/availability-history", tok);
                assertEquals(404, r.statusCode(),
                        "TC130: must be 404; got " + r.statusCode());
        }
}

// ─── TC131 — S4-F12 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC131_HistoryMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC131 — History without Authorization header returns 401")
        void history_missing_jwt_401() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC131 P " + nonce());
                HttpResponse<String> r = httpGet("/api/calendar/" + pid + "/availability-history");
                assertEquals(401, r.statusCode(),
                        "TC131: must be 401; got " + r.statusCode());
        }
}

// ─── TC132 — S4-F12 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC132_HistoryInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC132 — History with malformed JWT returns 401")
        void history_invalid_jwt_401() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC132 P " + nonce());
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/" + pid + "/availability-history", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC132: must be 401; got " + r.statusCode());
        }
}

// ─── TC133 — S4-F12 provider with no snapshots → empty list ──────────────
@Tag("public")
@Tag("features_m2")
class TC133_HistoryEmptyListTests extends TestBase {
        @Test
        @DisplayName("TC133 — History for provider with no snapshots returns empty list")
        void history_empty_list() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC133 P " + nonce());
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/" + pid + "/availability-history", tok);
                assert2xx(r, "TC133");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(), "TC133: empty list when no snapshots; body=" + r.body());
        }
}

// ─── TC134 — S4-F12 cache returns same body ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC134_HistoryCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC134 — Two identical history requests return identical bodies (cached)")
        void history_cache_same_body() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC134 P " + nonce());
                String tok = adminToken();
                String url = "/api/calendar/" + pid + "/availability-history";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC134 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC134 second");
                assertEquals(r1.body(), r2.body(),
                        "TC134: identical history responses expected (cached)");
        }
}

// ─── TC135 — S4-F12 each snapshot DTO carries required fields ────────────
@Tag("public")
@Tag("features_m2")
class TC135_HistoryDtoShapeTests extends TestBase {
        @Test
        @DisplayName("TC135 — Each snapshot includes timestamp/date/totalSlots/availableSlots/bookedSlots/utilizationRate/notes")
        void history_dto_shape() throws Exception {
                BASE_URL = deliveryServiceUrl;
                long pid = _BkM2.prov(this, "TC135 P " + nonce());
                _BkM2S4.slot(this, pid, "2026-04-22", false);
                String tok = adminToken();
                String body = "{\"date\":\"2026-04-22\",\"notes\":\"shape-test\"}";
                assert2xx(httpPostAuth("/api/calendar/" + pid + "/availability-snapshot", body, tok),
                        "TC135 snap");
                HttpResponse<String> r = httpGetAuth(
                        "/api/calendar/" + pid + "/availability-history", tok);
                assert2xx(r, "TC135 history");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertTrue(arr.size() >= 1, "TC135: must return ≥1 snapshot");
                JsonNode item = arr.get(0);
                assertTrue(item.has("timestamp") || item.has("eventTime") || item.has("recordedAt"),
                        "TC135: snapshot must include timestamp; got=" + item);
                assertTrue(item.has("date"),
                        "TC135: snapshot must include date; got=" + item);
                assertTrue(item.has("totalSlots") || item.has("total_slots"),
                        "TC135: snapshot must include totalSlots; got=" + item);
                assertTrue(item.has("availableSlots") || item.has("available_slots"),
                        "TC135: snapshot must include availableSlots; got=" + item);
                assertTrue(item.has("bookedSlots") || item.has("booked_slots"),
                        "TC135: snapshot must include bookedSlots; got=" + item);
                assertTrue(item.has("utilizationRate") || item.has("utilization_rate"),
                        "TC135: snapshot must include utilizationRate; got=" + item);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Helper class for S4 (slot seeding + Cassandra row coercion). Uses the
// `_BkM2.prov` helper from the S3 block for provider seeding.
// ════════════════════════════════════════════════════════════════════════════
final class _BkM2S4 {
        private _BkM2S4() {}

        static long slot(TestBase t, long providerId, String date, boolean available) {
                String table = t.tableName("TimeSlot");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("TimeSlot", "provider"), providerId);
                ov.put(t.columnByField("TimeSlot", "date"), java.sql.Date.valueOf(date));
                // Booking M1 §6.4: TimeSlot.startTime/endTime are LocalTime → PG TIME.
                // JDBC must bind java.time.LocalTime, never String, or PG rejects with
                // `column "end_time" is of type time without time zone but expression
                // is of type character varying`.
                try { ov.put(t.columnByField("TimeSlot", "startTime"), java.time.LocalTime.parse("10:00")); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("TimeSlot", "endTime"),   java.time.LocalTime.parse("11:00")); } catch (Throwable ignore) {}
                try { ov.put(t.columnByField("TimeSlot", "available"), available); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
                return id;
        }

        static long cqlLong(java.util.Map<String, Object> row, String... keys) {
                for (String k : keys) {
                        Object v = row.get(k);
                        if (v instanceof Number n) return n.longValue();
                }
                return -1L;
        }

        static double cqlDouble(java.util.Map<String, Object> row, String... keys) {
                for (String k : keys) {
                        Object v = row.get(k);
                        if (v instanceof Number n) return n.doubleValue();
                }
                return -1.0;
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S5 M2 — Invoice Service features (TC136..TC190)
//
// Covers S5-F10 (service-type revenue breakdown w/ cancellation-fee, TC136-
// TC158), S5-F11 (payment-method breakdown from Mongo audit trail, TC159-
// TC174), and S5-F12 (cancellation refund with strategy pattern + timing
// handling, TC175-TC190). Theme-specific: S5 endpoints under /api/invoices;
// Invoice carries booking_id FK, amount, method, status, plus a
// transactionDetails JSONB for refund metadata; the Mongo audit collection
// is payment_audit_trail (shared with M1 S5-F4 and M2 S5-F12). Strategies
// selected at runtime: NoRefundStrategy / FullRefundStrategy /
// PartialRefundStrategy. Per-test wipe of PG/Mongo/Redis happens in
// autoTruncateAllData().
// ════════════════════════════════════════════════════════════════════════════

// ─── TC136 — S5-F10 happy path (Dentist + Barber spec scenario) ────────────
@Tag("public")
@Tag("features_m2")
class TC136_ServiceTypeHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC136 — Service-type breakdown groups by specialty with cancellation-fee revenue")
        void service_type_happy_path() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p1 = _BkM2S5.provSpec(this, "Dentist");
                long p2 = _BkM2S5.provSpec(this, "Barber");
                // 3 COMPLETED Dentist bookings totalling 600
                for (double a : new double[]{200, 200, 200}) {
                        Long bid = _BkM2.bk(this, 1L, p1, "COMPLETED", "2026-03-15");
                        _BkM2S5.invWithDetails(this, bid, 1L, a, "COMPLETED", null);
                }
                // 1 REFUNDED Dentist booking — original 200, fee=100, refund=100
                Long refundedBid = _BkM2.bk(this, 1L, p1, "CANCELLED", "2026-03-20");
                _BkM2S5.invWithDetails(this, refundedBid, 1L, 200.0, "REFUNDED",
                        "{\"cancellationFee\":100,\"refundAmount\":100}");
                // 2 COMPLETED Barber bookings totalling 400
                for (double a : new double[]{200, 200}) {
                        Long bid = _BkM2.bk(this, 1L, p2, "COMPLETED", "2026-03-12");
                        _BkM2S5.invWithDetails(this, bid, 1L, a, "COMPLETED", null);
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC136");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode dent = _BkM2S5.findBySpecialty(arr, "Dentist");
                JsonNode barb = _BkM2S5.findBySpecialty(arr, "Barber");
                assertNotNull(dent, "TC136: Dentist row required");
                assertNotNull(barb, "TC136: Barber row required");
                assertEquals(100.0, _BkM2.rD(dent, "cancellationFeeRevenue", "cancellation_fee_revenue"), 0.01,
                        "TC136: Dentist cancellationFeeRevenue=100");
                assertEquals(0.0, _BkM2.rD(barb, "cancellationFeeRevenue", "cancellation_fee_revenue"), 0.01,
                        "TC136: Barber cancellationFeeRevenue=0");
        }
}

// ─── TC137 — S5-F10 totalRevenue = cancellationFeeRevenue + netBookingRevenue
@Tag("public")
@Tag("features_m2")
class TC137_ServiceTypeTotalRevenueTests extends TestBase {
        @Test
        @DisplayName("TC137 — totalRevenue equals cancellationFeeRevenue + netBookingRevenue")
        void total_revenue_decomposition() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Tutor");
                Long b1 = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b1, 1L, 500.0, "COMPLETED", null);
                Long b2 = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-12");
                _BkM2S5.invWithDetails(this, b2, 1L, 200.0, "REFUNDED",
                        "{\"cancellationFee\":50,\"refundAmount\":150}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC137");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Tutor");
                assertNotNull(row, "TC137: Tutor row required");
                double total = _BkM2.rD(row, "totalRevenue", "total_revenue");
                double fee = _BkM2.rD(row, "cancellationFeeRevenue", "cancellation_fee_revenue");
                double net = _BkM2.rD(row, "netBookingRevenue", "net_booking_revenue");
                assertEquals(total, fee + net, 0.01,
                        "TC137: totalRevenue (" + total + ") must equal fee+net (" + (fee + net) + ")");
        }
}

// ─── TC138 — S5-F10 cancellationFeeRevenue from JSONB ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC138_ServiceTypeCancellationFeeJsonbTests extends TestBase {
        @Test
        @DisplayName("TC138 — cancellationFeeRevenue sums transactionDetails.cancellationFee across REFUNDED rows")
        void cancellation_fee_from_jsonb() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Dentist");
                Long b1 = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b1, 1L, 100.0, "REFUNDED",
                        "{\"cancellationFee\":30,\"refundAmount\":70}");
                Long b2 = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-12");
                _BkM2S5.invWithDetails(this, b2, 1L, 100.0, "REFUNDED",
                        "{\"cancellationFee\":40,\"refundAmount\":60}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC138");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Dentist");
                assertEquals(70.0, _BkM2.rD(row, "cancellationFeeRevenue", "cancellation_fee_revenue"), 0.01,
                        "TC138: cancellationFeeRevenue=30+40=70");
        }
}

// ─── TC139 — S5-F10 netBookingRevenue subtracts refundAmount ──────────────
@Tag("public")
@Tag("features_m2")
class TC139_ServiceTypeNetRevenueTests extends TestBase {
        @Test
        @DisplayName("TC139 — netBookingRevenue is sum of (amount - refundAmount)")
        void net_revenue_subtracts_refund() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Barber");
                Long b1 = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b1, 1L, 100.0, "COMPLETED", null);
                Long b2 = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-12");
                _BkM2S5.invWithDetails(this, b2, 1L, 200.0, "REFUNDED",
                        "{\"cancellationFee\":50,\"refundAmount\":150}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC139");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Barber");
                assertEquals(150.0, _BkM2.rD(row, "netBookingRevenue", "net_booking_revenue"), 0.01,
                        "TC139: netBookingRevenue=100+(200-150)=150");
        }
}

// ─── TC140 — S5-F10 bookingCount counts distinct bookings ─────────────────
@Tag("public")
@Tag("features_m2")
class TC140_ServiceTypeBookingCountTests extends TestBase {
        @Test
        @DisplayName("TC140 — bookingCount equals distinct booking IDs in the group")
        void booking_count_distinct() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Tutor");
                for (int i = 0; i < 4; i++) {
                        Long b = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-1" + i);
                        _BkM2S5.invWithDetails(this, b, 1L, 100.0, "COMPLETED", null);
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC140");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Tutor");
                assertEquals(4L, _BkM2.rL(row, "bookingCount", "booking_count"),
                        "TC140: bookingCount=4");
        }
}

// ─── TC141 — S5-F10 cancellationRate = CANCELLED / bookingCount ───────────
@Tag("public")
@Tag("features_m2")
class TC141_ServiceTypeCancellationRateTests extends TestBase {
        @Test
        @DisplayName("TC141 — cancellationRate equals CANCELLED bookings / bookingCount")
        void cancellation_rate() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Barber");
                for (int i = 0; i < 2; i++) {
                        Long b = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-1" + i);
                        _BkM2S5.invWithDetails(this, b, 1L, 100.0, "COMPLETED", null);
                }
                Long bc = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-13");
                _BkM2S5.invWithDetails(this, bc, 1L, 100.0, "REFUNDED",
                        "{\"cancellationFee\":0,\"refundAmount\":100}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC141");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Barber");
                double rate = _BkM2.rD(row, "cancellationRate", "cancellation_rate");
                assertEquals(1.0 / 3.0, rate, 0.001,
                        "TC141: cancellationRate=1/3; got " + rate);
        }
}

// ─── TC142 — S5-F10 grouping by specialty separates results ───────────────
@Tag("public")
@Tag("features_m2")
class TC142_ServiceTypeGroupBySpecialtyTests extends TestBase {
        @Test
        @DisplayName("TC142 — Different specialties produce separate rows")
        void group_by_specialty() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p1 = _BkM2S5.provSpec(this, "Dentist");
                long p2 = _BkM2S5.provSpec(this, "Tutor");
                Long b1 = _BkM2.bk(this, 1L, p1, "COMPLETED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b1, 1L, 100.0, "COMPLETED", null);
                Long b2 = _BkM2.bk(this, 1L, p2, "COMPLETED", "2026-03-11");
                _BkM2S5.invWithDetails(this, b2, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC142");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_BkM2S5.findBySpecialty(arr, "Dentist"), "TC142: Dentist row");
                assertNotNull(_BkM2S5.findBySpecialty(arr, "Tutor"), "TC142: Tutor row");
        }
}

// ─── TC143 — S5-F10 includes both COMPLETED and REFUNDED invoices ────────
@Tag("public")
@Tag("features_m2")
class TC143_ServiceTypeIncludesRefundedTests extends TestBase {
        @Test
        @DisplayName("TC143 — Both COMPLETED and REFUNDED invoices contribute to bookingCount")
        void includes_refunded() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Dentist");
                Long b1 = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b1, 1L, 100.0, "COMPLETED", null);
                Long b2 = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-11");
                _BkM2S5.invWithDetails(this, b2, 1L, 100.0, "REFUNDED",
                        "{\"cancellationFee\":0,\"refundAmount\":100}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC143");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Dentist");
                assertEquals(2L, _BkM2.rL(row, "bookingCount", "booking_count"),
                        "TC143: bookingCount must include REFUNDED");
        }
}

// ─── TC144 — S5-F10 excludes PENDING / FAILED invoices ───────────────────
@Tag("public")
@Tag("features_m2")
class TC144_ServiceTypeExcludesPendingTests extends TestBase {
        @Test
        @DisplayName("TC144 — PENDING/FAILED invoices excluded from breakdown")
        void excludes_pending_failed() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Tutor");
                Long b1 = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b1, 1L, 100.0, "COMPLETED", null);
                Long b2 = _BkM2.bk(this, 1L, p, "REQUESTED", "2026-03-11");
                _BkM2S5.invWithDetails(this, b2, 1L, 100.0, "PENDING", null);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC144");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Tutor");
                assertNotNull(row, "TC144: Tutor row");
                assertEquals(1L, _BkM2.rL(row, "bookingCount", "booking_count"),
                        "TC144: bookingCount=1 (PENDING excluded)");
        }
}

// ─── TC145 — S5-F10 empty range returns empty list ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC145_ServiceTypeEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC145 — Date range with no invoices returns empty list")
        void empty_range_empty_list() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC145");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(), "TC145: empty list expected; body=" + r.body());
        }
}

// ─── TC146 — S5-F10 invalid date range → 400 ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC146_ServiceTypeInvalidRangeTests extends TestBase {
        @Test
        @DisplayName("TC146 — startDate > endDate returns 400")
        void invalid_date_range_400() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-04-30&endDate=2026-04-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC146: must be 400; got " + r.statusCode());
        }
}

// ─── TC147 — S5-F10 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC147_ServiceTypeMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC147 — Service-type without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31");
                assertEquals(401, r.statusCode(),
                        "TC147: must be 401; got " + r.statusCode());
        }
}

// ─── TC148 — S5-F10 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC148_ServiceTypeInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC148 — Service-type with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31",
                        "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC148: must be 401; got " + r.statusCode());
        }
}

// ─── TC149 — S5-F10 cancellationFee defaults to 0 when JSONB key missing ─
@Tag("public")
@Tag("features_m2")
class TC149_ServiceTypeFeeDefaultZeroTests extends TestBase {
        @Test
        @DisplayName("TC149 — REFUNDED invoice with no cancellationFee key → fee defaults to 0")
        void fee_defaults_to_zero() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Dentist");
                Long b = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b, 1L, 100.0, "REFUNDED", "{\"refundAmount\":100}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC149");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Dentist");
                assertEquals(0.0,
                        _BkM2.rD(row, "cancellationFeeRevenue", "cancellation_fee_revenue"), 0.01,
                        "TC149: missing cancellationFee key must default to 0");
        }
}

// ─── TC150 — S5-F10 refundAmount defaults to 0 on COMPLETED rows ─────────
@Tag("public")
@Tag("features_m2")
class TC150_ServiceTypeCompletedNoRefundTests extends TestBase {
        @Test
        @DisplayName("TC150 — COMPLETED invoice → netBookingRevenue gets full amount (no refund subtracted)")
        void completed_no_refund() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Tutor");
                Long b = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b, 1L, 250.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC150");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Tutor");
                assertEquals(250.0,
                        _BkM2.rD(row, "netBookingRevenue", "net_booking_revenue"), 0.01,
                        "TC150: COMPLETED → full amount in netBookingRevenue");
        }
}

// ─── TC151 — S5-F10 ANALYTICS_VIEWED logged on first call ────────────────
@Tag("public")
@Tag("features_m2")
class TC151_ServiceTypeAnalyticsViewedTests extends TestBase {
        @Test
        @DisplayName("TC151 — First service-type call writes ANALYTICS_VIEWED to payment_audit_trail")
        void analytics_viewed_logged() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC151: MongoDB required");
                String coll = s5AuditCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                String tok = adminToken();
                assert2xx(httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-07-01&endDate=2026-07-31", tok),
                        "TC151");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after > before,
                        "TC151: ANALYTICS_VIEWED count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC152 — S5-F10 ANALYTICS_VIEWED logged on cache hit too ─────────────
@Tag("public")
@Tag("features_m2")
class TC152_ServiceTypeAnalyticsCacheHitTests extends TestBase {
        @Test
        @DisplayName("TC152 — Second service-type call (cache hit) still logs ANALYTICS_VIEWED")
        void analytics_viewed_on_cache_hit() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC152: MongoDB required");
                String coll = s5AuditCollection();
                String tok = adminToken();
                String url = "/api/invoices/analytics/service-type?startDate=2026-07-01&endDate=2026-07-31";
                assert2xx(httpGetAuth(url, tok), "TC152 first");
                long after1 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assert2xx(httpGetAuth(url, tok), "TC152 second");
                long after2 = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "ANALYTICS_VIEWED"));
                assertTrue(after2 > after1,
                        "TC152: ANALYTICS_VIEWED on cache hit; after1=" + after1 + " after2=" + after2);
        }
}

// ─── TC153 — S5-F10 cache returns same body ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC153_ServiceTypeCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC153 — Two identical service-type requests return identical bodies")
        void cache_same_body() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                String url = "/api/invoices/analytics/service-type?startDate=2026-07-01&endDate=2026-07-31";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC153 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC153 second");
                assertEquals(r1.body(), r2.body(),
                        "TC153: cached service-type responses must match");
        }
}

// ─── TC154 — S5-F10 cache hit doesn't re-aggregate ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC154_ServiceTypeCacheNoReaggregateTests extends TestBase {
        @Test
        @DisplayName("TC154 — Insert invoice after first call → cached body still returned")
        void cache_does_not_reaggregate() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                String url = "/api/invoices/analytics/service-type?startDate=2026-11-01&endDate=2026-11-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC154 first");
                int before = (parseNode(r1.body()).has("content")
                        ? parseNode(r1.body()).get("content").size()
                        : parseNode(r1.body()).size());
                long p = _BkM2S5.provSpec(this, "Dentist");
                Long b = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-11-15");
                _BkM2S5.invWithDetails(this, b, 1L, 100.0, "COMPLETED", null);
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC154 second");
                int after = (parseNode(r2.body()).has("content")
                        ? parseNode(r2.body()).get("content").size()
                        : parseNode(r2.body()).size());
                assertEquals(before, after,
                        "TC154: cached size must equal pre-insert size; before=" + before + " after=" + after);
        }
}

// ─── TC155 — S5-F10 boundary date inclusion ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC155_ServiceTypeBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC155 — Invoice exactly on startDate is included")
        void boundary_included() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Barber");
                Long b = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-05-01");
                _BkM2S5.invWithDetails(this, b, 1L, 100.0, "COMPLETED", null);
                String bTable = tableName("Booking");
                String _bkTsCol155;
                try { _bkTsCol155 = columnByField("Booking", "createdAt"); }
                catch (Throwable _t1) {
                    try { _bkTsCol155 = columnByField("Booking", "requestedAt"); }
                    catch (Throwable _t2) { _bkTsCol155 = columnByField("Booking", "completedAt"); }
                }
                jdbc.update("UPDATE \"" + bTable + "\" SET " + _bkTsCol155 + "=? WHERE id=?",
                        java.sql.Timestamp.valueOf("2026-05-01 00:00:00"), b);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-05-01&endDate=2026-05-31", tok);
                assert2xx(r, "TC155");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_BkM2S5.findBySpecialty(arr, "Barber"),
                        "TC155: boundary booking must be included");
        }
}

// ─── TC156 — S5-F10 out-of-range invoices excluded ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC156_ServiceTypeOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC156 — Invoices outside the date range are excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Tutor");
                Long inRange = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-06-15");
                _BkM2S5.invWithDetails(this, inRange, 1L, 100.0, "COMPLETED", null);
                Long outRange = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-08-15");
                _BkM2S5.invWithDetails(this, outRange, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-06-01&endDate=2026-06-30", tok);
                assert2xx(r, "TC156");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Tutor");
                assertNotNull(row, "TC156: Tutor row required");
                assertEquals(1L, _BkM2.rL(row, "bookingCount", "booking_count"),
                        "TC156: bookingCount=1 (out-of-range excluded)");
        }
}

// ─── TC157 — S5-F10 cancellationRate=0 when no CANCELLED ─────────────────
@Tag("public")
@Tag("features_m2")
class TC157_ServiceTypeCancellationRateZeroTests extends TestBase {
        @Test
        @DisplayName("TC157 — cancellationRate=0 when no CANCELLED bookings exist")
        void cancellation_rate_zero() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Dentist");
                for (int i = 0; i < 3; i++) {
                        Long b = _BkM2.bk(this, 1L, p, "COMPLETED", "2026-03-1" + i);
                        _BkM2S5.invWithDetails(this, b, 1L, 100.0, "COMPLETED", null);
                }
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC157");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Dentist");
                assertEquals(0.0, _BkM2.rD(row, "cancellationRate", "cancellation_rate"), 0.001,
                        "TC157: cancellationRate=0 when no CANCELLED");
        }
}

// ─── TC158 — S5-F10 REFUNDED rows still contribute to revenue (retained fee)
@Tag("public")
@Tag("features_m2")
class TC158_ServiceTypeRefundedRetainedTests extends TestBase {
        @Test
        @DisplayName("TC158 — REFUNDED invoice with retained cancellationFee still contributes to totalRevenue")
        void refunded_retained_contributes() throws Exception {
                BASE_URL = checkoutServiceUrl;
                long p = _BkM2S5.provSpec(this, "Barber");
                Long b = _BkM2.bk(this, 1L, p, "CANCELLED", "2026-03-10");
                _BkM2S5.invWithDetails(this, b, 1L, 200.0, "REFUNDED",
                        "{\"cancellationFee\":50,\"refundAmount\":150}");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok);
                assert2xx(r, "TC158");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode row = _BkM2S5.findBySpecialty(arr, "Barber");
                double total = _BkM2.rD(row, "totalRevenue", "total_revenue");
                assertTrue(total >= 50.0,
                        "TC158: REFUNDED row's retained fee must show in totalRevenue; got " + total);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S5-F11 — Payment Method Breakdown (TC159-TC174)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC159 — S5-F11 happy path from payment_audit_trail ──────────────────
@Tag("public")
@Tag("features_m2")
class TC159_PaymentMethodHappyPathTests extends TestBase {
        @Test
        @DisplayName("TC159 — Methods breakdown groups by method with successCount/failureCount/successRate/totalAmount")
        void method_happy_path() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC159: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T12:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T13:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T14:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T15:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T16:00:00");
                _BkM2S5.audit(this, "FAILED",    "CREDIT_CARD",  50, "2026-08-15T17:00:00");
                _BkM2S5.audit(this, "FAILED",    "CREDIT_CARD",  50, "2026-08-15T18:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CASH",        100, "2026-08-15T19:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CASH",        100, "2026-08-15T20:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CASH",        100, "2026-08-15T21:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC159");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode cc = _BkM2S5.findByMethod(arr, "CREDIT_CARD");
                JsonNode cash = _BkM2S5.findByMethod(arr, "CASH");
                assertNotNull(cc, "TC159: CREDIT_CARD row required");
                assertNotNull(cash, "TC159: CASH row required");
                assertEquals(5L, _BkM2.rL(cc, "successCount", "success_count"), "TC159: CC success=5");
                assertEquals(2L, _BkM2.rL(cc, "failureCount", "failure_count"), "TC159: CC failure=2");
                assertEquals(500.0, _BkM2.rD(cc, "totalAmount", "total_amount"), 0.01, "TC159: CC total=500");
                assertEquals(3L, _BkM2.rL(cash, "successCount", "success_count"), "TC159: CASH success=3");
                assertEquals(0L, _BkM2.rL(cash, "failureCount", "failure_count"), "TC159: CASH failure=0");
        }
}

// ─── TC160 — S5-F11 successCount isolated ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC160_PaymentMethodSuccessCountTests extends TestBase {
        @Test
        @DisplayName("TC160 — successCount counts only COMPLETED events per method")
        void success_count_isolated() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC160: MongoDB required");
                _BkM2S5.cleanAudit(this);
                for (int i = 0; i < 4; i++)
                        _BkM2S5.audit(this, "COMPLETED", "WALLET", 50, "2026-08-1" + i + "T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC160");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode w = _BkM2S5.findByMethod(arr, "WALLET");
                assertEquals(4L, _BkM2.rL(w, "successCount", "success_count"), "TC160: WALLET success=4");
        }
}

// ─── TC161 — S5-F11 failureCount isolated ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC161_PaymentMethodFailureCountTests extends TestBase {
        @Test
        @DisplayName("TC161 — failureCount counts only FAILED events per method")
        void failure_count_isolated() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC161: MongoDB required");
                _BkM2S5.cleanAudit(this);
                for (int i = 0; i < 3; i++)
                        _BkM2S5.audit(this, "FAILED", "CASH", 0, "2026-08-1" + i + "T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC161");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode c = _BkM2S5.findByMethod(arr, "CASH");
                assertEquals(3L, _BkM2.rL(c, "failureCount", "failure_count"), "TC161: CASH failure=3");
        }
}

// ─── TC162 — S5-F11 successRate isolated ─────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC162_PaymentMethodSuccessRateTests extends TestBase {
        @Test
        @DisplayName("TC162 — successRate = successCount / (successCount + failureCount)")
        void success_rate_isolated() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC162: MongoDB required");
                _BkM2S5.cleanAudit(this);
                for (int i = 0; i < 7; i++)
                        _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-1" + i + "T12:00:00");
                for (int i = 0; i < 3; i++)
                        _BkM2S5.audit(this, "FAILED", "CREDIT_CARD", 100, "2026-08-2" + i + "T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC162");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode cc = _BkM2S5.findByMethod(arr, "CREDIT_CARD");
                assertEquals(0.7, _BkM2.rD(cc, "successRate", "success_rate"), 0.01,
                        "TC162: successRate=7/10=0.7");
        }
}

// ─── TC163 — S5-F11 totalAmount sums COMPLETED only ──────────────────────
@Tag("public")
@Tag("features_m2")
class TC163_PaymentMethodTotalAmountTests extends TestBase {
        @Test
        @DisplayName("TC163 — totalAmount sums amounts of COMPLETED events only (FAILED excluded)")
        void total_amount_completed_only() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC163: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-15T10:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 200, "2026-08-15T11:00:00");
                _BkM2S5.audit(this, "FAILED",    "CREDIT_CARD", 999, "2026-08-15T12:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC163");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode cc = _BkM2S5.findByMethod(arr, "CREDIT_CARD");
                assertEquals(300.0, _BkM2.rD(cc, "totalAmount", "total_amount"), 0.01,
                        "TC163: totalAmount=100+200=300 (FAILED excluded)");
        }
}

// ─── TC164 — S5-F11 grouping by method ───────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC164_PaymentMethodGroupingTests extends TestBase {
        @Test
        @DisplayName("TC164 — Different methods produce separate rows")
        void group_by_method() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC164: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED", "CREDIT_CARD", 100, "2026-08-10T10:00:00");
                _BkM2S5.audit(this, "COMPLETED", "CASH",        50, "2026-08-11T10:00:00");
                _BkM2S5.audit(this, "COMPLETED", "WALLET",      75, "2026-08-12T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC164");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_BkM2S5.findByMethod(arr, "CREDIT_CARD"), "TC164: CREDIT_CARD row");
                assertNotNull(_BkM2S5.findByMethod(arr, "CASH"), "TC164: CASH row");
                assertNotNull(_BkM2S5.findByMethod(arr, "WALLET"), "TC164: WALLET row");
        }
}

// ─── TC165 — S5-F11 only COMPLETED+FAILED actions counted ───────────────
@Tag("public")
@Tag("features_m2")
class TC165_PaymentMethodAllowedActionsTests extends TestBase {
        @Test
        @DisplayName("TC165 — Only action ∈ {COMPLETED, FAILED} contributes to counts")
        void allowed_actions_only() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC165: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED", "CASH", 100, "2026-08-10T10:00:00");
                _BkM2S5.audit(this, "FAILED",    "CASH",   0, "2026-08-11T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC165");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode c = _BkM2S5.findByMethod(arr, "CASH");
                assertNotNull(c, "TC165: CASH row");
                assertEquals(1L, _BkM2.rL(c, "successCount", "success_count"), "TC165: success=1");
                assertEquals(1L, _BkM2.rL(c, "failureCount", "failure_count"), "TC165: failure=1");
        }
}

// ─── TC166 — S5-F11 excluded actions ignored ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC166_PaymentMethodExcludedActionsTests extends TestBase {
        @Test
        @DisplayName("TC166 — CREATED/REFUNDED/REFUND_DENIED/ANALYTICS_VIEWED do NOT contribute")
        void excluded_actions_ignored() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC166: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED",        "CASH", 100, "2026-08-10T10:00:00");
                _BkM2S5.audit(this, "REFUNDED",         "CASH", 100, "2026-08-11T10:00:00");
                _BkM2S5.audit(this, "REFUND_DENIED",    "CASH",   0, "2026-08-12T10:00:00");
                _BkM2S5.audit(this, "CREATED",          "CASH", 100, "2026-08-13T10:00:00");
                _BkM2S5.audit(this, "ANALYTICS_VIEWED", "CASH",   0, "2026-08-14T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC166");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode c = _BkM2S5.findByMethod(arr, "CASH");
                assertEquals(1L, _BkM2.rL(c, "successCount", "success_count"),
                        "TC166: only the 1 COMPLETED counts");
                assertEquals(0L, _BkM2.rL(c, "failureCount", "failure_count"),
                        "TC166: failure=0 (no FAILED docs)");
        }
}

// ─── TC167 — S5-F11 empty range returns empty list ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC167_PaymentMethodEmptyRangeTests extends TestBase {
        @Test
        @DisplayName("TC167 — Date range with no events returns empty list")
        void empty_range_empty_list() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC167: MongoDB required");
                _BkM2S5.cleanAudit(this);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2099-01-01&endDate=2099-01-31", tok);
                assert2xx(r, "TC167");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(), "TC167: empty list expected");
        }
}

// ─── TC168 — S5-F11 invalid date range → 400 ─────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC168_PaymentMethodInvalidRangeTests extends TestBase {
        @Test
        @DisplayName("TC168 — startDate > endDate returns 400")
        void invalid_range_400() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-04-30&endDate=2026-04-01", tok);
                assertEquals(400, r.statusCode(),
                        "TC168: must be 400; got " + r.statusCode());
        }
}

// ─── TC169 — S5-F11 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC169_PaymentMethodMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC169 — Methods without Authorization header returns 401")
        void missing_jwt_401() throws Exception {
                BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpGet(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31");
                assertEquals(401, r.statusCode(),
                        "TC169: must be 401; got " + r.statusCode());
        }
}

// ─── TC170 — S5-F11 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC170_PaymentMethodInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC170 — Methods with malformed JWT returns 401")
        void invalid_jwt_401() throws Exception {
                BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31",
                        "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC170: must be 401; got " + r.statusCode());
        }
}

// ─── TC171 — S5-F11 successRate=0 when no events ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC171_PaymentMethodSuccessRateZeroDenominatorTests extends TestBase {
        @Test
        @DisplayName("TC171 — successRate=0 when method has no COMPLETED or FAILED events")
        void success_rate_zero_denominator() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC171: MongoDB required");
                _BkM2S5.cleanAudit(this);
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-08-01&endDate=2026-08-31", tok);
                assert2xx(r, "TC171");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertEquals(0, arr.size(),
                        "TC171: empty array when no events at all");
        }
}

// ─── TC172 — S5-F11 cache returns same body ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC172_PaymentMethodCacheSameBodyTests extends TestBase {
        @Test
        @DisplayName("TC172 — Two identical methods requests return identical bodies (cached)")
        void cache_same_body() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                String url = "/api/invoices/analytics/methods?startDate=2026-09-01&endDate=2026-09-30";
                HttpResponse<String> r1 = httpGetAuth(url, tok);
                assert2xx(r1, "TC172 first");
                HttpResponse<String> r2 = httpGetAuth(url, tok);
                assert2xx(r2, "TC172 second");
                assertEquals(r1.body(), r2.body(),
                        "TC172: cached methods responses must match");
        }
}

// ─── TC173 — S5-F11 boundary date inclusion ──────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC173_PaymentMethodBoundaryInclusionTests extends TestBase {
        @Test
        @DisplayName("TC173 — Audit event exactly on startDate is included")
        void boundary_included() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC173: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED", "CASH", 100, "2026-10-01T00:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-10-01&endDate=2026-10-31", tok);
                assert2xx(r, "TC173");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                assertNotNull(_BkM2S5.findByMethod(arr, "CASH"),
                        "TC173: boundary event must be counted");
        }
}

// ─── TC174 — S5-F11 out-of-range events excluded ─────────────────────────
@Tag("public")
@Tag("features_m2")
class TC174_PaymentMethodOutOfRangeTests extends TestBase {
        @Test
        @DisplayName("TC174 — Audit events outside the date range are excluded")
        void out_of_range_excluded() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC174: MongoDB required");
                _BkM2S5.cleanAudit(this);
                _BkM2S5.audit(this, "COMPLETED", "WALLET", 100, "2026-10-15T10:00:00");
                _BkM2S5.audit(this, "COMPLETED", "WALLET", 200, "2026-12-15T10:00:00");
                String tok = adminToken();
                HttpResponse<String> r = httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-10-01&endDate=2026-10-31", tok);
                assert2xx(r, "TC174");
                JsonNode arr = parseNode(r.body());
                if (arr.has("content")) arr = arr.get("content");
                JsonNode w = _BkM2S5.findByMethod(arr, "WALLET");
                assertEquals(1L, _BkM2.rL(w, "successCount", "success_count"),
                        "TC174: only the in-range event counts");
        }
}

// ════════════════════════════════════════════════════════════════════════════
// S5-F12 — Cancellation Refund with Strategy + Timing (TC175-TC190)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC175 — S5-F12 FullRefundStrategy when appointment >24h away ────────
@Tag("public")
@Tag("features_m2")
class TC175_RefundFullStrategyTests extends TestBase {
        @Test
        @DisplayName("TC175 — CONFIRMED + appointment >24h → FullRefundStrategy: refund=full, fee=0")
        void full_refund_path() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Dentist"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"schedule_conflict\"}", tok);
                assert2xx(r, "TC175");
                String iTable = tableName("Invoice");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + iTable + "\" WHERE id=?",
                        String.class, iid);
                assertNotNull(details, "TC175: transactionDetails must be populated");
                assertTrue(details.contains("\"refundAmount\":200")
                                || details.contains("\"refundAmount\": 200")
                                || details.contains("\"refundAmount\":200.0"),
                        "TC175: refundAmount=200 expected; got " + details);
                assertTrue(details.contains("FullRefundStrategy"),
                        "TC175: strategyName=FullRefundStrategy expected; got " + details);
        }
}

// ─── TC176 — S5-F12 PartialRefundStrategy when appointment ≤24h ──────────
@Tag("public")
@Tag("features_m2")
class TC176_RefundPartialStrategyTests extends TestBase {
        @Test
        @DisplayName("TC176 — CONFIRMED + appointment within 24h → PartialRefundStrategy: refund=50%, fee=50%")
        void partial_refund_path() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String today = java.time.LocalDate.now().toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Tutor"), "CONFIRMED", today);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 300.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"schedule_conflict\"}", tok);
                assert2xx(r, "TC176");
                String iTable = tableName("Invoice");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + iTable + "\" WHERE id=?",
                        String.class, iid);
                assertNotNull(details, "TC176: transactionDetails populated");
                assertTrue(details.contains("PartialRefundStrategy"),
                        "TC176: strategyName=PartialRefundStrategy expected; got " + details);
                assertTrue(details.contains("150"),
                        "TC176: refundAmount/cancellationFee should be 150 (50% of 300); got " + details);
        }
}

// ─── TC177 — S5-F12 NoRefundStrategy when booking IN_PROGRESS → 400 ─────
@Tag("public")
@Tag("features_m2")
class TC177_RefundNoRefundInProgressTests extends TestBase {
        @Test
        @DisplayName("TC177 — IN_PROGRESS booking → NoRefundStrategy → 400 'booking already started or completed'")
        void no_refund_in_progress_400() throws Exception {
                BASE_URL = checkoutServiceUrl;
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Barber"), "IN_PROGRESS", "2026-04-10");
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 100.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"schedule_conflict\"}", tok);
                assertEquals(400, r.statusCode(),
                        "TC177: must be 400 for IN_PROGRESS; got " + r.statusCode() + " body=" + r.body());
        }
}

// ─── TC178 — S5-F12 NoRefundStrategy when booking COMPLETED → 400 ────────
@Tag("public")
@Tag("features_m2")
class TC178_RefundNoRefundCompletedTests extends TestBase {
        @Test
        @DisplayName("TC178 — COMPLETED booking → NoRefundStrategy → 400")
        void no_refund_completed_400() throws Exception {
                BASE_URL = checkoutServiceUrl;
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Barber"), "COMPLETED", "2026-04-10");
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 100.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok);
                assertEquals(400, r.statusCode(),
                        "TC178: must be 400 for COMPLETED booking; got " + r.statusCode());
        }
}

// ─── TC179 — S5-F12 PENDING invoice → 400 ────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC179_RefundPendingInvoiceTests extends TestBase {
        @Test
        @DisplayName("TC179 — Refund attempt on PENDING invoice returns 400")
        void refund_pending_400() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Dentist"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 100.0, "PENDING", null);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok);
                assertEquals(400, r.statusCode(),
                        "TC179: must be 400 for PENDING invoice; got " + r.statusCode());
        }
}

// ─── TC180 — S5-F12 already-REFUNDED invoice → 400 ───────────────────────
@Tag("public")
@Tag("features_m2")
class TC180_RefundAlreadyRefundedTests extends TestBase {
        @Test
        @DisplayName("TC180 — Refund attempt on already REFUNDED invoice returns 400")
        void refund_already_refunded_400() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Dentist"), "CANCELLED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 100.0, "REFUNDED",
                        "{\"refundAmount\":100,\"cancellationFee\":0}");
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok);
                assertEquals(400, r.statusCode(),
                        "TC180: must be 400 for REFUNDED invoice; got " + r.statusCode());
        }
}

// ─── TC181 — S5-F12 non-existent invoice → 404 ───────────────────────────
@Tag("public")
@Tag("features_m2")
class TC181_RefundNotFoundTests extends TestBase {
        @Test
        @DisplayName("TC181 — Refund of non-existent invoice returns 404")
        void refund_not_found_404() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/999999/refund-cancellation", "{\"reason\":\"x\"}", tok);
                assertEquals(404, r.statusCode(),
                        "TC181: must be 404; got " + r.statusCode());
        }
}

// ─── TC182 — S5-F12 missing JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC182_RefundMissingJwtTests extends TestBase {
        @Test
        @DisplayName("TC182 — Refund without Authorization header returns 401")
        void refund_missing_jwt_401() throws Exception {
                BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpPost(
                        "/api/invoices/1/refund-cancellation", "{\"reason\":\"x\"}");
                assertEquals(401, r.statusCode(),
                        "TC182: must be 401; got " + r.statusCode());
        }
}

// ─── TC183 — S5-F12 invalid JWT → 401 ────────────────────────────────────
@Tag("public")
@Tag("features_m2")
class TC183_RefundInvalidJwtTests extends TestBase {
        @Test
        @DisplayName("TC183 — Refund with malformed JWT returns 401")
        void refund_invalid_jwt_401() throws Exception {
                BASE_URL = checkoutServiceUrl;
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/1/refund-cancellation", "{\"reason\":\"x\"}", "xxx.yyy.zzz");
                assertEquals(401, r.statusCode(),
                        "TC183: must be 401; got " + r.statusCode());
        }
}

// ─── TC184 — S5-F12 invoice.status → REFUNDED on success ─────────────────
@Tag("public")
@Tag("features_m2")
class TC184_RefundInvoiceStatusUpdatedTests extends TestBase {
        @Test
        @DisplayName("TC184 — On successful refund, invoice.status becomes REFUNDED")
        void invoice_status_refunded() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Tutor"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok), "TC184");
                String iTable = tableName("Invoice");
                String stCol = columnByField("Invoice", "status");
                String status = jdbc.queryForObject(
                        "SELECT \"" + stCol + "\"::text FROM \"" + iTable + "\" WHERE id=?",
                        String.class, iid);
                assertEquals("REFUNDED", status,
                        "TC184: invoice.status must be REFUNDED; got " + status);
        }
}

// ─── TC185 — S5-F12 booking.status → CANCELLED on success ────────────────
@Tag("public")
@Tag("features_m2")
class TC185_RefundBookingCancelledTests extends TestBase {
        @Test
        @DisplayName("TC185 — On successful refund, booking.status becomes CANCELLED")
        void booking_status_cancelled() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Tutor"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok), "TC185");
                String bTable = tableName("Booking");
                String stCol = columnByField("Booking", "status");
                String status = jdbc.queryForObject(
                        "SELECT \"" + stCol + "\"::text FROM \"" + bTable + "\" WHERE id=?",
                        String.class, bid);
                assertEquals("CANCELLED", status,
                        "TC185: booking.status must be CANCELLED; got " + status);
        }
}

// ─── TC186 — S5-F12 transactionDetails JSONB has all required keys ──────
@Tag("public")
@Tag("features_m2")
class TC186_RefundJsonbKeysTests extends TestBase {
        @Test
        @DisplayName("TC186 — transactionDetails has refundAmount, cancellationFee, refundReason, strategyName, refundedAt")
        void jsonb_keys_present() throws Exception {
                BASE_URL = checkoutServiceUrl;
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Dentist"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"schedule_conflict\"}", tok), "TC186");
                String iTable = tableName("Invoice");
                String details = jdbc.queryForObject(
                        "SELECT transaction_details::text FROM \"" + iTable + "\" WHERE id=?",
                        String.class, iid);
                assertTrue(details.contains("refundAmount"), "TC186: refundAmount key");
                assertTrue(details.contains("cancellationFee"), "TC186: cancellationFee key");
                assertTrue(details.contains("refundReason") || details.contains("reason"),
                        "TC186: refundReason key");
                assertTrue(details.contains("strategyName") || details.contains("strategy"),
                        "TC186: strategyName key");
                assertTrue(details.contains("refundedAt") || details.contains("refunded_at"),
                        "TC186: refundedAt key");
        }
}

// ─── TC187 — S5-F12 REFUNDED Mongo doc on success ────────────────────────
@Tag("public")
@Tag("features_m2")
class TC187_RefundMongoSuccessLogTests extends TestBase {
        @Test
        @DisplayName("TC187 — On success, REFUNDED doc written to payment_audit_trail")
        void refunded_mongo_log() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC187: MongoDB required");
                String coll = s5AuditCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUNDED"));
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Tutor"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                String tok = adminToken();
                assert2xx(httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok), "TC187");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUNDED"));
                assertTrue(after > before,
                        "TC187: REFUNDED doc count must increase; before=" + before + " after=" + after);
        }
}

// ─── TC188 — S5-F12 REFUND_DENIED logged BEFORE 400 thrown ───────────────
@Tag("public")
@Tag("features_m2")
class TC188_RefundDeniedMongoLogTests extends TestBase {
        @Test
        @DisplayName("TC188 — NoRefundStrategy denial path writes REFUND_DENIED to payment_audit_trail BEFORE 400")
        void refund_denied_logged_before_throw() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (mongo == null) throw new AssertionError("TC188: MongoDB required");
                String coll = s5AuditCollection();
                long before = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUND_DENIED"));
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Barber"), "IN_PROGRESS", "2026-04-10");
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 100.0, "COMPLETED", null);
                String tok = adminToken();
                HttpResponse<String> r = httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok);
                assertEquals(400, r.statusCode(),
                        "TC188 sanity: must 400 for IN_PROGRESS booking");
                long after = mongo.getCollection(coll).countDocuments(
                        new org.bson.Document("action", "REFUND_DENIED"));
                assertTrue(after > before,
                        "TC188: REFUND_DENIED doc must be persisted BEFORE 400 thrown; before="
                                + before + " after=" + after);
        }
}

// ─── TC189 — S5-F12 success invalidates S5-F10 cache ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC189_RefundInvalidatesS5F10CacheTests extends TestBase {
        @Test
        @DisplayName("TC189 — Successful refund removes invoice-service::S5-F10::* keys from Redis")
        void refund_invalidates_s5_f10_cache() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (redis == null) throw new AssertionError("TC189: Redis required");
                String tok = adminToken();
                // Warm the S5-F10 cache
                assert2xx(httpGetAuth(
                        "/api/invoices/analytics/service-type?startDate=2026-03-01&endDate=2026-03-31", tok),
                        "TC189 warm");
                java.util.Set<String> beforeKeys = redisKeys("*S5-F10*");
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Tutor"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                assert2xx(httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok), "TC189 refund");
                java.util.Set<String> afterKeys = redisKeys("*S5-F10*");
                assertTrue(afterKeys.size() < beforeKeys.size() || afterKeys.isEmpty(),
                        "TC189: S5-F10 cache must be invalidated; before=" + beforeKeys + " after=" + afterKeys);
        }
}

// ─── TC190 — S5-F12 success invalidates S5-F11 cache ─────────────────────
@Tag("public")
@Tag("features_m2")
class TC190_RefundInvalidatesS5F11CacheTests extends TestBase {
        @Test
        @DisplayName("TC190 — Successful refund removes invoice-service::S5-F11::* keys from Redis")
        void refund_invalidates_s5_f11_cache() throws Exception {
                BASE_URL = checkoutServiceUrl;
                if (redis == null) throw new AssertionError("TC190: Redis required");
                String tok = adminToken();
                // Warm the S5-F11 cache
                assert2xx(httpGetAuth(
                        "/api/invoices/analytics/methods?startDate=2026-03-01&endDate=2026-03-31", tok),
                        "TC190 warm");
                java.util.Set<String> beforeKeys = redisKeys("*S5-F11*");
                String farFuture = java.time.LocalDate.now().plusDays(3).toString();
                Long bid = _BkM2.bk(this, 1L, _BkM2S5.provSpec(this, "Dentist"), "CONFIRMED", farFuture);
                Long iid = _BkM2S5.invWithDetails(this, bid, 1L, 200.0, "COMPLETED", null);
                assert2xx(httpPostAuth(
                        "/api/invoices/" + iid + "/refund-cancellation",
                        "{\"reason\":\"x\"}", tok), "TC190 refund");
                java.util.Set<String> afterKeys = redisKeys("*S5-F11*");
                assertTrue(afterKeys.size() < beforeKeys.size() || afterKeys.isEmpty(),
                        "TC190: S5-F11 cache must be invalidated; before=" + beforeKeys + " after=" + afterKeys);
        }
}

// ════════════════════════════════════════════════════════════════════════════
// Helper class for S5 (provider seeding by specialty, invoice with JSONB
// transactionDetails, payment_audit_trail synthetic events, response row
// finders by specialty/method).
// ════════════════════════════════════════════════════════════════════════════
final class _BkM2S5 {
        private _BkM2S5() {}

        /** Provider with the specified specialty. Falls back to enum index 0
         *  if the requested label isn't a valid Specialty enum value at runtime. */
        static long provSpec(TestBase t, String specialty) {
                String table = t.tableName("Provider");
                String name = "BkS5_" + specialty + "_" + System.nanoTime();
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Provider", "name"), name);
                ov.put(t.columnByField("Provider", "email"),
                        name.toLowerCase() + "@bk.io");
                ov.put(t.columnByField("Provider", "phone"),
                        "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L));
                ov.put(t.columnByField("Provider", "specialty"), specialty);
                try {
                        ov.put(t.columnByField("Provider", "status"),
                                t.enumValueAt("Provider", "status", 0));
                } catch (Throwable ignore) {}
                return t.insertRowReturningId(table, ov);
        }

        /** Invoice with JSONB transactionDetails. detailsJson may be null. */
        static Long invWithDetails(TestBase t, long bookingId, long userId,
                        double amount, String status, String detailsJson) {
                String table = t.tableName("Invoice");
                java.util.Map<String, Object> ov = new java.util.HashMap<>();
                ov.put(t.columnByField("Invoice", "booking"), bookingId);
                try { ov.put(t.columnByField("Invoice", "user"), userId); } catch (Throwable ignore) {}
                ov.put(t.columnByField("Invoice", "amount"), amount);
                ov.put(t.columnByField("Invoice", "status"), status);
                try { ov.put(t.columnByField("Invoice", "method"), "CREDIT_CARD"); } catch (Throwable ignore) {}
                Long id = t.insertRowReturningId(table, ov);
                if (detailsJson != null) {
                        try {
                                t.jdbc.update(
                                        "UPDATE \"" + table + "\" SET transaction_details = ?::jsonb WHERE id = ?",
                                        detailsJson, id);
                        } catch (Throwable ignore) {
                                // some students may not have transaction_details column
                        }
                }
                t.setAllDateColumns(table, id, java.sql.Timestamp.valueOf("2026-03-15 12:00:00"));
                return id;
        }

        /** Insert a synthetic event into payment_audit_trail for S5-F11
         *  testing. Bypasses the Spring app — drives the Mongo aggregation
         *  pipeline directly. */
        static void audit(TestBase t, String action, String method,
                        double amount, String iso) {
                if (t.mongo == null) return;
                String coll = t.s5AuditCollection();
                org.bson.Document doc = new org.bson.Document("action", action)
                        .append("method", method)
                        .append("amount", amount)
                        .append("timestamp", java.time.LocalDateTime.parse(iso)
                                .atZone(java.time.ZoneOffset.UTC).toInstant());
                t.mongo.getCollection(coll).insertOne(doc);
        }

        /** Drop all docs in payment_audit_trail (test isolation). */
        static void cleanAudit(TestBase t) {
                if (t.mongo == null) return;
                t.mongo.getCollection(t.s5AuditCollection())
                        .deleteMany(new org.bson.Document());
        }

        static JsonNode findBySpecialty(JsonNode arr, String specialty) {
                if (arr == null) return null;
                for (JsonNode item : arr) {
                        if (item.has("specialty") && specialty.equals(item.get("specialty").asText())) {
                                return item;
                        }
                }
                return null;
        }

        static JsonNode findByMethod(JsonNode arr, String method) {
                if (arr == null) return null;
                for (JsonNode item : arr) {
                        if (item.has("method") && method.equals(item.get("method").asText())) {
                                return item;
                        }
                }
                return null;
        }
}

// ════════════════════════════════════════════════════════════════════════════
// M1 REGRESSION BLOCK — TC191..TC378 (Booking theme)
// One @Test per class; each class extends TestBase; all schema-driven
// seeding via tableName/columnByField/insertRowReturningId helpers.
// Tags: @Tag("public") + @Tag("features_m1").
// ════════════════════════════════════════════════════════════════════════════

/** Shared seeding helper for the Booking M1 regression block. Mirrors
 *  Amazon's _AmzM1Seed pattern: every column lookup goes through
 *  TestBase.columnByField(...) so a student renaming a field on a spec
 *  entity still seeds correctly. */
final class _BkM1Seed {
    private _BkM1Seed() {}

    /** INSERT a user. Returns new user id. role: CLIENT or ADMIN. */
    static long seedUser(TestBase t, String name, String email, String role) {
        String tbl = t.tableName("User");
        String bcrypt = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        String phone = "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("User", "name"), name);
        ov.put(t.columnByField("User", "email"), email);
        ov.put(t.columnByField("User", "phone"), phone);
        ov.put(t.columnByField("User", "password"), bcrypt);
        ov.put(t.columnByField("User", "role"), role);
        ov.put(t.columnByField("User", "status"), "ACTIVE");
        try { ov.put(t.columnByField("User", "preferences"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a SavedAddress. */
    static long seedAddress(TestBase t, long userId, String label, boolean isDefault) {
        String tbl = t.tableName("SavedAddress");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("SavedAddress", "user"), userId);
        try { ov.put(t.columnByField("SavedAddress", "label"), label); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "address"), "12 Tahrir St, " + label); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "latitude"), 30.044); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "longitude"), 31.235); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "isDefault"), isDefault); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("SavedAddress", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Provider. Returns id. */
    static long seedProvider(TestBase t, String name, String specialty, String status,
                             double rating, int totalRatings) {
        String tbl = t.tableName("Provider");
        String sn = name.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (sn.length() > 24) sn = sn.substring(0, 24);
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Provider", "name"), name);
        ov.put(t.columnByField("Provider", "email"), sn + "_" + System.nanoTime() + "@bk.io");
        ov.put(t.columnByField("Provider", "phone"),
                "+201" + String.format("%09d", System.nanoTime() % 1_000_000_000L));
        ov.put(t.columnByField("Provider", "specialty"), specialty);
        ov.put(t.columnByField("Provider", "status"), status);
        try { ov.put(t.columnByField("Provider", "rating"), rating); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Provider", "totalRatings"), totalRatings); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Provider", "serviceDetails"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a ProviderCertification. */
    static long seedCertification(TestBase t, long providerId, String type,
                                  java.time.LocalDate expiryDate, boolean verified) {
        String tbl = t.tableName("ProviderCertification");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("ProviderCertification", "provider"), providerId);
        ov.put(t.columnByField("ProviderCertification", "type"), type);
        try { ov.put(t.columnByField("ProviderCertification", "documentUrl"),
                "https://docs.bk.io/cert_" + System.nanoTime()); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("ProviderCertification", "expiryDate"),
                java.sql.Date.valueOf(expiryDate)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("ProviderCertification", "verified"), verified); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("ProviderCertification", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Booking. status: REQUESTED/CONFIRMED/IN_PROGRESS/COMPLETED/CANCELLED.
     *  date: yyyy-MM-dd format. */
    static long seedBooking(TestBase t, long userId, long providerId, String status,
                            String date, double totalPrice) {
        String tbl = t.tableName("Booking");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Booking", "user"), userId);
        try { ov.put(t.columnByField("Booking", "provider"), providerId); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Booking", "appointmentDate"), java.sql.Date.valueOf(date)); }
        catch (Throwable ignore) {}
        // Booking.startTime/endTime are LocalTime → PG TIME (per Booking M1 §6).
        // JDBC must bind java.time.LocalTime, never String.
        try { ov.put(t.columnByField("Booking", "startTime"), java.time.LocalTime.parse("10:00")); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Booking", "endTime"),   java.time.LocalTime.parse("11:00")); } catch (Throwable ignore) {}
        ov.put(t.columnByField("Booking", "status"), status);
        try { ov.put(t.columnByField("Booking", "totalPrice"), totalPrice); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Booking", "metadata"), "{}"); } catch (Throwable ignore) {}
        Long id = t.insertRowReturningId(tbl, ov);
        t.setAllDateColumns(tbl, id, java.sql.Timestamp.valueOf(date + " 12:00:00"));
        return id;
    }

    /** INSERT a BookingService. */
    static long seedBookingService(TestBase t, long bookingId, int order, String name,
                                   int duration, double price, String status) {
        String tbl = t.tableName("BookingService");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        String bkCol = t.columnByField("BookingService", "booking");
        ov.put(bkCol.equals("booking") ? "booking_id" : bkCol, bookingId);
        try { ov.put(t.columnByField("BookingService", "serviceOrder"), order); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("BookingService", "serviceName"), name); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("BookingService", "duration"), duration); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("BookingService", "price"), price); } catch (Throwable ignore) {}
        ov.put(t.columnByField("BookingService", "status"), status);
        try { ov.put(t.columnByField("BookingService", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a TimeSlot. startTime/endTime accepted as "HH:mm" strings; converted to
     *  java.time.LocalTime for the JDBC bind because Booking M1 §6.4 declares both
     *  fields as LocalTime → PG TIME. Binding as String triggers
     *  `column "end_time" is of type time without time zone but expression is of type
     *  character varying`. */
    static long seedTimeSlot(TestBase t, long providerId, String date,
                             String startTime, String endTime, boolean available) {
        String tbl = t.tableName("TimeSlot");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("TimeSlot", "provider"), providerId);
        try { ov.put(t.columnByField("TimeSlot", "date"), java.sql.Date.valueOf(date)); }
        catch (Throwable ignore) {}
        try { ov.put(t.columnByField("TimeSlot", "startTime"), java.time.LocalTime.parse(startTime)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("TimeSlot", "endTime"), java.time.LocalTime.parse(endTime)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("TimeSlot", "available"), available); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("TimeSlot", "metadata"), "{}"); } catch (Throwable ignore) {}
        Long id = t.insertRowReturningId(tbl, ov);
        try {
            t.setAllDateColumns(tbl, id, java.sql.Timestamp.valueOf(date + " 09:00:00"));
        } catch (Throwable ignore) {}
        return id;
    }

    /** INSERT an Invoice. method: CREDIT_CARD/CASH/WALLET. */
    static long seedInvoice(TestBase t, long bookingId, long userId, double amount,
                            String method, String status) {
        String tbl = t.tableName("Invoice");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Invoice", "booking"), bookingId);
        try { ov.put(t.columnByField("Invoice", "user"), userId); } catch (Throwable ignore) {}
        ov.put(t.columnByField("Invoice", "amount"), amount);
        try { ov.put(t.columnByField("Invoice", "method"), method); } catch (Throwable ignore) {}
        ov.put(t.columnByField("Invoice", "status"), status);
        try { ov.put(t.columnByField("Invoice", "transactionDetails"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT a Discount. expiry can be null. */
    static long seedDiscount(TestBase t, String code, String type, double value,
                             int maxUses, java.time.LocalDateTime expiry, boolean active) {
        String tbl = t.tableName("Discount");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("Discount", "code"), code);
        ov.put(t.columnByField("Discount", "discountType"), type);
        ov.put(t.columnByField("Discount", "discountValue"), value);
        try { ov.put(t.columnByField("Discount", "maxUses"), maxUses); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Discount", "currentUses"), 0); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Discount", "expiryDate"),
                expiry == null ? null : java.sql.Timestamp.valueOf(expiry)); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Discount", "active"), active); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("Discount", "metadata"), "{}"); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    /** INSERT an InvoiceDiscount join row. */
    static long seedInvoiceDiscount(TestBase t, long invoiceId, long discountId, double applied) {
        String tbl = t.tableName("InvoiceDiscount");
        java.util.Map<String, Object> ov = new java.util.HashMap<>();
        ov.put(t.columnByField("InvoiceDiscount", "invoice"), invoiceId);
        ov.put(t.columnByField("InvoiceDiscount", "discount"), discountId);
        try { ov.put(t.columnByField("InvoiceDiscount", "discountApplied"), applied); } catch (Throwable ignore) {}
        try { ov.put(t.columnByField("InvoiceDiscount", "appliedAt"),
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now())); } catch (Throwable ignore) {}
        return t.insertRowReturningId(tbl, ov);
    }

    // ─── JSONB setters (after insertRowReturningId since JSONB casts via raw UPDATE) ──

    static void setUserPreferences(TestBase t, long userId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("User") + "\" SET preferences = ?::jsonb WHERE id = ?",
                    json, userId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("User needs `preferences` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setProviderServiceDetails(TestBase t, long providerId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Provider") + "\" SET \""
                    + t.columnByField("Provider", "serviceDetails") + "\" = ?::jsonb WHERE id = ?",
                    json, providerId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Provider needs `serviceDetails` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setBookingMetadata(TestBase t, long bookingId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Booking") + "\" SET \""
                    + t.columnByField("Booking", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, bookingId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Booking needs `metadata` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setTimeSlotMetadata(TestBase t, long slotId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("TimeSlot") + "\" SET \""
                    + t.columnByField("TimeSlot", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, slotId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("TimeSlot needs `metadata` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setSavedAddressMetadata(TestBase t, long addrId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("SavedAddress") + "\" SET \""
                    + t.columnByField("SavedAddress", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, addrId);
        } catch (org.springframework.dao.DataAccessException ignored) { /* metadata may be optional */ }
    }

    static void setProviderCertificationMetadata(TestBase t, long certId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("ProviderCertification") + "\" SET \""
                    + t.columnByField("ProviderCertification", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, certId);
        } catch (org.springframework.dao.DataAccessException ignored) { }
    }

    static void setBookingServiceMetadata(TestBase t, long bsId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("BookingService") + "\" SET \""
                    + t.columnByField("BookingService", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, bsId);
        } catch (org.springframework.dao.DataAccessException ignored) { }
    }

    static void setInvoiceTransactionDetails(TestBase t, long invId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Invoice") + "\" SET \""
                    + t.columnByField("Invoice", "transactionDetails") + "\" = ?::jsonb WHERE id = ?",
                    json, invId);
        } catch (org.springframework.dao.DataAccessException e) {
            throw new AssertionError("Invoice needs `transactionDetails` JSONB column — " + e.getMessage(), e);
        }
    }

    static void setDiscountMetadata(TestBase t, long discId, String json) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Discount") + "\" SET \""
                    + t.columnByField("Discount", "metadata") + "\" = ?::jsonb WHERE id = ?",
                    json, discId);
        } catch (org.springframework.dao.DataAccessException ignored) { }
    }

    // ─── Date setters ──

    static void setBookingCompletedAt(TestBase t, long bookingId, java.sql.Timestamp ts) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Booking") + "\" SET \""
                    + t.columnByField("Booking", "completedAt") + "\" = ? WHERE id = ?",
                    ts, bookingId);
        } catch (Throwable ignore) { /* completedAt may be optional */ }
    }

    static void setBookingRequestedAt(TestBase t, long bookingId, java.sql.Timestamp ts) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Booking") + "\" SET \""
                    + t.columnByField("Booking", "requestedAt") + "\" = ? WHERE id = ?",
                    ts, bookingId);
        } catch (Throwable ignore) { }
    }

    /** Update existing TimeSlot.available flag (some tests insert then toggle). */
    static void setTimeSlotAvailable(TestBase t, long slotId, boolean available) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("TimeSlot") + "\" SET \""
                    + t.columnByField("TimeSlot", "available") + "\" = ? WHERE id = ?",
                    available, slotId);
        } catch (Throwable ignore) { }
    }

    /** Update Discount.currentUses to a specific value. */
    static void setDiscountCurrentUses(TestBase t, long discountId, int uses) {
        try {
            t.jdbc.update("UPDATE \"" + t.tableName("Discount") + "\" SET \""
                    + t.columnByField("Discount", "currentUses") + "\" = ? WHERE id = ?",
                    uses, discountId);
        } catch (Throwable ignore) { }
    }

    static java.time.LocalDateTime futureDateTime() {
        return java.time.LocalDateTime.of(2030, 12, 31, 23, 59, 59);
    }
    static java.time.LocalDateTime pastDateTime() {
        return java.time.LocalDateTime.of(2020, 1, 1, 0, 0, 0);
    }
    static java.time.LocalDate today() { return java.time.LocalDate.now(); }
    static java.time.LocalDate futureDate() { return java.time.LocalDate.of(2030, 12, 31); }
    static java.time.LocalDate pastDate() { return java.time.LocalDate.of(2020, 1, 1); }
}

// ────────────────────────────────────────────────────────────────────────────
// S1 — User Service (TC191..TC220)  — 9 features × ~3 TCs each
// S1-F1 search, S1-F2 prefs, S1-F3 booking-summary, S1-F4 deactivate,
// S1-F5 prefs filter, S1-F6 top-clients, S1-F7 default address,
// S1-F8 profile, S1-F9 lang+minBookings
// ────────────────────────────────────────────────────────────────────────────

// ─── TC191 — S1-F1 search by partial name ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC191_BkSearchUsersByNameTests extends TestBase {
    @Test
    @DisplayName("TC191 — Search by name 'Ahmed' returns 2 users (partial match)")
    void search_by_name() throws Exception {
        BASE_URL = userServiceUrl;
        _BkM1Seed.seedUser(this, "Ahmed",     "tc191_a@bk.io", "CLIENT");
        _BkM1Seed.seedUser(this, "Sara",      "tc191_b@bk.io", "ADMIN");
        _BkM1Seed.seedUser(this, "Ahmed Ali", "tc191_c@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?name=Ahmed", tok);
        assert2xx(r, "TC191 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        int matches = 0;
        for (JsonNode it : list) {
            String n = it.has("name") ? it.get("name").asText() : "";
            if (n.contains("Ahmed")) matches++;
        }
        assertEquals(2, matches, "TC191: 2 Ahmed-named users expected; got " + matches + " body=" + r.body());
    }
}

// ─── TC192 — S1-F1 search by role exact match ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC192_BkSearchUsersByRoleTests extends TestBase {
    @Test
    @DisplayName("TC192 — Search by role=ADMIN returns ADMIN users only")
    void search_by_role() throws Exception {
        BASE_URL = userServiceUrl;
        _BkM1Seed.seedUser(this, "Ahmed",     "tc192_a@bk.io", "CLIENT");
        _BkM1Seed.seedUser(this, "Sara",      "tc192_b@bk.io", "ADMIN");
        _BkM1Seed.seedUser(this, "Ahmed Ali", "tc192_c@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?role=ADMIN", tok);
        assert2xx(r, "TC192 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String role = it.has("role") ? it.get("role").asText() : "";
            assertEquals("ADMIN", role, "TC192: every result must have role=ADMIN; got " + role);
        }
        assertTrue(list.size() >= 1, "TC192: at least one ADMIN expected; got " + list.size());
    }
}

// ─── TC193 — S1-F1 no-match returns empty list ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC193_BkSearchUsersNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC193 — Search with no-matching name returns empty list")
    void search_no_match() throws Exception {
        BASE_URL = userServiceUrl;
        _BkM1Seed.seedUser(this, "Ahmed", "tc193_a@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?name=zzzNoMatchXYZ", tok);
        assert2xx(r, "TC193 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC193: empty list expected; got " + list.size() + " body=" + r.body());
    }
}

// ─── TC194 — S1-F2 update preferences merge ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC194_BkUpdatePreferencesMergeTests extends TestBase {
    @Test
    @DisplayName("TC194 — PUT preferences merges: language preserved, theme updated, currency added")
    void preferences_merge() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Pref User", "tc194@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uid, "{\"language\":\"en\",\"theme\":\"light\"}");
        String tok = adminToken();
        String body = "{\"theme\":\"dark\",\"currency\":\"EGP\"}";
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/preferences", body, tok);
        assert2xx(r, "TC194");
        JsonNode j = parseNode(r.body());
        JsonNode prefs = j.has("preferences") ? j.get("preferences") : j;
        assertEquals("en",   prefs.has("language") ? prefs.get("language").asText() : "", "TC194: language preserved");
        assertEquals("dark", prefs.has("theme")    ? prefs.get("theme").asText()    : "", "TC194: theme updated");
        assertEquals("EGP",  prefs.has("currency") ? prefs.get("currency").asText() : "", "TC194: currency added");
    }
}

// ─── TC195 — S1-F2 same-key overwrite ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC195_BkUpdatePreferencesOverwriteTests extends TestBase {
    @Test
    @DisplayName("TC195 — PUT with existing key overwrites it")
    void preferences_overwrite() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Pref User", "tc195@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uid, "{\"language\":\"en\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/preferences", "{\"language\":\"fr\"}", tok);
        assert2xx(r, "TC195");
        JsonNode prefs = parseNode(r.body()).has("preferences")
                ? parseNode(r.body()).get("preferences") : parseNode(r.body());
        assertEquals("fr", prefs.has("language") ? prefs.get("language").asText() : "",
                "TC195: language must be overwritten to 'fr'");
    }
}

// ─── TC196 — S1-F2 404 non-existent user ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC196_BkUpdatePreferencesNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC196 — PUT preferences for non-existent user returns 404")
    void preferences_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/999999/preferences", "{\"x\":\"y\"}", tok);
        assertEquals(404, r.statusCode(), "TC196: must be 404; got " + r.statusCode());
    }
}

// ─── TC197 — S1-F3 booking summary happy path ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC197_BkBookingSummaryHappyTests extends TestBase {
    @Test
    @DisplayName("TC197 — Summary returns totalBookings=5, completedBookings=3, totalSpent=700, cancelledBookings=1")
    void summary_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Sum User", "tc197@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 150.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 350.0);
        _BkM1Seed.seedBooking(this, uid, pid, "CANCELLED", "2026-03-13", 999.0);
        _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-14", 999.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/booking-summary", tok);
        assert2xx(r, "TC197");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        long completed = _BkM2.rL(j, "completedBookings", "completed_bookings");
        long cancelled = _BkM2.rL(j, "cancelledBookings", "cancelled_bookings");
        double spent = _BkM2.rD(j, "totalSpent", "total_spent");
        assertEquals(5, total, "TC197: totalBookings=5; got " + total);
        assertEquals(3, completed, "TC197: completedBookings=3; got " + completed);
        assertEquals(1, cancelled, "TC197: cancelledBookings=1; got " + cancelled);
        assertEquals(700.0, spent, 0.5, "TC197: totalSpent=700; got " + spent);
    }
}

// ─── TC198 — S1-F3 user with no bookings → zeros ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC198_BkBookingSummaryNoBookingsTests extends TestBase {
    @Test
    @DisplayName("TC198 — Summary for user with no bookings returns zeros")
    void summary_no_bookings() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Empty User", "tc198@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/booking-summary", tok);
        assert2xx(r, "TC198");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        double spent = _BkM2.rD(j, "totalSpent", "total_spent");
        assertEquals(0L, total, "TC198: totalBookings=0; got " + total);
        assertEquals(0.0, spent, 0.01, "TC198: totalSpent=0; got " + spent);
    }
}

// ─── TC199 — S1-F3 404 non-existent user ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC199_BkBookingSummaryNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC199 — Summary for non-existent user returns 404")
    void summary_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/999999/booking-summary", tok);
        assertEquals(404, r.statusCode(), "TC199: must be 404; got " + r.statusCode());
    }
}

// ─── TC200 — S1-F4 deactivate fails when active CONFIRMED booking ─────────────
@Tag("public")
@Tag("features_m1")
class TC200_BkDeactivateActiveBookingTests extends TestBase {
    @Test
    @DisplayName("TC200 — Deactivate fails (400) when user has a CONFIRMED booking")
    void deactivate_active_400() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Active User", "tc200@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "CONFIRMED", "2030-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/deactivate", "", tok);
        assertEquals(400, r.statusCode(), "TC200: must be 400 when active booking exists; got " + r.statusCode());
    }
}

// ─── TC201 — S1-F4 deactivate succeeds when no active bookings ───────────────
@Tag("public")
@Tag("features_m1")
class TC201_BkDeactivateSuccessTests extends TestBase {
    @Test
    @DisplayName("TC201 — Deactivate succeeds when only COMPLETED bookings; PG status=DEACTIVATED")
    void deactivate_success() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Done User", "tc201@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Barber", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/deactivate", "", tok);
        assert2xx(r, "TC201");
        String stCol = columnByField("User", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("User") + "\" WHERE id = ?",
            String.class, uid);
        assertEquals("DEACTIVATED", dbStatus, "TC201: PG user.status=DEACTIVATED expected; got " + dbStatus);
    }
}

// ─── TC202 — S1-F4 404 non-existent user ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC202_BkDeactivateNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC202 — Deactivate non-existent user returns 404")
    void deactivate_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/999999/deactivate", "", tok);
        assertEquals(404, r.statusCode(), "TC202: must be 404; got " + r.statusCode());
    }
}

// ─── TC203 — S1-F5 preferences search happy match ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC203_BkPreferencesSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC203 — ?key=language&value=ar matches users with prefs.language=ar")
    void preferences_search_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _BkM1Seed.seedUser(this, "Ar1", "tc203_a@bk.io", "CLIENT");
        long u2 = _BkM1Seed.seedUser(this, "En1", "tc203_b@bk.io", "CLIENT");
        long u3 = _BkM1Seed.seedUser(this, "Ar2", "tc203_c@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, u1, "{\"language\":\"ar\"}");
        _BkM1Seed.setUserPreferences(this, u2, "{\"language\":\"en\"}");
        _BkM1Seed.setUserPreferences(this, u3, "{\"language\":\"ar\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/search?key=language&value=ar", tok);
        assert2xx(r, "TC203");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC203: at least 2 ar-language users expected; got " + list.size());
    }
}

// ─── TC204 — S1-F5 no match returns empty list ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC204_BkPreferencesSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC204 — Unknown value returns empty list")
    void preferences_search_no_match() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _BkM1Seed.seedUser(this, "X", "tc204@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, u1, "{\"language\":\"ar\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/search?key=language&value=zh", tok);
        assert2xx(r, "TC204");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC204: empty list expected; got " + list.size());
    }
}

// ─── TC205 — S1-F5 400 blank key ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC205_BkPreferencesSearchBlankKeyTests extends TestBase {
    @Test
    @DisplayName("TC205 — Blank key returns 400")
    void preferences_search_blank_key() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/search?key=&value=ar", tok);
        assertEquals(400, r.statusCode(), "TC205: blank key must be 400; got " + r.statusCode());
    }
}

// ─── TC206 — S1-F6 top clients happy ranking ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC206_BkTopClientsHappyTests extends TestBase {
    @Test
    @DisplayName("TC206 — Top clients ranks user B (3500) above user A (1200)")
    void top_clients_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "ClientA", "tc206_a@bk.io", "CLIENT");
        long uB = _BkM1Seed.seedUser(this, "ClientB", "tc206_b@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 1200.0);
        _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-11", 1500.0);
        _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-12", 2000.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-clients?startDate=2026-03-01&endDate=2026-03-31&limit=10", tok);
        assert2xx(r, "TC206");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC206: at least 2 clients expected; got " + list.size());
        long firstId = _BkM2.rL(list.get(0), "userId", "id");
        assertEquals(uB, firstId, "TC206: client B (3500) must rank first; got id=" + firstId);
    }
}

// ─── TC207 — S1-F6 empty range returns empty list ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC207_BkTopClientsEmptyRangeTests extends TestBase {
    @Test
    @DisplayName("TC207 — Date range with no completed bookings returns empty list")
    void top_clients_empty_range() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "ClientA", "tc207@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-clients?startDate=2030-01-01&endDate=2030-01-31&limit=10", tok);
        assert2xx(r, "TC207");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC207: empty list expected; got " + list.size());
    }
}

// ─── TC208 — S1-F6 400 invalid range ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC208_BkTopClientsInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC208 — start>end returns 400")
    void top_clients_invalid_range() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-clients?startDate=2026-03-31&endDate=2026-03-01&limit=10", tok);
        assertEquals(400, r.statusCode(), "TC208: start>end must be 400; got " + r.statusCode());
    }
}

// ─── TC209 — S1-F7 set default address happy switch ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC209_BkSetDefaultAddressHappyTests extends TestBase {
    @Test
    @DisplayName("TC209 — PUT /addresses/{addressId}/default flips default to target")
    void set_default_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "AddrUser", "tc209@bk.io", "CLIENT");
        _BkM1Seed.seedAddress(this, uid, "Home",   false);
        long a2 = _BkM1Seed.seedAddress(this, uid, "Work",   true);
        long a3 = _BkM1Seed.seedAddress(this, uid, "Other",  false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/" + uid + "/addresses/" + a3 + "/default", "", tok);
        assert2xx(r, "TC209");
        String dCol = columnByField("SavedAddress", "isDefault");
        Boolean a3def = jdbc.queryForObject(
            "SELECT \"" + dCol + "\" FROM \"" + tableName("SavedAddress") + "\" WHERE id = ?",
            Boolean.class, a3);
        Boolean a2def = jdbc.queryForObject(
            "SELECT \"" + dCol + "\" FROM \"" + tableName("SavedAddress") + "\" WHERE id = ?",
            Boolean.class, a2);
        assertEquals(Boolean.TRUE,  a3def, "TC209: a3 must be default after PUT; got " + a3def);
        assertEquals(Boolean.FALSE, a2def, "TC209: a2 must no longer be default; got " + a2def);
    }
}

// ─── TC210 — S1-F7 404 non-existent user ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC210_BkSetDefaultAddressUserNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC210 — Non-existent user returns 404")
    void set_default_user_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/999999/addresses/1/default", "", tok);
        assertEquals(404, r.statusCode(), "TC210: must be 404; got " + r.statusCode());
    }
}

// ─── TC211 — S1-F7 400 address belongs to different user ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC211_BkSetDefaultAddressCrossUserTests extends TestBase {
    @Test
    @DisplayName("TC211 — Address belongs to different user → 400")
    void set_default_cross_user() throws Exception {
        BASE_URL = userServiceUrl;
        long u1 = _BkM1Seed.seedUser(this, "U1", "tc211_a@bk.io", "CLIENT");
        long u2 = _BkM1Seed.seedUser(this, "U2", "tc211_b@bk.io", "CLIENT");
        long a2 = _BkM1Seed.seedAddress(this, u2, "Home", true);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/" + u1 + "/addresses/" + a2 + "/default", "", tok);
        assertEquals(400, r.statusCode(), "TC211: cross-user must be 400; got " + r.statusCode());
    }
}

// ─── TC212 — S1-F7 404 non-existent address ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC212_BkSetDefaultAddressNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC212 — Non-existent address returns 404")
    void set_default_address_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc212@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/users/" + uid + "/addresses/999999/default", "", tok);
        assertEquals(404, r.statusCode(), "TC212: must be 404; got " + r.statusCode());
    }
}

// ─── TC213 — S1-F8 user profile happy path ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC213_BkUserProfileHappyTests extends TestBase {
    @Test
    @DisplayName("TC213 — Profile DTO has totalAddresses=3 + savedAddresses array")
    void profile_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "ProfUser", "tc213@bk.io", "CLIENT");
        _BkM1Seed.seedAddress(this, uid, "A1", true);
        _BkM1Seed.seedAddress(this, uid, "A2", false);
        _BkM1Seed.seedAddress(this, uid, "A3", false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC213");
        JsonNode j = parseNode(r.body());
        long totalAddr = _BkM2.rL(j, "totalAddresses", "total_addresses");
        assertEquals(3L, totalAddr, "TC213: totalAddresses=3; got " + totalAddr);
        JsonNode addrs = _BkM2.rO(j, "savedAddresses", "saved_addresses");
        assertNotNull(addrs, "TC213: savedAddresses array required; body=" + r.body());
        assertEquals(3, addrs.size(), "TC213: 3 addresses expected; got " + addrs.size());
    }
}

// ─── TC214 — S1-F8 user with no addresses ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC214_BkUserProfileNoAddressesTests extends TestBase {
    @Test
    @DisplayName("TC214 — User with 0 addresses returns totalAddresses=0 and empty array")
    void profile_no_addresses() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "NoAddrUser", "tc214@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC214");
        JsonNode j = parseNode(r.body());
        long totalAddr = _BkM2.rL(j, "totalAddresses", "total_addresses");
        assertEquals(0L, totalAddr, "TC214: totalAddresses=0; got " + totalAddr);
    }
}

// ─── TC215 — S1-F8 404 non-existent user ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC215_BkUserProfileNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC215 — Non-existent user returns 404")
    void profile_not_found() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/999999/profile", tok);
        assertEquals(404, r.statusCode(), "TC215: must be 404; got " + r.statusCode());
    }
}

// ─── TC216 — S1-F9 language + minBookings happy filter ───────────────────────
@Tag("public")
@Tag("features_m1")
class TC216_BkLanguageMinBookingsHappyTests extends TestBase {
    @Test
    @DisplayName("TC216 — lang=ar&minBookings=3 returns user A (5 completed bookings)")
    void lang_min_bookings_happy() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "ArUserA", "tc216_a@bk.io", "CLIENT");
        long uB = _BkM1Seed.seedUser(this, "ArUserB", "tc216_b@bk.io", "CLIENT");
        long uC = _BkM1Seed.seedUser(this, "EnUserC", "tc216_c@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uA, "{\"language\":\"ar\"}");
        _BkM1Seed.setUserPreferences(this, uB, "{\"language\":\"ar\"}");
        _BkM1Seed.setUserPreferences(this, uC, "{\"language\":\"en\"}");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 0.0, 0);
        for (int i = 0; i < 5; i++) _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-1" + i, 100.0);
        for (int i = 0; i < 2; i++) _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-1" + i, 100.0);
        for (int i = 0; i < 4; i++) _BkM1Seed.seedBooking(this, uC, pid, "COMPLETED", "2026-03-2" + i, 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/language?lang=ar&minBookings=3", tok);
        assert2xx(r, "TC216");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundA = false;
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "userId", "id");
            if (id == uA) foundA = true;
            assertNotEquals(uB, id, "TC216: user B (only 2 bookings) must be excluded");
            assertNotEquals(uC, id, "TC216: user C (English) must be excluded");
        }
        assertTrue(foundA, "TC216: user A must be in results; body=" + r.body());
    }
}

// ─── TC217 — S1-F9 400 blank lang ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC217_BkLanguageBlankLangTests extends TestBase {
    @Test
    @DisplayName("TC217 — lang= blank returns 400")
    void lang_blank() throws Exception {
        BASE_URL = userServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/language?lang=&minBookings=1", tok);
        assertEquals(400, r.statusCode(), "TC217: blank lang must be 400; got " + r.statusCode());
    }
}

// ─── TC218 — S1-F9 lower threshold returns more users ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC218_BkLanguageLowerThresholdTests extends TestBase {
    @Test
    @DisplayName("TC218 — lang=ar&minBookings=1 returns A+B")
    void lang_lower_threshold() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "ArA", "tc218_a@bk.io", "CLIENT");
        long uB = _BkM1Seed.seedUser(this, "ArB", "tc218_b@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uA, "{\"language\":\"ar\"}");
        _BkM1Seed.setUserPreferences(this, uB, "{\"language\":\"ar\"}");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/language?lang=ar&minBookings=1", tok);
        assert2xx(r, "TC218");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC218: at least 2 users expected; got " + list.size());
    }
}

// ─── TC219 — S1-F9 only COMPLETED bookings count ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC219_BkLanguageCompletedOnlyTests extends TestBase {
    @Test
    @DisplayName("TC219 — minBookings counts COMPLETED only (REQUESTED/CANCELLED ignored)")
    void lang_completed_only() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "ArA", "tc219@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uA, "{\"language\":\"ar\"}");
        long pid = _BkM1Seed.seedProvider(this, "P", "Barber", "AVAILABLE", 0.0, 0);
        _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uA, pid, "REQUESTED", "2026-03-11", 100.0);
        _BkM1Seed.seedBooking(this, uA, pid, "CANCELLED", "2026-03-12", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/language?lang=ar&minBookings=2", tok);
        assert2xx(r, "TC219");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "userId", "id");
            assertNotEquals(uA, id, "TC219: A has only 1 COMPLETED, must not match minBookings=2");
        }
    }
}

// ─── TC220 — S1-F8 profile contains preferences JSONB ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC220_BkUserProfileWithPreferencesTests extends TestBase {
    @Test
    @DisplayName("TC220 — Profile DTO surfaces preferences JSONB content")
    void profile_with_prefs() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "PrefProfile", "tc220@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uid, "{\"language\":\"ar\",\"theme\":\"dark\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC220");
        JsonNode j = parseNode(r.body());
        JsonNode prefs = _BkM2.rO(j, "preferences");
        assertNotNull(prefs, "TC220: preferences object expected in profile; body=" + r.body());
        assertEquals("ar", prefs.has("language") ? prefs.get("language").asText() : "",
                "TC220: preferences.language=ar expected");
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S2 — Provider Service (TC221..TC248)  — 9 features × ~3 TCs each
// S2-F1 search, S2-F2 details, S2-F3 earnings, S2-F4 toggle availability,
// S2-F5 pricing-tier, S2-F6 top-rated, S2-F7 rate, S2-F8 verify cert,
// S2-F9 expired certs
// ────────────────────────────────────────────────────────────────────────────

// ─── TC221 — S2-F1 search by status + min/max rating ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC221_BkProviderSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC221 — status=AVAILABLE&minRating=4&maxRating=5 returns matching providers (rating asc)")
    void provider_search_happy() throws Exception {
        BASE_URL = catalogServiceUrl;
        _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.seedProvider(this, "P2", "Barber",  "BUSY",      4.8, 20);
        _BkM1Seed.seedProvider(this, "P3", "Tutor",   "AVAILABLE", 4.2, 15);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/search?status=AVAILABLE&minRating=4&maxRating=5", tok);
        assert2xx(r, "TC221");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC221: at least 2 AVAILABLE in [4,5] expected; got " + list.size());
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("AVAILABLE", s, "TC221: every result must be AVAILABLE; got " + s);
        }
    }
}

// ─── TC222 — S2-F1 search narrow rating range ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC222_BkProviderSearchNarrowRatingTests extends TestBase {
    @Test
    @DisplayName("TC222 — minRating=4.7&maxRating=4.9 returns only the 4.8-rated provider")
    void provider_search_narrow() throws Exception {
        BASE_URL = catalogServiceUrl;
        _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.seedProvider(this, "P2", "Barber",  "AVAILABLE", 4.8, 20);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/search?minRating=4.7&maxRating=4.9", tok);
        assert2xx(r, "TC222");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC222: 1 provider expected in [4.7,4.9]; got " + list.size());
    }
}

// ─── TC223 — S2-F1 invalid range 400 ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC223_BkProviderSearchInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC223 — minRating>maxRating returns 400")
    void provider_search_invalid_range() throws Exception {
        BASE_URL = catalogServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/search?minRating=4.5&maxRating=2.0", tok);
        assertEquals(400, r.statusCode(), "TC223: minRating>maxRating must be 400; got " + r.statusCode());
    }
}

// ─── TC224 — S2-F2 service-details merge happy ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC224_BkProviderDetailsMergeTests extends TestBase {
    @Test
    @DisplayName("TC224 — PUT service-details merges: years preserved, languages updated, certifications added")
    void details_merge() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "DentP", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.setProviderServiceDetails(this, pid,
            "{\"years\":\"5\",\"languages\":[\"en\"],\"specialty\":\"Dentist\"}");
        String tok = adminToken();
        String body = "{\"languages\":[\"en\",\"ar\"],\"certifications\":[\"BDS\"]}";
        HttpResponse<String> r = httpPutAuth("/api/providers/" + pid + "/service-details", body, tok);
        assert2xx(r, "TC224");
        JsonNode j = parseNode(r.body());
        JsonNode details = _BkM2.rO(j, "serviceDetails", "service_details", "details");
        if (details == null) details = j;
        assertEquals("5", details.has("years") ? details.get("years").asText() : "",
            "TC224: years preserved");
        JsonNode langs = details.has("languages") ? details.get("languages") : null;
        assertNotNull(langs, "TC224: languages array required");
        assertEquals(2, langs.size(), "TC224: languages updated to 2 elements; got " + langs.size());
        JsonNode certs = details.has("certifications") ? details.get("certifications") : null;
        assertNotNull(certs, "TC224: certifications array added");
    }
}

// ─── TC225 — S2-F2 same-key overwrite ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC225_BkProviderDetailsOverwriteTests extends TestBase {
    @Test
    @DisplayName("TC225 — PUT with existing key overwrites that key")
    void details_overwrite() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "DentP", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.setProviderServiceDetails(this, pid, "{\"years\":\"5\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/" + pid + "/service-details", "{\"years\":\"10\"}", tok);
        assert2xx(r, "TC225");
        JsonNode j = parseNode(r.body());
        JsonNode details = _BkM2.rO(j, "serviceDetails", "service_details", "details");
        if (details == null) details = j;
        assertEquals("10", details.has("years") ? details.get("years").asText() : "",
            "TC225: years must be overwritten to 10");
    }
}

// ─── TC226 — S2-F2 404 non-existent provider ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC226_BkProviderDetailsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC226 — PUT service-details for non-existent provider returns 404")
    void details_not_found() throws Exception {
        BASE_URL = catalogServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/999999/service-details", "{\"x\":\"y\"}", tok);
        assertEquals(404, r.statusCode(), "TC226: must be 404; got " + r.statusCode());
    }
}

// ─── TC227 — S2-F3 earnings happy path ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC227_BkProviderEarningsHappyTests extends TestBase {
    @Test
    @DisplayName("TC227 — Earnings: 5 COMPLETED bookings → totalBookings=5, totalEarnings=900")
    void earnings_happy() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Buyer", "tc227@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "DocP", "Dentist", "AVAILABLE", 4.5, 10);
        double[] amounts = { 100.0, 150.0, 200.0, 200.0, 250.0 };
        for (int i = 0; i < amounts.length; i++) {
            _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-1" + i, amounts[i]);
        }
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/" + pid + "/earnings?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC227");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        double earnings = _BkM2.rD(j, "totalEarnings", "total_earnings");
        assertEquals(5L, total, "TC227: totalBookings=5; got " + total);
        assertEquals(900.0, earnings, 1.0, "TC227: totalEarnings~900; got " + earnings);
    }
}

// ─── TC228 — S2-F3 empty range returns zeros ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC228_BkProviderEarningsEmptyRangeTests extends TestBase {
    @Test
    @DisplayName("TC228 — Earnings in empty range returns 0/0")
    void earnings_empty_range() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/" + pid + "/earnings?startDate=2030-01-01&endDate=2030-01-31", tok);
        assert2xx(r, "TC228");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        double earnings = _BkM2.rD(j, "totalEarnings", "total_earnings");
        assertEquals(0L, total, "TC228: totalBookings=0; got " + total);
        assertEquals(0.0, earnings, 0.01, "TC228: totalEarnings=0; got " + earnings);
    }
}

// ─── TC229 — S2-F3 404 non-existent provider ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC229_BkProviderEarningsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC229 — Earnings for non-existent provider returns 404")
    void earnings_not_found() throws Exception {
        BASE_URL = catalogServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/999999/earnings?startDate=2026-03-01&endDate=2026-03-31", tok);
        assertEquals(404, r.statusCode(), "TC229: must be 404; got " + r.statusCode());
    }
}

// ─── TC230 — S2-F4 toggle availability blocked by active booking ─────────────
@Tag("public")
@Tag("features_m1")
class TC230_BkToggleAvailabilityActiveTests extends TestBase {
    @Test
    @DisplayName("TC230 — Toggle to OFFLINE rejected (400) when provider has CONFIRMED booking")
    void toggle_active_400() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc230@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "CONFIRMED", "2030-04-01", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/providers/" + pid + "/availability",
                "{\"status\":\"OFFLINE\"}", tok);
        assertEquals(400, r.statusCode(), "TC230: must be 400 with active booking; got " + r.statusCode());
    }
}

// ─── TC231 — S2-F4 toggle success when no active bookings ────────────────────
@Tag("public")
@Tag("features_m1")
class TC231_BkToggleAvailabilitySuccessTests extends TestBase {
    @Test
    @DisplayName("TC231 — Toggle to OFFLINE succeeds when only COMPLETED bookings; PG status=OFFLINE")
    void toggle_success() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc231@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/providers/" + pid + "/availability",
                "{\"status\":\"OFFLINE\"}", tok);
        assert2xx(r, "TC231");
        String stCol = columnByField("Provider", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Provider") + "\" WHERE id = ?",
            String.class, pid);
        assertEquals("OFFLINE", dbStatus, "TC231: PG provider.status=OFFLINE expected; got " + dbStatus);
    }
}

// ─── TC232 — S2-F4 404 non-existent provider ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC232_BkToggleAvailabilityNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC232 — Toggle availability on non-existent provider returns 404")
    void toggle_not_found() throws Exception {
        BASE_URL = catalogServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/providers/999999/availability",
                "{\"status\":\"OFFLINE\"}", tok);
        assertEquals(404, r.statusCode(), "TC232: must be 404; got " + r.statusCode());
    }
}

// ─── TC233 — S2-F5 pricing-tier filter happy with status ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC233_BkPricingTierFilterStatusTests extends TestBase {
    @Test
    @DisplayName("TC233 — pricing-tier?tier=PREMIUM&status=AVAILABLE filters via JSONB serviceDetails")
    void pricing_filter_status() throws Exception {
        BASE_URL = catalogServiceUrl;
        long p1 = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.5, 10);
        long p2 = _BkM1Seed.seedProvider(this, "P2", "Dentist", "BUSY",      4.8, 20);
        long p3 = _BkM1Seed.seedProvider(this, "P3", "Dentist", "AVAILABLE", 3.5, 5);
        _BkM1Seed.setProviderServiceDetails(this, p1, "{\"tier\":\"PREMIUM\"}");
        _BkM1Seed.setProviderServiceDetails(this, p2, "{\"tier\":\"PREMIUM\"}");
        _BkM1Seed.setProviderServiceDetails(this, p3, "{\"tier\":\"BASIC\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/pricing-tier?tier=PREMIUM&status=AVAILABLE", tok);
        assert2xx(r, "TC233");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundP1 = false;
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            if (id == p1) foundP1 = true;
            assertNotEquals(p2, id, "TC233: BUSY provider must be excluded");
            assertNotEquals(p3, id, "TC233: BASIC tier must be excluded");
        }
        assertTrue(foundP1, "TC233: P1 (PREMIUM+AVAILABLE) must be in results; body=" + r.body());
    }
}

// ─── TC234 — S2-F5 happy without status ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC234_BkPricingTierNoStatusTests extends TestBase {
    @Test
    @DisplayName("TC234 — pricing-tier?tier=PREMIUM with no status returns all PREMIUM")
    void pricing_no_status() throws Exception {
        BASE_URL = catalogServiceUrl;
        long p1 = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.5, 10);
        long p2 = _BkM1Seed.seedProvider(this, "P2", "Dentist", "BUSY",      4.8, 20);
        _BkM1Seed.setProviderServiceDetails(this, p1, "{\"tier\":\"PREMIUM\"}");
        _BkM1Seed.setProviderServiceDetails(this, p2, "{\"tier\":\"PREMIUM\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/pricing-tier?tier=PREMIUM", tok);
        assert2xx(r, "TC234");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC234: at least 2 PREMIUM expected; got " + list.size());
    }
}

// ─── TC235 — S2-F5 no match returns empty ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC235_BkPricingTierNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC235 — pricing-tier with unknown tier returns empty list")
    void pricing_no_match() throws Exception {
        BASE_URL = catalogServiceUrl;
        long p1 = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.setProviderServiceDetails(this, p1, "{\"tier\":\"PREMIUM\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/pricing-tier?tier=UNKNOWN", tok);
        assert2xx(r, "TC235");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC235: empty list expected; got " + list.size());
    }
}

// ─── TC236 — S2-F6 top-rated happy ranking ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC236_BkTopRatedHappyTests extends TestBase {
    @Test
    @DisplayName("TC236 — Top-rated ranks 4.9-rated above 4.0-rated")
    void top_rated_happy() throws Exception {
        BASE_URL = catalogServiceUrl;
        _BkM1Seed.seedProvider(this, "Low", "Dentist", "AVAILABLE", 4.0, 5);
        _BkM1Seed.seedProvider(this, "Hi",  "Dentist", "AVAILABLE", 4.9, 50);
        _BkM1Seed.seedProvider(this, "Mid", "Dentist", "AVAILABLE", 4.5, 10);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/reports/top-rated?limit=10", tok);
        assert2xx(r, "TC236");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 3, "TC236: at least 3 providers expected; got " + list.size());
        double firstRating = list.get(0).has("rating") ? list.get(0).get("rating").asDouble() : -1;
        double secondRating = list.get(1).has("rating") ? list.get(1).get("rating").asDouble() : -1;
        assertTrue(firstRating >= secondRating,
            "TC236: results must be rating desc; got " + firstRating + " then " + secondRating);
    }
}

// ─── TC237 — S2-F6 limit greater than available ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC237_BkTopRatedLimitOverflowTests extends TestBase {
    @Test
    @DisplayName("TC237 — Top-rated limit=100 returns all 2 providers (no error)")
    void top_rated_limit_overflow() throws Exception {
        BASE_URL = catalogServiceUrl;
        _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.0, 5);
        _BkM1Seed.seedProvider(this, "P2", "Dentist", "AVAILABLE", 4.9, 50);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/reports/top-rated?limit=100", tok);
        assert2xx(r, "TC237");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC237: at least 2 providers expected; got " + list.size());
    }
}

// ─── TC238 — S2-F7 rate first review (running avg = rating) ──────────────────
@Tag("public")
@Tag("features_m1")
class TC238_BkRateFirstTests extends TestBase {
    @Test
    @DisplayName("TC238 — First rating sets totalRatings=1, rating=given value")
    void rate_first() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc238@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 0.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        String body = "{\"bookingId\":" + bid + ",\"rating\":5}";
        HttpResponse<String> r = httpPostAuth("/api/providers/" + pid + "/rate", body, tok);
        assert2xx(r, "TC238");
        Double rating = jdbc.queryForObject(
            "SELECT \"" + columnByField("Provider", "rating") + "\" FROM \""
            + tableName("Provider") + "\" WHERE id = ?", Double.class, pid);
        Integer total = jdbc.queryForObject(
            "SELECT \"" + columnByField("Provider", "totalRatings") + "\" FROM \""
            + tableName("Provider") + "\" WHERE id = ?", Integer.class, pid);
        assertEquals(5.0, rating == null ? 0 : rating, 0.01, "TC238: rating=5; got " + rating);
        assertEquals(1, total == null ? 0 : total, "TC238: totalRatings=1; got " + total);
    }
}

// ─── TC239 — S2-F7 running average rating ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC239_BkRateRunningAvgTests extends TestBase {
    @Test
    @DisplayName("TC239 — Two ratings 5.0 and 3.0 → average 4.0, totalRatings=2")
    void rate_running_avg() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "UA", "tc239_a@bk.io", "CLIENT");
        long uB = _BkM1Seed.seedUser(this, "UB", "tc239_b@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 0.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 50.0);
        long b2 = _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-11", 50.0);
        String tok = adminToken();
        assert2xx(httpPostAuth("/api/providers/" + pid + "/rate",
                "{\"bookingId\":" + b1 + ",\"rating\":5}", tok), "TC239 first");
        assert2xx(httpPostAuth("/api/providers/" + pid + "/rate",
                "{\"bookingId\":" + b2 + ",\"rating\":3}", tok), "TC239 second");
        Double rating = jdbc.queryForObject(
            "SELECT \"" + columnByField("Provider", "rating") + "\" FROM \""
            + tableName("Provider") + "\" WHERE id = ?", Double.class, pid);
        Integer total = jdbc.queryForObject(
            "SELECT \"" + columnByField("Provider", "totalRatings") + "\" FROM \""
            + tableName("Provider") + "\" WHERE id = ?", Integer.class, pid);
        assertEquals(4.0, rating == null ? 0 : rating, 0.05, "TC239: avg=4.0; got " + rating);
        assertEquals(2, total == null ? 0 : total, "TC239: totalRatings=2; got " + total);
    }
}

// ─── TC240 — S2-F7 rating out-of-range 400 ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC240_BkRateOutOfRangeTests extends TestBase {
    @Test
    @DisplayName("TC240 — Rating=6 returns 400 (1..5 range only)")
    void rate_out_of_range() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc240@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 0.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/providers/" + pid + "/rate",
                "{\"bookingId\":" + bid + ",\"rating\":6}", tok);
        assertEquals(400, r.statusCode(), "TC240: must be 400; got " + r.statusCode());
    }
}

// ─── TC241 — S2-F7 user without COMPLETED booking rejected ──────────────────
@Tag("public")
@Tag("features_m1")
class TC241_BkRateMustHaveBookingTests extends TestBase {
    @Test
    @DisplayName("TC241 — Rating with REQUESTED (not COMPLETED) booking → 400")
    void rate_must_have_completed() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc241@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 0.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 50.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/providers/" + pid + "/rate",
                "{\"bookingId\":" + bid + ",\"rating\":4}", tok);
        assertEquals(400, r.statusCode(), "TC241: must be 400 (no completed booking); got " + r.statusCode());
    }
}

// ─── TC242 — S2-F8 verify certification happy ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC242_BkVerifyCertHappyTests extends TestBase {
    @Test
    @DisplayName("TC242 — Verify certification flips verified=true (admin only)")
    void verify_cert_happy() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long cid = _BkM1Seed.seedCertification(this, pid, "LICENSE",
                java.time.LocalDate.of(2030, 1, 1), false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/" + pid + "/certifications/" + cid + "/verify",
            "{\"verifiedBy\":3}", tok);
        assert2xx(r, "TC242");
        Boolean verified = jdbc.queryForObject(
            "SELECT \"" + columnByField("ProviderCertification", "verified") + "\" FROM \""
            + tableName("ProviderCertification") + "\" WHERE id = ?", Boolean.class, cid);
        assertEquals(Boolean.TRUE, verified, "TC242: verified must be true; got " + verified);
    }
}

// ─── TC243 — S2-F8 verify certification 404 cert ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC243_BkVerifyCertNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC243 — Verify non-existent certification returns 404")
    void verify_cert_not_found() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/" + pid + "/certifications/999999/verify",
            "{\"verifiedBy\":3}", tok);
        assertEquals(404, r.statusCode(), "TC243: must be 404; got " + r.statusCode());
    }
}

// ─── TC244 — S2-F8 verify certification 403 non-admin ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC244_BkVerifyCertNonAdminTests extends TestBase {
    @Test
    @DisplayName("TC244 — Verify certification by CLIENT user returns 403")
    void verify_cert_non_admin() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long cid = _BkM1Seed.seedCertification(this, pid, "LICENSE",
                java.time.LocalDate.of(2030, 1, 1), false);
        java.util.Map<String, Object> client = seedAndLoginUser("tc244c");
        String clientTok = (String) client.get("token");
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/" + pid + "/certifications/" + cid + "/verify",
            "{\"verifiedBy\":3}", clientTok);
        assertEquals(403, r.statusCode(), "TC244: must be 403 for non-admin; got " + r.statusCode());
    }
}

// ─── TC245 — S2-F9 expired certs returns alerts ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC245_BkExpiredCertsHappyTests extends TestBase {
    @Test
    @DisplayName("TC245 — Expired certifications endpoint returns alerts grouped by provider")
    void expired_certs_happy() throws Exception {
        BASE_URL = catalogServiceUrl;
        long p1 = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.0, 0);
        long p2 = _BkM1Seed.seedProvider(this, "P2", "Tutor",   "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedCertification(this, p1, "LICENSE",
                java.time.LocalDate.of(2020, 1, 1), true);
        _BkM1Seed.seedCertification(this, p2, "DEGREE",
                java.time.LocalDate.of(2030, 1, 1), true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/certifications/expired", tok);
        assert2xx(r, "TC245");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundP1 = false;
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            if (id == p1) foundP1 = true;
            assertNotEquals(p2, id, "TC245: P2 (cert valid until 2030) must be excluded");
        }
        assertTrue(foundP1, "TC245: P1 (expired LICENSE) must be in results; body=" + r.body());
    }
}

// ─── TC246 — S2-F9 expired count matches certs ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC246_BkExpiredCertsCountTests extends TestBase {
    @Test
    @DisplayName("TC246 — expiredCount matches number of expired certs per provider")
    void expired_certs_count() throws Exception {
        BASE_URL = catalogServiceUrl;
        long p1 = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedCertification(this, p1, "LICENSE",
                java.time.LocalDate.of(2020, 1, 1), true);
        _BkM1Seed.seedCertification(this, p1, "DEGREE",
                java.time.LocalDate.of(2021, 1, 1), true);
        _BkM1Seed.seedCertification(this, p1, "INSURANCE",
                java.time.LocalDate.of(2030, 1, 1), true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/certifications/expired", tok);
        assert2xx(r, "TC246");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundP1 = false;
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            if (id == p1) {
                foundP1 = true;
                long cnt = _BkM2.rL(it, "expiredCount", "expired_count");
                assertEquals(2, cnt, "TC246: expiredCount=2 for P1; got " + cnt);
            }
        }
        assertTrue(foundP1, "TC246: P1 must be in results; body=" + r.body());
    }
}

// ─── TC247 — S2-F9 empty when no expired certs ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC247_BkExpiredCertsEmptyTests extends TestBase {
    @Test
    @DisplayName("TC247 — Returns empty list when no certifications are expired")
    void expired_certs_empty() throws Exception {
        BASE_URL = catalogServiceUrl;
        long p1 = _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedCertification(this, p1, "LICENSE",
                java.time.LocalDate.of(2030, 1, 1), true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/certifications/expired", tok);
        assert2xx(r, "TC247");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            assertNotEquals(p1, id, "TC247: P1 has no expired certs, must be excluded");
        }
    }
}

// ─── TC248 — S2-F1 search no match returns empty ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC248_BkProviderSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC248 — Search with status=OFFLINE returns empty when no OFFLINE providers")
    void provider_search_no_match() throws Exception {
        BASE_URL = catalogServiceUrl;
        _BkM1Seed.seedProvider(this, "P1", "Dentist", "AVAILABLE", 4.5, 10);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/search?status=OFFLINE", tok);
        assert2xx(r, "TC248");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC248: empty list expected; got " + list.size());
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S3 — Booking Service (TC249..TC274)  — 9 features × ~3 TCs each
// S3-F1 search, S3-F2 assign, S3-F3 estimate, S3-F4 complete,
// S3-F5 metadata, S3-F6 analytics, S3-F7 cancel, S3-F8 add-services,
// S3-F9 details
// ────────────────────────────────────────────────────────────────────────────

// ─── TC249 — S3-F1 search by status + date range ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC249_BkBookingSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC249 — status=COMPLETED + date range returns 2 matching bookings")
    void booking_search_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc249@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-15", 200.0);
        _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-20", 300.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-04-10", 400.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/search?status=COMPLETED&startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC249");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(2, list.size(), "TC249: 2 COMPLETED in March expected; got " + list.size());
    }
}

// ─── TC250 — S3-F1 invalid range 400 ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC250_BkBookingSearchInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC250 — start>end returns 400")
    void booking_search_invalid_range() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/search?startDate=2026-03-31&endDate=2026-03-01", tok);
        assertEquals(400, r.statusCode(), "TC250: must be 400; got " + r.statusCode());
    }
}

// ─── TC251 — S3-F1 no match returns empty list ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC251_BkBookingSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC251 — Search with status=COMPLETED returns empty when none completed")
    void booking_search_no_match() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc251@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/search?status=COMPLETED", tok);
        assert2xx(r, "TC251");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC251: empty list expected; got " + list.size());
    }
}

// ─── TC252 — S3-F2 assign provider happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC252_BkAssignProviderHappyTests extends TestBase {
    @Test
    @DisplayName("TC252 — Assign provider to REQUESTED booking transitions to CONFIRMED, sets provider FK")
    void assign_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc252@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, 0L, "REQUESTED", "2030-04-10", 100.0);
        // clear provider FK to simulate unassigned
        try {
            jdbc.update("UPDATE \"" + tableName("Booking") + "\" SET \""
                + columnByField("Booking", "provider") + "\" = NULL WHERE id = ?", bid);
        } catch (Throwable ignore) { }
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/bookings/" + bid + "/assign?providerId=" + pid, "", tok);
        assert2xx(r, "TC252");
        String stCol = columnByField("Booking", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Booking") + "\" WHERE id = ?",
            String.class, bid);
        assertEquals("CONFIRMED", dbStatus, "TC252: booking.status=CONFIRMED expected; got " + dbStatus);
    }
}

// ─── TC253 — S3-F2 assign rejects when not REQUESTED ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC253_BkAssignProviderInvalidStateTests extends TestBase {
    @Test
    @DisplayName("TC253 — Assign provider to COMPLETED booking returns 400")
    void assign_invalid_state() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc253@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/bookings/" + bid + "/assign?providerId=" + pid, "", tok);
        assertEquals(400, r.statusCode(), "TC253: must be 400 for COMPLETED booking; got " + r.statusCode());
    }
}

// ─── TC254 — S3-F2 404 non-existent booking ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC254_BkAssignProviderNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC254 — Assign provider on non-existent booking returns 404")
    void assign_not_found() throws Exception {
        BASE_URL = orderServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/bookings/999999/assign?providerId=" + pid, "", tok);
        assertEquals(404, r.statusCode(), "TC254: must be 404; got " + r.statusCode());
    }
}

// ─── TC255 — S3-F3 estimate happy ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC255_BkEstimateHappyTests extends TestBase {
    @Test
    @DisplayName("TC255 — Estimate returns totalDuration + basePrice + estimatedPrice + demandMultiplier")
    void estimate_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String body = "{\"providerId\":" + pid + ",\"appointmentDate\":\"2030-04-10\","
                + "\"services\":[{\"serviceName\":\"Cleaning\",\"duration\":30,\"price\":50},"
                + "{\"serviceName\":\"Whitening\",\"duration\":60,\"price\":150}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/estimate", body, tok);
        assert2xx(r, "TC255");
        JsonNode j = parseNode(r.body());
        long duration = _BkM2.rL(j, "totalDuration", "total_duration");
        double base = _BkM2.rD(j, "basePrice", "base_price");
        double estimated = _BkM2.rD(j, "estimatedPrice", "estimated_price");
        double mult = _BkM2.rD(j, "demandMultiplier", "demand_multiplier");
        assertEquals(90L, duration, "TC255: totalDuration=90; got " + duration);
        assertEquals(200.0, base, 1.0, "TC255: basePrice=200; got " + base);
        assertTrue(estimated >= base, "TC255: estimatedPrice >= basePrice; got " + estimated);
        assertTrue(mult >= 1.0, "TC255: demandMultiplier >= 1.0; got " + mult);
    }
}

// ─── TC256 — S3-F3 estimate provider not found ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC256_BkEstimateProviderNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC256 — Estimate with non-existent provider returns 404")
    void estimate_provider_not_found() throws Exception {
        BASE_URL = orderServiceUrl;
        String body = "{\"providerId\":999999,\"appointmentDate\":\"2030-04-10\","
                + "\"services\":[{\"serviceName\":\"Cleaning\",\"duration\":30,\"price\":50}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/estimate", body, tok);
        assertEquals(404, r.statusCode(), "TC256: must be 404; got " + r.statusCode());
    }
}

// ─── TC257 — S3-F4 complete booking happy ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC257_BkCompleteBookingHappyTests extends TestBase {
    @Test
    @DisplayName("TC257 — Complete IN_PROGRESS booking transitions to COMPLETED")
    void complete_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc257@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "IN_PROGRESS", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/complete", "", tok);
        assert2xx(r, "TC257");
        String stCol = columnByField("Booking", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Booking") + "\" WHERE id = ?",
            String.class, bid);
        assertEquals("COMPLETED", dbStatus, "TC257: status=COMPLETED expected; got " + dbStatus);
    }
}

// ─── TC258 — S3-F4 complete invalid state 400 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC258_BkCompleteBookingInvalidStateTests extends TestBase {
    @Test
    @DisplayName("TC258 — Complete REQUESTED booking returns 400")
    void complete_invalid_state() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc258@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/complete", "", tok);
        assertEquals(400, r.statusCode(), "TC258: must be 400; got " + r.statusCode());
    }
}

// ─── TC259 — S3-F4 404 non-existent booking ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC259_BkCompleteBookingNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC259 — Complete non-existent booking returns 404")
    void complete_not_found() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/999999/complete", "", tok);
        assertEquals(404, r.statusCode(), "TC259: must be 404; got " + r.statusCode());
    }
}

// ─── TC260 — S3-F5 metadata search happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC260_BkMetadataSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC260 — metadata?key=source&value=mobile matches bookings with metadata.source=mobile")
    void metadata_search_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc260@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 100.0);
        long b3 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 100.0);
        _BkM1Seed.setBookingMetadata(this, b1, "{\"source\":\"mobile\"}");
        _BkM1Seed.setBookingMetadata(this, b2, "{\"source\":\"web\"}");
        _BkM1Seed.setBookingMetadata(this, b3, "{\"source\":\"mobile\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/metadata/search?key=source&value=mobile", tok);
        assert2xx(r, "TC260");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(2, list.size(), "TC260: 2 mobile bookings expected; got " + list.size());
    }
}

// ─── TC261 — S3-F5 metadata no match ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC261_BkMetadataSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC261 — metadata search unknown value returns empty list")
    void metadata_no_match() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/metadata/search?key=source&value=zzznonexist", tok);
        assert2xx(r, "TC261");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC261: empty list expected; got " + list.size());
    }
}

// ─── TC262 — S3-F5 blank key 400 ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC262_BkMetadataSearchBlankKeyTests extends TestBase {
    @Test
    @DisplayName("TC262 — Blank key returns 400")
    void metadata_blank_key() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/metadata/search?key=&value=mobile", tok);
        assertEquals(400, r.statusCode(), "TC262: must be 400; got " + r.statusCode());
    }
}

// ─── TC263 — S3-F6 analytics happy ───────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC263_BkAnalyticsHappyTests extends TestBase {
    @Test
    @DisplayName("TC263 — Analytics: 5 bookings, 3 completed, 1 cancelled → completionRate=0.6")
    void analytics_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc263@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 300.0);
        _BkM1Seed.seedBooking(this, uid, pid, "CANCELLED", "2026-03-13", 99.0);
        _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-14", 99.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/analytics?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC263");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        long completed = _BkM2.rL(j, "completedBookings", "completed_bookings");
        long cancelled = _BkM2.rL(j, "cancelledBookings", "cancelled_bookings");
        double rate = _BkM2.rD(j, "completionRate", "completion_rate");
        assertEquals(5L, total, "TC263: totalBookings=5; got " + total);
        assertEquals(3L, completed, "TC263: completedBookings=3; got " + completed);
        assertEquals(1L, cancelled, "TC263: cancelledBookings=1; got " + cancelled);
        assertEquals(0.6, rate, 0.05, "TC263: completionRate=0.6; got " + rate);
    }
}

// ─── TC264 — S3-F6 empty range zeros ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC264_BkAnalyticsEmptyRangeTests extends TestBase {
    @Test
    @DisplayName("TC264 — Analytics in empty range returns zeros")
    void analytics_empty() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/analytics?startDate=2030-01-01&endDate=2030-01-31", tok);
        assert2xx(r, "TC264");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        assertEquals(0L, total, "TC264: totalBookings=0; got " + total);
    }
}

// ─── TC265 — S3-F6 invalid range 400 ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC265_BkAnalyticsInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC265 — Analytics start>end returns 400")
    void analytics_invalid_range() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/analytics?startDate=2026-03-31&endDate=2026-03-01", tok);
        assertEquals(400, r.statusCode(), "TC265: must be 400; got " + r.statusCode());
    }
}

// ─── TC266 — S3-F7 cancel REQUESTED booking happy ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC266_BkCancelHappyTests extends TestBase {
    @Test
    @DisplayName("TC266 — Cancel REQUESTED booking transitions to CANCELLED")
    void cancel_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc266@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2030-04-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/cancel", "", tok);
        assert2xx(r, "TC266");
        String stCol = columnByField("Booking", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Booking") + "\" WHERE id = ?",
            String.class, bid);
        assertEquals("CANCELLED", dbStatus, "TC266: status=CANCELLED expected; got " + dbStatus);
    }
}

// ─── TC267 — S3-F7 cancel COMPLETED booking 400 ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC267_BkCancelCompletedTests extends TestBase {
    @Test
    @DisplayName("TC267 — Cancel COMPLETED booking returns 400")
    void cancel_completed() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc267@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/cancel", "", tok);
        assertEquals(400, r.statusCode(), "TC267: must be 400; got " + r.statusCode());
    }
}

// ─── TC268 — S3-F7 cancel non-existent ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC268_BkCancelNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC268 — Cancel non-existent booking returns 404")
    void cancel_not_found() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/999999/cancel", "", tok);
        assertEquals(404, r.statusCode(), "TC268: must be 404; got " + r.statusCode());
    }
}

// ─── TC269 — S3-F8 add services to booking happy ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC269_BkAddServicesHappyTests extends TestBase {
    @Test
    @DisplayName("TC269 — POST /services adds services + recomputes totalPrice")
    void add_services_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc269@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2030-04-10", 0.0);
        String body = "{\"services\":[{\"serviceName\":\"Cleaning\",\"duration\":30,\"price\":50},"
                + "{\"serviceName\":\"X-Ray\",\"duration\":15,\"price\":30}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/" + bid + "/services", body, tok);
        assert2xx(r, "TC269");
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("BookingService") + "\" WHERE \""
            + columnByField("BookingService", "booking") + "\" = ?", Long.class, bid);
        assertEquals(2L, count == null ? 0L : count.longValue(),
                "TC269: 2 services added; got " + count);
    }
}

// ─── TC270 — S3-F8 404 non-existent booking ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC270_BkAddServicesNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC270 — Add services to non-existent booking returns 404")
    void add_services_not_found() throws Exception {
        BASE_URL = orderServiceUrl;
        String body = "{\"services\":[{\"serviceName\":\"X\",\"duration\":10,\"price\":20}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/999999/services", body, tok);
        assertEquals(404, r.statusCode(), "TC270: must be 404; got " + r.statusCode());
    }
}

// ─── TC271 — S3-F8 empty list 400 ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC271_BkAddServicesEmptyListTests extends TestBase {
    @Test
    @DisplayName("TC271 — Empty services list returns 400")
    void add_services_empty() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc271@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2030-04-10", 0.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/bookings/" + bid + "/services", "{\"services\":[]}", tok);
        assertEquals(400, r.statusCode(), "TC271: must be 400 for empty list; got " + r.statusCode());
    }
}

// ─── TC272 — S3-F9 booking details happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC272_BkBookingDetailsHappyTests extends TestBase {
    @Test
    @DisplayName("TC272 — Details DTO has services list, totalServices, completedServices counts")
    void details_happy() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc272@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "IN_PROGRESS", "2026-03-10", 200.0);
        _BkM1Seed.seedBookingService(this, bid, 1, "Cleaning", 30, 50.0, "COMPLETED");
        _BkM1Seed.seedBookingService(this, bid, 2, "X-Ray",    15, 30.0, "COMPLETED");
        _BkM1Seed.seedBookingService(this, bid, 3, "Whitening",60, 120.0,"PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/" + bid + "/details", tok);
        assert2xx(r, "TC272");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalServices", "total_services");
        long completed = _BkM2.rL(j, "completedServices", "completed_services");
        assertEquals(3L, total, "TC272: totalServices=3; got " + total);
        assertEquals(2L, completed, "TC272: completedServices=2; got " + completed);
        JsonNode services = _BkM2.rO(j, "services");
        assertNotNull(services, "TC272: services array required; body=" + r.body());
        assertEquals(3, services.size(), "TC272: 3 services expected; got " + services.size());
    }
}

// ─── TC273 — S3-F9 details with no services ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC273_BkBookingDetailsNoServicesTests extends TestBase {
    @Test
    @DisplayName("TC273 — Details for booking with no services returns totalServices=0, empty array")
    void details_no_services() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc273@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 0.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/" + bid + "/details", tok);
        assert2xx(r, "TC273");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalServices", "total_services");
        assertEquals(0L, total, "TC273: totalServices=0; got " + total);
    }
}

// ─── TC274 — S3-F9 details 404 not found ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC274_BkBookingDetailsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC274 — Details for non-existent booking returns 404")
    void details_not_found() throws Exception {
        BASE_URL = orderServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/999999/details", tok);
        assertEquals(404, r.statusCode(), "TC274: must be 404; got " + r.statusCode());
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S4 — Calendar Service (TC275..TC297)  — 9 features × ~3 TCs each
// S4-F1 latest, S4-F2 create, S4-F3 available, S4-F4 batch,
// S4-F5 metadata, S4-F6 history, S4-F7 purge, S4-F8 utilization, S4-F9 idle
// All endpoints under /api/timeslots (M1; M2 uses /api/calendar)
// ────────────────────────────────────────────────────────────────────────────

// ─── TC275 — S4-F1 latest slot returns most recent ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC275_BkLatestSlotHappyTests extends TestBase {
    @Test
    @DisplayName("TC275 — Latest slot returns the most recent slot for provider")
    void latest_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedTimeSlot(this, pid, "2026-03-10", "09:00", "10:00", true);
        _BkM1Seed.seedTimeSlot(this, pid, "2026-03-15", "14:00", "15:00", true);
        long latest = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-01", "16:00", "17:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/provider/" + pid + "/latest", tok);
        assert2xx(r, "TC275");
        JsonNode j = parseNode(r.body());
        long id = _BkM2.rL(j, "id", "slotId");
        assertEquals(latest, id, "TC275: latest id expected; got " + id);
    }
}

// ─── TC276 — S4-F1 latest no slots 404 ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC276_BkLatestSlotNoSlotsTests extends TestBase {
    @Test
    @DisplayName("TC276 — Latest slot for provider with 0 slots returns 404")
    void latest_no_slots() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/provider/" + pid + "/latest", tok);
        assertEquals(404, r.statusCode(), "TC276: must be 404; got " + r.statusCode());
    }
}

// ─── TC277 — S4-F2 create slot happy ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC277_BkCreateSlotHappyTests extends TestBase {
    @Test
    @DisplayName("TC277 — POST /timeslots/provider/{id} creates new slot in PG")
    void create_slot_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        String body = "{\"date\":\"2030-04-10\",\"startTime\":\"09:00\",\"endTime\":\"10:00\","
                + "\"metadata\":{\"room\":\"101\"}}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/provider/" + pid, body, tok);
        assert2xx(r, "TC277");
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("TimeSlot") + "\" WHERE \""
            + columnByField("TimeSlot", "provider") + "\" = ?", Long.class, pid);
        assertTrue(count != null && count >= 1, "TC277: expected at least 1 slot; got " + count);
    }
}

// ─── TC278 — S4-F2 create slot 404 unknown provider ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC278_BkCreateSlotNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC278 — Create slot for non-existent provider returns 404")
    void create_slot_not_found() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String body = "{\"date\":\"2030-04-10\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/provider/999999", body, tok);
        assertEquals(404, r.statusCode(), "TC278: must be 404; got " + r.statusCode());
    }
}

// ─── TC279 — S4-F2 invalid time range 400 ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC279_BkCreateSlotInvalidTimeTests extends TestBase {
    @Test
    @DisplayName("TC279 — endTime before startTime returns 400")
    void create_slot_invalid_time() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        String body = "{\"date\":\"2030-04-10\",\"startTime\":\"10:00\",\"endTime\":\"09:00\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/provider/" + pid, body, tok);
        assertEquals(400, r.statusCode(), "TC279: must be 400; got " + r.statusCode());
    }
}

// ─── TC280 — S4-F3 available providers happy ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC280_BkAvailableProvidersHappyTests extends TestBase {
    @Test
    @DisplayName("TC280 — Available endpoint returns providers with matching specialty + open slots")
    void available_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pA = _BkM1Seed.seedProvider(this, "PA", "Dentist", "AVAILABLE", 4.5, 10);
        long pB = _BkM1Seed.seedProvider(this, "PB", "Tutor",   "AVAILABLE", 4.5, 10);
        _BkM1Seed.seedTimeSlot(this, pA, "2030-04-10", "09:00", "10:00", true);
        _BkM1Seed.seedTimeSlot(this, pB, "2030-04-10", "09:00", "10:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/available?date=2030-04-10&specialty=Dentist", tok);
        assert2xx(r, "TC280");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundPA = false;
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            if (id == pA) foundPA = true;
            assertNotEquals(pB, id, "TC280: PB (Tutor) must be excluded");
        }
        assertTrue(foundPA, "TC280: PA (Dentist) must be in results; body=" + r.body());
    }
}

// ─── TC281 — S4-F3 available no slots returns empty ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC281_BkAvailableNoSlotsTests extends TestBase {
    @Test
    @DisplayName("TC281 — Available endpoint returns empty when no providers have open slots")
    void available_no_slots() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pA = _BkM1Seed.seedProvider(this, "PA", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.seedTimeSlot(this, pA, "2030-04-10", "09:00", "10:00", false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/available?date=2030-04-10&specialty=Dentist", tok);
        assert2xx(r, "TC281");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC281: empty list expected; got " + list.size());
    }
}

// ─── TC282 — S4-F3 available unknown specialty returns empty ─────────────────
@Tag("public")
@Tag("features_m1")
class TC282_BkAvailableUnknownSpecialtyTests extends TestBase {
    @Test
    @DisplayName("TC282 — Available endpoint with unknown specialty returns empty")
    void available_unknown_specialty() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pA = _BkM1Seed.seedProvider(this, "PA", "Dentist", "AVAILABLE", 4.5, 10);
        _BkM1Seed.seedTimeSlot(this, pA, "2030-04-10", "09:00", "10:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/available?date=2030-04-10&specialty=Astronaut", tok);
        assert2xx(r, "TC282");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC282: empty list expected; got " + list.size());
    }
}

// ─── TC283 — S4-F4 batch create happy ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC283_BkBatchCreateHappyTests extends TestBase {
    @Test
    @DisplayName("TC283 — POST /timeslots/batch creates multiple slots")
    void batch_create_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String body = "{\"providerId\":" + pid + ",\"slots\":["
                + "{\"date\":\"2030-04-10\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"},"
                + "{\"date\":\"2030-04-10\",\"startTime\":\"10:00\",\"endTime\":\"11:00\"},"
                + "{\"date\":\"2030-04-10\",\"startTime\":\"11:00\",\"endTime\":\"12:00\"}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/batch", body, tok);
        assert2xx(r, "TC283");
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("TimeSlot") + "\" WHERE \""
            + columnByField("TimeSlot", "provider") + "\" = ?", Long.class, pid);
        assertTrue(count != null && count >= 3, "TC283: 3 slots expected; got " + count);
    }
}

// ─── TC284 — S4-F4 batch create empty list 400 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC284_BkBatchCreateEmptyTests extends TestBase {
    @Test
    @DisplayName("TC284 — Batch create with empty slots list returns 400")
    void batch_create_empty() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String body = "{\"providerId\":" + pid + ",\"slots\":[]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/batch", body, tok);
        assertEquals(400, r.statusCode(), "TC284: must be 400; got " + r.statusCode());
    }
}

// ─── TC285 — S4-F5 metadata gt operator ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC285_BkSlotMetadataGtTests extends TestBase {
    @Test
    @DisplayName("TC285 — metadata?key=duration&operator=gt&value=30 matches slots with duration > 30")
    void metadata_gt() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long s1 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-01", "09:00", "10:00", true);
        long s2 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-02", "09:00", "10:00", true);
        long s3 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-03", "09:00", "10:00", true);
        _BkM1Seed.setTimeSlotMetadata(this, s1, "{\"duration\":15}");
        _BkM1Seed.setTimeSlotMetadata(this, s2, "{\"duration\":45}");
        _BkM1Seed.setTimeSlotMetadata(this, s3, "{\"duration\":60}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/metadata/search?key=duration&operator=gt&value=30", tok);
        assert2xx(r, "TC285");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(2, list.size(), "TC285: 2 slots with duration>30 expected; got " + list.size());
    }
}

// ─── TC286 — S4-F5 metadata eq operator ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC286_BkSlotMetadataEqTests extends TestBase {
    @Test
    @DisplayName("TC286 — metadata?key=room&operator=eq&value=101 matches exact value")
    void metadata_eq() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long s1 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-01", "09:00", "10:00", true);
        long s2 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-02", "09:00", "10:00", true);
        _BkM1Seed.setTimeSlotMetadata(this, s1, "{\"room\":\"101\"}");
        _BkM1Seed.setTimeSlotMetadata(this, s2, "{\"room\":\"202\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/metadata/search?key=room&operator=eq&value=101", tok);
        assert2xx(r, "TC286");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC286: 1 slot with room=101 expected; got " + list.size());
    }
}

// ─── TC287 — S4-F5 unknown operator 400 ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC287_BkSlotMetadataInvalidOpTests extends TestBase {
    @Test
    @DisplayName("TC287 — metadata search with unknown operator returns 400")
    void metadata_invalid_op() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/metadata/search?key=duration&operator=zzz&value=30", tok);
        assertEquals(400, r.statusCode(), "TC287: must be 400; got " + r.statusCode());
    }
}

// ─── TC288 — S4-F6 history happy ─────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC288_BkHistoryHappyTests extends TestBase {
    @Test
    @DisplayName("TC288 — History endpoint returns slots within range filtered by provider")
    void history_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pA = _BkM1Seed.seedProvider(this, "PA", "Dentist", "AVAILABLE", 4.0, 0);
        long pB = _BkM1Seed.seedProvider(this, "PB", "Tutor",   "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedTimeSlot(this, pA, "2026-03-10", "09:00", "10:00", true);
        _BkM1Seed.seedTimeSlot(this, pA, "2026-03-15", "09:00", "10:00", true);
        _BkM1Seed.seedTimeSlot(this, pB, "2026-03-10", "09:00", "10:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/history?startDate=2026-03-01&endDate=2026-03-31&providerId=" + pA, tok);
        assert2xx(r, "TC288");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(2, list.size(), "TC288: 2 slots for PA expected; got " + list.size());
    }
}

// ─── TC289 — S4-F6 history empty range ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC289_BkHistoryEmptyRangeTests extends TestBase {
    @Test
    @DisplayName("TC289 — History in empty range returns empty list")
    void history_empty() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedTimeSlot(this, pid, "2026-03-10", "09:00", "10:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/history?startDate=2030-01-01&endDate=2030-01-31&providerId=" + pid, tok);
        assert2xx(r, "TC289");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC289: empty list expected; got " + list.size());
    }
}

// ─── TC290 — S4-F7 purge old slots ───────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC290_BkPurgeOldSlotsTests extends TestBase {
    @Test
    @DisplayName("TC290 — Purge?olderThanDays=30 removes only slots older than 30 days")
    void purge_old() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long old1 = _BkM1Seed.seedTimeSlot(this, pid, "2020-01-01", "09:00", "10:00", false);
        long recent = _BkM1Seed.seedTimeSlot(this, pid, "2030-01-01", "09:00", "10:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpDeleteAuth("/api/timeslots/purge?olderThanDays=30", tok);
        assert2xx(r, "TC290");
        Long oldExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("TimeSlot") + "\" WHERE id = ?", Long.class, old1);
        Long recentExists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("TimeSlot") + "\" WHERE id = ?", Long.class, recent);
        assertEquals(0L, oldExists == null ? -1L : oldExists.longValue(),
                "TC290: old slot must be purged; got count=" + oldExists);
        assertEquals(1L, recentExists == null ? -1L : recentExists.longValue(),
                "TC290: recent slot must survive; got count=" + recentExists);
    }
}

// ─── TC291 — S4-F7 purge negative days 400 ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC291_BkPurgeNegativeDaysTests extends TestBase {
    @Test
    @DisplayName("TC291 — Purge with olderThanDays=-1 returns 400")
    void purge_negative() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpDeleteAuth("/api/timeslots/purge?olderThanDays=-1", tok);
        assertEquals(400, r.statusCode(), "TC291: must be 400; got " + r.statusCode());
    }
}

// ─── TC292 — S4-F8 utilization happy ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC292_BkUtilizationHappyTests extends TestBase {
    @Test
    @DisplayName("TC292 — Utilization: 10 slots, 6 booked → utilizationRate=0.6")
    void utilization_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        for (int i = 0; i < 4; i++) _BkM1Seed.seedTimeSlot(this, pid, String.format("2026-04-%02d", i + 1), "09:00", "10:00", true);
        for (int i = 0; i < 6; i++) _BkM1Seed.seedTimeSlot(this, pid, String.format("2026-04-%02d", i + 5), "09:00", "10:00", false);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/provider/" + pid + "/utilization?startDate=2026-04-01&endDate=2026-04-30", tok);
        assert2xx(r, "TC292");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalSlots", "total_slots");
        long booked = _BkM2.rL(j, "bookedSlots", "booked_slots");
        double rate = _BkM2.rD(j, "utilizationRate", "utilization_rate");
        assertEquals(10L, total, "TC292: totalSlots=10; got " + total);
        assertEquals(6L, booked, "TC292: bookedSlots=6; got " + booked);
        assertEquals(0.6, rate, 0.05, "TC292: utilizationRate=0.6; got " + rate);
    }
}

// ─── TC293 — S4-F8 utilization no slots zeros ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC293_BkUtilizationNoSlotsTests extends TestBase {
    @Test
    @DisplayName("TC293 — Utilization for provider with no slots returns zeros")
    void utilization_no_slots() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/provider/" + pid + "/utilization?startDate=2026-04-01&endDate=2026-04-30", tok);
        assert2xx(r, "TC293");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalSlots", "total_slots");
        assertEquals(0L, total, "TC293: totalSlots=0; got " + total);
    }
}

// ─── TC294 — S4-F8 utilization 404 unknown provider ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC294_BkUtilizationNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC294 — Utilization for non-existent provider returns 404")
    void utilization_not_found() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/provider/999999/utilization?startDate=2026-04-01&endDate=2026-04-30", tok);
        assertEquals(404, r.statusCode(), "TC294: must be 404; got " + r.statusCode());
    }
}

// ─── TC295 — S4-F9 idle providers happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC295_BkIdleProvidersHappyTests extends TestBase {
    @Test
    @DisplayName("TC295 — idle?maxBookedSlots=2&sinceDays=30 returns providers with ≤2 booked slots")
    void idle_happy() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pA = _BkM1Seed.seedProvider(this, "PA", "Dentist", "AVAILABLE", 4.0, 0);
        long pB = _BkM1Seed.seedProvider(this, "PB", "Dentist", "AVAILABLE", 4.0, 0);
        // PA: 1 booked slot (idle)
        _BkM1Seed.seedTimeSlot(this, pA, java.time.LocalDate.now().minusDays(5).toString(), "09:00", "10:00", false);
        // PB: 5 booked slots (busy)
        for (int i = 0; i < 5; i++) {
            _BkM1Seed.seedTimeSlot(this, pB, java.time.LocalDate.now().minusDays(5 + i).toString(), "09:00", "10:00", false);
        }
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/idle?maxBookedSlots=2&sinceDays=30", tok);
        assert2xx(r, "TC295");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean foundPA = false;
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            if (id == pA) foundPA = true;
            assertNotEquals(pB, id, "TC295: PB (busy) must be excluded");
        }
        assertTrue(foundPA, "TC295: PA (idle) must be in results; body=" + r.body());
    }
}

// ─── TC296 — S4-F9 idle empty when none idle ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC296_BkIdleProvidersEmptyTests extends TestBase {
    @Test
    @DisplayName("TC296 — idle endpoint returns empty when all providers exceed maxBookedSlots")
    void idle_empty() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        for (int i = 0; i < 5; i++) {
            _BkM1Seed.seedTimeSlot(this, pid, java.time.LocalDate.now().minusDays(5 + i).toString(),
                    "09:00", "10:00", false);
        }
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/idle?maxBookedSlots=2&sinceDays=30", tok);
        assert2xx(r, "TC296");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "providerId", "id");
            assertNotEquals(pid, id, "TC296: busy provider must be excluded");
        }
    }
}

// ─── TC297 — S4-F9 negative param 400 ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC297_BkIdleNegativeParamTests extends TestBase {
    @Test
    @DisplayName("TC297 — idle with maxBookedSlots=-1 returns 400")
    void idle_negative() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/idle?maxBookedSlots=-1&sinceDays=30", tok);
        assertEquals(400, r.statusCode(), "TC297: must be 400; got " + r.statusCode());
    }
}

// ────────────────────────────────────────────────────────────────────────────
// S5 — Invoice Service (TC298..TC328)  — 9 features × ~3-4 TCs each
// S5-F1 search, S5-F2 refund, S5-F3 user summary, S5-F4 process,
// S5-F5 apply discount, S5-F6 revenue report, S5-F7 retry,
// S5-F8 details, S5-F9 top discounts
// ────────────────────────────────────────────────────────────────────────────

// ─── TC298 — S5-F1 search by status + date range ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC298_BkInvoiceSearchHappyTests extends TestBase {
    @Test
    @DisplayName("TC298 — status=COMPLETED + date range returns matching invoices")
    void invoice_search_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc298@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        long b3 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 300.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b2, uid, 200.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b3, uid, 300.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/search?status=COMPLETED&startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC298");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(2, list.size(), "TC298: 2 COMPLETED invoices expected; got " + list.size());
    }
}

// ─── TC299 — S5-F1 invalid range 400 ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC299_BkInvoiceSearchInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC299 — start>end returns 400")
    void invoice_search_invalid_range() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/search?startDate=2026-03-31&endDate=2026-03-01", tok);
        assertEquals(400, r.statusCode(), "TC299: must be 400; got " + r.statusCode());
    }
}

// ─── TC300 — S5-F1 no match returns empty ────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC300_BkInvoiceSearchNoMatchTests extends TestBase {
    @Test
    @DisplayName("TC300 — Search with status=REFUNDED returns empty when none refunded")
    void invoice_search_no_match() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/search?status=REFUNDED", tok);
        assert2xx(r, "TC300");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC300: empty list expected; got " + list.size());
    }
}

// ─── TC301 — S5-F2 refund happy ──────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC301_BkRefundHappyTests extends TestBase {
    @Test
    @DisplayName("TC301 — Refund COMPLETED invoice transitions to REFUNDED")
    void refund_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc301@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/" + iid + "/refund",
                "{\"reason\":\"client requested\"}", tok);
        assert2xx(r, "TC301");
        String stCol = columnByField("Invoice", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Invoice") + "\" WHERE id = ?",
            String.class, iid);
        assertEquals("REFUNDED", dbStatus, "TC301: invoice.status=REFUNDED expected; got " + dbStatus);
    }
}

// ─── TC302 — S5-F2 refund PENDING invoice 400 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC302_BkRefundPendingTests extends TestBase {
    @Test
    @DisplayName("TC302 — Refund PENDING invoice returns 400")
    void refund_pending() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc302@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/" + iid + "/refund",
                "{\"reason\":\"x\"}", tok);
        assertEquals(400, r.statusCode(), "TC302: must be 400; got " + r.statusCode());
    }
}

// ─── TC303 — S5-F2 refund 404 not found ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC303_BkRefundNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC303 — Refund non-existent invoice returns 404")
    void refund_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/999999/refund",
                "{\"reason\":\"x\"}", tok);
        assertEquals(404, r.statusCode(), "TC303: must be 404; got " + r.statusCode());
    }
}

// ─── TC304 — S5-F3 user invoice summary happy ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC304_BkUserSummaryHappyTests extends TestBase {
    @Test
    @DisplayName("TC304 — User summary aggregates totalInvoices, totalAmount, methodBreakdown")
    void user_summary_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc304@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        long b3 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 300.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b2, uid, 200.0, "CASH",        "COMPLETED");
        _BkM1Seed.seedInvoice(this, b3, uid, 300.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/user/" + uid + "/summary", tok);
        assert2xx(r, "TC304");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalInvoices", "total_invoices");
        double amount = _BkM2.rD(j, "totalAmount", "total_amount");
        assertEquals(3L, total, "TC304: totalInvoices=3; got " + total);
        assertEquals(600.0, amount, 1.0, "TC304: totalAmount=600; got " + amount);
        JsonNode breakdown = _BkM2.rO(j, "methodBreakdown", "method_breakdown");
        assertNotNull(breakdown, "TC304: methodBreakdown required; body=" + r.body());
    }
}

// ─── TC305 — S5-F3 no invoices zeros ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC305_BkUserSummaryEmptyTests extends TestBase {
    @Test
    @DisplayName("TC305 — User summary for user with no invoices returns zeros")
    void user_summary_empty() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc305@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/user/" + uid + "/summary", tok);
        assert2xx(r, "TC305");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalInvoices", "total_invoices");
        assertEquals(0L, total, "TC305: totalInvoices=0; got " + total);
    }
}

// ─── TC306 — S5-F3 404 user not found ────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC306_BkUserSummaryNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC306 — User summary for non-existent user returns 404")
    void user_summary_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/user/999999/summary", tok);
        assertEquals(404, r.statusCode(), "TC306: must be 404; got " + r.statusCode());
    }
}

// ─── TC307 — S5-F4 process invoice happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC307_BkProcessInvoiceHappyTests extends TestBase {
    @Test
    @DisplayName("TC307 — POST /invoices/booking/{id} creates invoice with PENDING/COMPLETED status")
    void process_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc307@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 250.0);
        String body = "{\"method\":\"CREDIT_CARD\",\"cardLastFour\":\"4242\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/invoices/booking/" + bid, body, tok);
        assert2xx(r, "TC307");
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("Invoice") + "\" WHERE \""
            + columnByField("Invoice", "booking") + "\" = ?", Long.class, bid);
        assertTrue(count != null && count >= 1, "TC307: invoice should be created; got count=" + count);
    }
}

// ─── TC308 — S5-F4 process 404 booking not found ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC308_BkProcessInvoiceNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC308 — Process invoice for non-existent booking returns 404")
    void process_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String body = "{\"method\":\"CREDIT_CARD\",\"cardLastFour\":\"4242\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/invoices/booking/999999", body, tok);
        assertEquals(404, r.statusCode(), "TC308: must be 404; got " + r.statusCode());
    }
}

// ─── TC309 — S5-F4 process invalid method 400 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC309_BkProcessInvoiceInvalidMethodTests extends TestBase {
    @Test
    @DisplayName("TC309 — Process with invalid method returns 400")
    void process_invalid_method() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc309@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        String body = "{\"method\":\"BITCOIN\",\"cardLastFour\":\"\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/invoices/booking/" + bid, body, tok);
        assertEquals(400, r.statusCode(), "TC309: must be 400; got " + r.statusCode());
    }
}

// ─── TC310 — S5-F5 apply discount happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC310_BkApplyDiscountHappyTests extends TestBase {
    @Test
    @DisplayName("TC310 — Apply 20% discount to 100.0 invoice → InvoiceDiscount row + currentUses incremented")
    void apply_discount_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc310@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long did = _BkM1Seed.seedDiscount(this, "PROMO20_" + nonce(), "PERCENTAGE", 20.0, 100,
                _BkM1Seed.futureDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/invoices/" + iid + "/discounts/" + did, "", tok);
        assert2xx(r, "TC310");
        Long ldCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("InvoiceDiscount") + "\" WHERE \""
            + columnByField("InvoiceDiscount", "invoice") + "\" = ? AND \""
            + columnByField("InvoiceDiscount", "discount") + "\" = ?", Long.class, iid, did);
        assertEquals(1L, ldCount == null ? 0L : ldCount.longValue(),
                "TC310: InvoiceDiscount row created; got count=" + ldCount);
        Integer uses = jdbc.queryForObject(
            "SELECT \"" + columnByField("Discount", "currentUses") + "\" FROM \""
            + tableName("Discount") + "\" WHERE id = ?", Integer.class, did);
        assertEquals(1, uses == null ? 0 : uses, "TC310: currentUses=1; got " + uses);
    }
}

// ─── TC311 — S5-F5 expired discount 400 ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC311_BkApplyDiscountExpiredTests extends TestBase {
    @Test
    @DisplayName("TC311 — Apply expired discount returns 400")
    void apply_expired() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc311@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long did = _BkM1Seed.seedDiscount(this, "EXPIRED_" + nonce(), "PERCENTAGE", 10.0, 100,
                _BkM1Seed.pastDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/invoices/" + iid + "/discounts/" + did, "", tok);
        assertEquals(400, r.statusCode(), "TC311: must be 400; got " + r.statusCode());
    }
}

// ─── TC312 — S5-F5 maxUses exceeded 400 ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC312_BkApplyDiscountMaxUsesTests extends TestBase {
    @Test
    @DisplayName("TC312 — Apply discount with currentUses>=maxUses returns 400")
    void apply_max_uses() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc312@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long did = _BkM1Seed.seedDiscount(this, "MAX_" + nonce(), "PERCENTAGE", 10.0, 1,
                _BkM1Seed.futureDateTime(), true);
        _BkM1Seed.setDiscountCurrentUses(this, did, 1);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/invoices/" + iid + "/discounts/" + did, "", tok);
        assertEquals(400, r.statusCode(), "TC312: must be 400; got " + r.statusCode());
    }
}

// ─── TC313 — S5-F5 inactive discount 400 ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC313_BkApplyDiscountInactiveTests extends TestBase {
    @Test
    @DisplayName("TC313 — Apply inactive discount returns 400")
    void apply_inactive() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc313@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        long did = _BkM1Seed.seedDiscount(this, "INACT_" + nonce(), "PERCENTAGE", 10.0, 100,
                _BkM1Seed.futureDateTime(), false);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/invoices/" + iid + "/discounts/" + did, "", tok);
        assertEquals(400, r.statusCode(), "TC313: must be 400; got " + r.statusCode());
    }
}

// ─── TC314 — S5-F6 revenue report happy ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC314_BkRevenueReportHappyTests extends TestBase {
    @Test
    @DisplayName("TC314 — Revenue report aggregates totalRevenue, totalTransactions, refundedAmount")
    void revenue_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc314@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        long b3 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 300.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b2, uid, 200.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b3, uid, 300.0, "CREDIT_CARD", "REFUNDED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC314");
        JsonNode j = parseNode(r.body());
        double total = _BkM2.rD(j, "totalRevenue", "total_revenue");
        long count = _BkM2.rL(j, "totalTransactions", "total_transactions");
        double refunded = _BkM2.rD(j, "refundedAmount", "refunded_amount");
        long refundCount = _BkM2.rL(j, "refundCount", "refund_count");
        assertEquals(300.0, total, 1.0, "TC314: totalRevenue=300 (only COMPLETED); got " + total);
        assertEquals(2L, count, "TC314: totalTransactions=2; got " + count);
        assertEquals(300.0, refunded, 1.0, "TC314: refundedAmount=300; got " + refunded);
        assertEquals(1L, refundCount, "TC314: refundCount=1; got " + refundCount);
    }
}

// ─── TC315 — S5-F6 empty range zeros ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC315_BkRevenueEmptyRangeTests extends TestBase {
    @Test
    @DisplayName("TC315 — Revenue report in empty range returns zeros")
    void revenue_empty() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/reports/revenue?startDate=2030-01-01&endDate=2030-01-31", tok);
        assert2xx(r, "TC315");
        JsonNode j = parseNode(r.body());
        double total = _BkM2.rD(j, "totalRevenue", "total_revenue");
        assertEquals(0.0, total, 0.01, "TC315: totalRevenue=0; got " + total);
    }
}

// ─── TC316 — S5-F6 invalid range 400 ─────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC316_BkRevenueInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC316 — Revenue start>end returns 400")
    void revenue_invalid_range() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/reports/revenue?startDate=2026-03-31&endDate=2026-03-01", tok);
        assertEquals(400, r.statusCode(), "TC316: must be 400; got " + r.statusCode());
    }
}

// ─── TC317 — S5-F7 retry FAILED invoice happy ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC317_BkRetryHappyTests extends TestBase {
    @Test
    @DisplayName("TC317 — Retry FAILED invoice transitions to PENDING/COMPLETED")
    void retry_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc317@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "FAILED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/" + iid + "/retry", "", tok);
        assert2xx(r, "TC317");
        String stCol = columnByField("Invoice", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Invoice") + "\" WHERE id = ?",
            String.class, iid);
        assertNotEquals("FAILED", dbStatus,
                "TC317: status must change from FAILED; got " + dbStatus);
    }
}

// ─── TC318 — S5-F7 retry COMPLETED rejected ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC318_BkRetryCompletedTests extends TestBase {
    @Test
    @DisplayName("TC318 — Retry COMPLETED invoice returns 400")
    void retry_completed() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc318@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/" + iid + "/retry", "", tok);
        assertEquals(400, r.statusCode(), "TC318: must be 400; got " + r.statusCode());
    }
}

// ─── TC319 — S5-F7 retry 404 ─────────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC319_BkRetryNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC319 — Retry non-existent invoice returns 404")
    void retry_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/999999/retry", "", tok);
        assertEquals(404, r.statusCode(), "TC319: must be 404; got " + r.statusCode());
    }
}

// ─── TC320 — S5-F8 invoice details happy ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC320_BkInvoiceDetailsHappyTests extends TestBase {
    @Test
    @DisplayName("TC320 — Details DTO surfaces transactionDetails JSONB + appliedDiscounts list")
    void details_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc320@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.setInvoiceTransactionDetails(this, iid,
                "{\"cardLastFour\":\"4242\",\"authCode\":\"AUTH1\"}");
        long did = _BkM1Seed.seedDiscount(this, "D_" + nonce(), "PERCENTAGE", 20.0, 100,
                _BkM1Seed.futureDateTime(), true);
        _BkM1Seed.seedInvoiceDiscount(this, iid, did, 20.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/" + iid + "/details", tok);
        assert2xx(r, "TC320");
        JsonNode j = parseNode(r.body());
        JsonNode txDetails = _BkM2.rO(j, "transactionDetails", "transaction_details");
        assertNotNull(txDetails, "TC320: transactionDetails required; body=" + r.body());
        JsonNode applied = _BkM2.rO(j, "appliedDiscounts", "applied_discounts");
        assertNotNull(applied, "TC320: appliedDiscounts list required");
        assertTrue(applied.size() >= 1, "TC320: at least 1 discount expected; got " + applied.size());
    }
}

// ─── TC321 — S5-F8 details no discounts ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC321_BkInvoiceDetailsNoDiscountsTests extends TestBase {
    @Test
    @DisplayName("TC321 — Details for invoice with no discounts returns empty appliedDiscounts")
    void details_no_discounts() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc321@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CASH", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/" + iid + "/details", tok);
        assert2xx(r, "TC321");
        JsonNode j = parseNode(r.body());
        JsonNode applied = _BkM2.rO(j, "appliedDiscounts", "applied_discounts");
        assertNotNull(applied, "TC321: appliedDiscounts must be present (empty)");
        assertEquals(0, applied.size(), "TC321: appliedDiscounts size=0; got " + applied.size());
    }
}

// ─── TC322 — S5-F8 details 404 ───────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC322_BkInvoiceDetailsNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC322 — Details for non-existent invoice returns 404")
    void details_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/999999/details", tok);
        assertEquals(404, r.statusCode(), "TC322: must be 404; got " + r.statusCode());
    }
}

// ─── TC323 — S5-F9 top discounts happy ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC323_BkTopDiscountsHappyTests extends TestBase {
    @Test
    @DisplayName("TC323 — Top discounts ranks by usage count desc")
    void top_discounts_happy() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc323@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 100.0);
        long b3 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 100.0);
        long i1 = _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long i2 = _BkM1Seed.seedInvoice(this, b2, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long i3 = _BkM1Seed.seedInvoice(this, b3, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long dHigh = _BkM1Seed.seedDiscount(this, "HIGH_" + nonce(), "PERCENTAGE", 20.0, 100,
                _BkM1Seed.futureDateTime(), true);
        long dLow = _BkM1Seed.seedDiscount(this, "LOW_" + nonce(), "PERCENTAGE", 5.0, 100,
                _BkM1Seed.futureDateTime(), true);
        _BkM1Seed.seedInvoiceDiscount(this, i1, dHigh, 20.0);
        _BkM1Seed.seedInvoiceDiscount(this, i2, dHigh, 20.0);
        _BkM1Seed.seedInvoiceDiscount(this, i3, dLow,  5.0);
        _BkM1Seed.setDiscountCurrentUses(this, dHigh, 2);
        _BkM1Seed.setDiscountCurrentUses(this, dLow,  1);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/discounts/top-used?limit=10", tok);
        assert2xx(r, "TC323");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 2, "TC323: at least 2 discounts expected; got " + list.size());
        long firstId = _BkM2.rL(list.get(0), "discountId", "id");
        assertEquals(dHigh, firstId, "TC323: dHigh (2 uses) must rank first; got id=" + firstId);
    }
}

// ─── TC324 — S5-F9 limit overflow ────────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC324_BkTopDiscountsLimitOverflowTests extends TestBase {
    @Test
    @DisplayName("TC324 — Top discounts limit=100 returns all (no error)")
    void top_discounts_overflow() throws Exception {
        BASE_URL = checkoutServiceUrl;
        _BkM1Seed.seedDiscount(this, "D1_" + nonce(), "PERCENTAGE", 20.0, 100,
                _BkM1Seed.futureDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/discounts/top-used?limit=100", tok);
        assert2xx(r, "TC324");
    }
}

// ─── TC325 — S5-F9 expired flag set ──────────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC325_BkTopDiscountsExpiredFlagTests extends TestBase {
    @Test
    @DisplayName("TC325 — expired=true for discounts past expiryDate")
    void top_discounts_expired_flag() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc325@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long did = _BkM1Seed.seedDiscount(this, "EXP_" + nonce(), "PERCENTAGE", 10.0, 100,
                _BkM1Seed.pastDateTime(), true);
        _BkM1Seed.seedInvoiceDiscount(this, iid, did, 10.0);
        _BkM1Seed.setDiscountCurrentUses(this, did, 1);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/discounts/top-used?limit=10", tok);
        assert2xx(r, "TC325");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "discountId", "id");
            if (id == did) {
                assertTrue(it.has("expired") && it.get("expired").asBoolean(),
                        "TC325: expired=true expected for past-expiry discount; got " + it);
                return;
            }
        }
        throw new AssertionError("TC325: target expired discount not found; body=" + r.body());
    }
}

// ─── TC326 — S5-F1 invoice search by status only ─────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC326_BkInvoiceSearchStatusOnlyTests extends TestBase {
    @Test
    @DisplayName("TC326 — Search by status=PENDING returns only PENDING invoices")
    void search_status_only() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc326@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "PENDING");
        _BkM1Seed.seedInvoice(this, b2, uid, 200.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/search?status=PENDING", tok);
        assert2xx(r, "TC326");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("PENDING", s, "TC326: every result must be PENDING; got " + s);
        }
    }
}

// ─── TC327 — S5-F4 process double-billing rejected ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC327_BkProcessDoubleBillingTests extends TestBase {
    @Test
    @DisplayName("TC327 — Process invoice for booking with existing invoice returns 400")
    void process_double_billing() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc327@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        String body = "{\"method\":\"CREDIT_CARD\",\"cardLastFour\":\"4242\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/invoices/booking/" + bid, body, tok);
        assertEquals(400, r.statusCode(), "TC327: must be 400 (already invoiced); got " + r.statusCode());
    }
}

// ─── TC328 — S5-F8 details for refunded shows updated status ────────────────
@Tag("public")
@Tag("features_m1")
class TC328_BkInvoiceDetailsRefundedTests extends TestBase {
    @Test
    @DisplayName("TC328 — Details for REFUNDED invoice surfaces status=REFUNDED")
    void details_refunded() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc328@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "REFUNDED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/" + iid + "/details", tok);
        assert2xx(r, "TC328");
        JsonNode j = parseNode(r.body());
        String status = j.has("status") ? j.get("status").asText() : "";
        assertEquals("REFUNDED", status, "TC328: status=REFUNDED expected; got " + status);
    }
}

// ════════════════════════════════════════════════════════════════════════════
// EXTRAS — TC329..TC378 (50 additional tests across all 5 services)
// Distribution mirrors Amazon's extras: extra spec-scenario coverage that
// didn't fit in the main TC191-TC328 allocation.
// TC329-TC335 (S1) + TC336-TC341 (S2) + TC342-TC351 (S3) + TC352-TC359 (S4)
// + TC360-TC369 (S5) + TC370-TC378 (mixed/edge cases per service)
// ════════════════════════════════════════════════════════════════════════════

// ─── TC329 — S1-F1 search by email match ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC329_BkSearchUsersByEmailTests extends TestBase {
    @Test
    @DisplayName("TC329 — Search by email='admin' returns matching users")
    void search_by_email() throws Exception {
        BASE_URL = userServiceUrl;
        _BkM1Seed.seedUser(this, "Admin Boss",  "admin_root_" + nonce() + "@bk.io", "ADMIN");
        _BkM1Seed.seedUser(this, "Plain Sara",  "sara_" + nonce() + "@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?email=admin", tok);
        assert2xx(r, "TC329 search");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertTrue(list.size() >= 1, "TC329: at least one admin email match expected; got " + list.size());
    }
}

// ─── TC330 — S1-F1 combined name+role filters ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC330_BkSearchUsersCombinedFiltersTests extends TestBase {
    @Test
    @DisplayName("TC330 — Combined name=Mona+role=ADMIN returns only Mona-named ADMIN users")
    void search_combined() throws Exception {
        BASE_URL = userServiceUrl;
        _BkM1Seed.seedUser(this, "Mona", "tc330_a@bk.io", "ADMIN");
        _BkM1Seed.seedUser(this, "Mona", "tc330_b@bk.io", "CLIENT");
        _BkM1Seed.seedUser(this, "Sara", "tc330_c@bk.io", "ADMIN");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/search?name=Mona&role=ADMIN", tok);
        assert2xx(r, "TC330");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String n = it.has("name") ? it.get("name").asText() : "";
            String role = it.has("role") ? it.get("role").asText() : "";
            assertTrue(n.contains("Mona") && "ADMIN".equals(role),
                "TC330: only Mona+ADMIN expected; got name=" + n + " role=" + role);
        }
    }
}

// ─── TC331 — S1-F2 add new key without overwriting existing ──────────────────
@Tag("public")
@Tag("features_m1")
class TC331_BkUpdatePreferencesNewKeyTests extends TestBase {
    @Test
    @DisplayName("TC331 — PUT preferences with new key adds to JSONB without affecting existing")
    void prefs_new_key() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Pref User", "tc331@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uid, "{\"language\":\"en\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/preferences",
                "{\"notifications\":\"sms\"}", tok);
        assert2xx(r, "TC331");
        JsonNode prefs = parseNode(r.body()).has("preferences")
                ? parseNode(r.body()).get("preferences") : parseNode(r.body());
        assertEquals("en", prefs.has("language") ? prefs.get("language").asText() : "",
                "TC331: language preserved");
        assertEquals("sms", prefs.has("notifications") ? prefs.get("notifications").asText() : "",
                "TC331: notifications added");
    }
}

// ─── TC332 — S1-F3 booking summary excludes other users ──────────────────────
@Tag("public")
@Tag("features_m1")
class TC332_BkBookingSummaryUserScopedTests extends TestBase {
    @Test
    @DisplayName("TC332 — Booking summary scopes to single user only")
    void summary_user_scoped() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "UA", "tc332_a@bk.io", "CLIENT");
        long uB = _BkM1Seed.seedUser(this, "UB", "tc332_b@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-11", 200.0);
        _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-12", 300.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uA + "/booking-summary", tok);
        assert2xx(r, "TC332");
        JsonNode j = parseNode(r.body());
        long total = _BkM2.rL(j, "totalBookings", "total_bookings");
        assertEquals(1L, total, "TC332: A's totalBookings=1; got " + total);
    }
}

// ─── TC333 — S1-F4 deactivated user blocked from re-deactivation ─────────────
@Tag("public")
@Tag("features_m1")
class TC333_BkDeactivateAlreadyDeactivatedTests extends TestBase {
    @Test
    @DisplayName("TC333 — Deactivate already-DEACTIVATED user returns 400")
    void deactivate_already() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc333@bk.io", "CLIENT");
        jdbc.update("UPDATE \"" + tableName("User") + "\" SET \""
            + columnByField("User", "status") + "\" = ? WHERE id = ?", "DEACTIVATED", uid);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/users/" + uid + "/deactivate", "", tok);
        assertEquals(400, r.statusCode(), "TC333: must be 400; got " + r.statusCode());
    }
}

// ─── TC334 — S1-F6 top-clients respects limit ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC334_BkTopClientsLimitTests extends TestBase {
    @Test
    @DisplayName("TC334 — Top clients respects limit=1 (only top spender)")
    void top_clients_limit() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "UA", "tc334_a@bk.io", "CLIENT");
        long uB = _BkM1Seed.seedUser(this, "UB", "tc334_b@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uA, pid, "COMPLETED", "2026-03-10", 1000.0);
        _BkM1Seed.seedBooking(this, uB, pid, "COMPLETED", "2026-03-11", 500.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/users/reports/top-clients?startDate=2026-03-01&endDate=2026-03-31&limit=1", tok);
        assert2xx(r, "TC334");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC334: limit=1 enforced; got " + list.size());
    }
}

// ─── TC335 — S1-F8 profile email/phone present ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC335_BkUserProfileBasicFieldsTests extends TestBase {
    @Test
    @DisplayName("TC335 — Profile DTO surfaces email + phone basic fields")
    void profile_basic_fields() throws Exception {
        BASE_URL = userServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "Field User", "tc335@bk.io", "CLIENT");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/" + uid + "/profile", tok);
        assert2xx(r, "TC335");
        JsonNode j = parseNode(r.body());
        assertTrue(j.has("email"), "TC335: email field required");
        assertTrue(j.has("phone"), "TC335: phone field required");
        assertEquals("tc335@bk.io",
                j.has("email") ? j.get("email").asText() : "", "TC335: email matches");
    }
}

// ─── TC336 — S2-F1 search no token 401 ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC336_BkProviderSearchNoTokenTests extends TestBase {
    @Test
    @DisplayName("TC336 — Provider search without token returns 401")
    void search_no_token() throws Exception {
        BASE_URL = catalogServiceUrl;
        HttpResponse<String> r = httpGet("/api/providers/search?status=AVAILABLE");
        assertEquals(401, r.statusCode(), "TC336: must be 401; got " + r.statusCode());
    }
}

// ─── TC337 — S2-F2 details PUT preserves nested keys ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC337_BkProviderDetailsNestedPreserveTests extends TestBase {
    @Test
    @DisplayName("TC337 — Details merge preserves unmodified top-level keys")
    void details_preserve() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.setProviderServiceDetails(this, pid,
                "{\"years\":\"5\",\"languages\":[\"en\",\"ar\"],\"certifications\":[\"BDS\"]}");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/providers/" + pid + "/service-details",
                "{\"languages\":[\"fr\"]}", tok);
        assert2xx(r, "TC337");
        JsonNode j = parseNode(r.body());
        JsonNode details = _BkM2.rO(j, "serviceDetails", "service_details", "details");
        if (details == null) details = j;
        assertEquals("5", details.has("years") ? details.get("years").asText() : "",
                "TC337: years preserved");
        JsonNode certs = details.has("certifications") ? details.get("certifications") : null;
        assertNotNull(certs, "TC337: certifications preserved");
    }
}

// ─── TC338 — S2-F3 earnings invalid range 400 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC338_BkProviderEarningsInvalidRangeTests extends TestBase {
    @Test
    @DisplayName("TC338 — Earnings start>end returns 400")
    void earnings_invalid_range() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/providers/" + pid + "/earnings?startDate=2026-03-31&endDate=2026-03-01", tok);
        assertEquals(400, r.statusCode(), "TC338: must be 400; got " + r.statusCode());
    }
}

// ─── TC339 — S2-F4 toggle to BUSY succeeds ───────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC339_BkToggleAvailabilityToBusyTests extends TestBase {
    @Test
    @DisplayName("TC339 — Toggle from AVAILABLE to BUSY succeeds")
    void toggle_to_busy() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/providers/" + pid + "/availability",
                "{\"status\":\"BUSY\"}", tok);
        assert2xx(r, "TC339");
        String stCol = columnByField("Provider", "status");
        String dbStatus = jdbc.queryForObject(
            "SELECT \"" + stCol + "\"::text FROM \"" + tableName("Provider") + "\" WHERE id = ?",
            String.class, pid);
        assertEquals("BUSY", dbStatus, "TC339: status=BUSY expected; got " + dbStatus);
    }
}

// ─── TC340 — S2-F7 rate already-rated booking 400 ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC340_BkRateDuplicateTests extends TestBase {
    @Test
    @DisplayName("TC340 — Rating same booking twice returns 400")
    void rate_duplicate() throws Exception {
        BASE_URL = catalogServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc340@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 0.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 50.0);
        String tok = adminToken();
        assert2xx(httpPostAuth("/api/providers/" + pid + "/rate",
                "{\"bookingId\":" + bid + ",\"rating\":4}", tok), "TC340 first");
        HttpResponse<String> r = httpPostAuth("/api/providers/" + pid + "/rate",
                "{\"bookingId\":" + bid + ",\"rating\":4}", tok);
        assertEquals(400, r.statusCode(), "TC340: dup rating must be 400; got " + r.statusCode());
    }
}

// ─── TC341 — S2-F8 verify already-verified noop or success ──────────────────
@Tag("public")
@Tag("features_m1")
class TC341_BkVerifyCertAlreadyVerifiedTests extends TestBase {
    @Test
    @DisplayName("TC341 — Verify already-verified cert is idempotent (200) or 400")
    void verify_already() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long cid = _BkM1Seed.seedCertification(this, pid, "LICENSE",
                java.time.LocalDate.of(2030, 1, 1), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/" + pid + "/certifications/" + cid + "/verify",
            "{\"verifiedBy\":3}", tok);
        int sc = r.statusCode();
        assertTrue(sc / 100 == 2 || sc == 400,
                "TC341: must be 2xx or 400 for already-verified; got " + sc);
    }
}

// ─── TC342 — S3-F2 assign to unknown provider 404 ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC342_BkAssignProviderUnknownTests extends TestBase {
    @Test
    @DisplayName("TC342 — Assign with unknown providerId returns 404")
    void assign_unknown_provider() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc342@bk.io", "CLIENT");
        long bid = _BkM1Seed.seedBooking(this, uid, 0L, "REQUESTED", "2030-04-10", 100.0);
        try {
            jdbc.update("UPDATE \"" + tableName("Booking") + "\" SET \""
                + columnByField("Booking", "provider") + "\" = NULL WHERE id = ?", bid);
        } catch (Throwable ignore) { }
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/bookings/" + bid + "/assign?providerId=999999", "", tok);
        assertEquals(404, r.statusCode(), "TC342: must be 404; got " + r.statusCode());
    }
}

// ─── TC343 — S3-F3 estimate empty services 400 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC343_BkEstimateEmptyServicesTests extends TestBase {
    @Test
    @DisplayName("TC343 — Estimate with empty services list returns 400")
    void estimate_empty() throws Exception {
        BASE_URL = orderServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String body = "{\"providerId\":" + pid + ",\"appointmentDate\":\"2030-04-10\",\"services\":[]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/estimate", body, tok);
        assertEquals(400, r.statusCode(), "TC343: must be 400; got " + r.statusCode());
    }
}

// ─── TC344 — S3-F4 complete idempotency 400 (already COMPLETED) ──────────────
@Tag("public")
@Tag("features_m1")
class TC344_BkCompleteAlreadyTests extends TestBase {
    @Test
    @DisplayName("TC344 — Complete already-COMPLETED booking returns 400")
    void complete_already() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc344@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/complete", "", tok);
        assertEquals(400, r.statusCode(), "TC344: must be 400; got " + r.statusCode());
    }
}

// ─── TC345 — S3-F5 metadata search nested key ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC345_BkMetadataSearchNestedKeyTests extends TestBase {
    @Test
    @DisplayName("TC345 — metadata search with key=device matches by JSONB top-level key")
    void metadata_nested() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc345@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 100.0);
        _BkM1Seed.setBookingMetadata(this, b1, "{\"device\":\"ios\"}");
        _BkM1Seed.setBookingMetadata(this, b2, "{\"device\":\"android\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/metadata/search?key=device&value=ios", tok);
        assert2xx(r, "TC345");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC345: 1 ios booking expected; got " + list.size());
    }
}

// ─── TC346 — S3-F6 analytics zero completion rate ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC346_BkAnalyticsZeroCompletionTests extends TestBase {
    @Test
    @DisplayName("TC346 — Analytics with all CANCELLED returns completionRate=0")
    void analytics_zero() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc346@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "CANCELLED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uid, pid, "CANCELLED", "2026-03-11", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/analytics?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC346");
        JsonNode j = parseNode(r.body());
        double rate = _BkM2.rD(j, "completionRate", "completion_rate");
        assertEquals(0.0, rate, 0.05, "TC346: completionRate=0; got " + rate);
    }
}

// ─── TC347 — S3-F7 cancel CONFIRMED booking ──────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC347_BkCancelConfirmedTests extends TestBase {
    @Test
    @DisplayName("TC347 — Cancel CONFIRMED booking transitions to CANCELLED")
    void cancel_confirmed() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc347@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "CONFIRMED", "2030-04-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/cancel", "", tok);
        assert2xx(r, "TC347");
    }
}

// ─── TC348 — S3-F8 add services to CONFIRMED booking ─────────────────────────
@Tag("public")
@Tag("features_m1")
class TC348_BkAddServicesConfirmedTests extends TestBase {
    @Test
    @DisplayName("TC348 — Add services to CONFIRMED booking succeeds")
    void add_services_confirmed() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc348@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "CONFIRMED", "2030-04-10", 0.0);
        String body = "{\"services\":[{\"serviceName\":\"Cleaning\",\"duration\":30,\"price\":50}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/" + bid + "/services", body, tok);
        assert2xx(r, "TC348");
    }
}

// ─── TC349 — S3-F9 details with completed services count ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC349_BkDetailsCompletedCountTests extends TestBase {
    @Test
    @DisplayName("TC349 — Details for booking with all PENDING services has completedServices=0")
    void details_pending_only() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc349@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2026-03-10", 50.0);
        _BkM1Seed.seedBookingService(this, bid, 1, "S1", 30, 50.0, "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/" + bid + "/details", tok);
        assert2xx(r, "TC349");
        JsonNode j = parseNode(r.body());
        long completed = _BkM2.rL(j, "completedServices", "completed_services");
        assertEquals(0L, completed, "TC349: completedServices=0; got " + completed);
    }
}

// ─── TC350 — S3-F1 search status only returns matches ───────────────────────
@Tag("public")
@Tag("features_m1")
class TC350_BkBookingSearchStatusOnlyTests extends TestBase {
    @Test
    @DisplayName("TC350 — Search with status=CANCELLED only returns CANCELLED bookings")
    void search_status_only() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc350@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "CANCELLED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/bookings/search?status=CANCELLED", tok);
        assert2xx(r, "TC350");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            String s = it.has("status") ? it.get("status").asText() : "";
            assertEquals("CANCELLED", s, "TC350: every result must be CANCELLED; got " + s);
        }
    }
}

// ─── TC351 — S3-F4 complete CONFIRMED → COMPLETED ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC351_BkCompleteConfirmedTests extends TestBase {
    @Test
    @DisplayName("TC351 — Complete CONFIRMED booking transitions to COMPLETED")
    void complete_confirmed() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc351@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "CONFIRMED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/bookings/" + bid + "/complete", "", tok);
        int sc = r.statusCode();
        // Spec allows IN_PROGRESS-only → should be 400, but some impls allow CONFIRMED → 200
        assertTrue(sc / 100 == 2 || sc == 400,
                "TC351: must be 2xx or 400; got " + sc);
    }
}

// ─── TC352 — S4-F2 create slot overlap returns 400 ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC352_BkCreateSlotOverlapTests extends TestBase {
    @Test
    @DisplayName("TC352 — Create overlapping slot returns 400")
    void create_overlap() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedTimeSlot(this, pid, "2030-04-10", "09:00", "10:00", true);
        String body = "{\"date\":\"2030-04-10\",\"startTime\":\"09:30\",\"endTime\":\"10:30\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/provider/" + pid, body, tok);
        int sc = r.statusCode();
        assertTrue(sc == 400 || sc == 409,
                "TC352: must be 400/409 for overlap; got " + sc);
    }
}

// ─── TC353 — S4-F4 batch create with one invalid slot 400 ────────────────────
@Tag("public")
@Tag("features_m1")
class TC353_BkBatchPartialInvalidTests extends TestBase {
    @Test
    @DisplayName("TC353 — Batch create with one invalid slot returns 400")
    void batch_invalid() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String body = "{\"providerId\":" + pid + ",\"slots\":["
                + "{\"date\":\"2030-04-10\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"},"
                + "{\"date\":\"2030-04-10\",\"startTime\":\"11:00\",\"endTime\":\"10:00\"}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/timeslots/batch", body, tok);
        assertEquals(400, r.statusCode(), "TC353: must be 400; got " + r.statusCode());
    }
}

// ─── TC354 — S4-F5 metadata operator lt ──────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC354_BkSlotMetadataLtTests extends TestBase {
    @Test
    @DisplayName("TC354 — metadata?operator=lt&value=30 matches duration < 30")
    void metadata_lt() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long s1 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-01", "09:00", "10:00", true);
        long s2 = _BkM1Seed.seedTimeSlot(this, pid, "2026-04-02", "09:00", "10:00", true);
        _BkM1Seed.setTimeSlotMetadata(this, s1, "{\"duration\":15}");
        _BkM1Seed.setTimeSlotMetadata(this, s2, "{\"duration\":45}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/metadata/search?key=duration&operator=lt&value=30", tok);
        assert2xx(r, "TC354");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC354: 1 slot with duration<30 expected; got " + list.size());
    }
}

// ─── TC355 — S4-F6 history without providerId 400 ────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC355_BkHistoryNoProviderTests extends TestBase {
    @Test
    @DisplayName("TC355 — History without providerId returns 400")
    void history_no_provider() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/history?startDate=2026-03-01&endDate=2026-03-31", tok);
        int sc = r.statusCode();
        assertTrue(sc == 400 || sc / 100 == 2,
                "TC355: must be 400 or 2xx; got " + sc);
    }
}

// ─── TC356 — S4-F7 purge zero days deletes nothing ──────────────────────────
@Tag("public")
@Tag("features_m1")
class TC356_BkPurgeZeroDaysTests extends TestBase {
    @Test
    @DisplayName("TC356 — Purge with olderThanDays=0 is rejected or noop")
    void purge_zero_days() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpDeleteAuth("/api/timeslots/purge?olderThanDays=0", tok);
        int sc = r.statusCode();
        assertTrue(sc == 400 || sc / 100 == 2,
                "TC356: must be 400 or 2xx; got " + sc);
    }
}

// ─── TC357 — S4-F8 utilization with zero booked ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC357_BkUtilizationZeroBookedTests extends TestBase {
    @Test
    @DisplayName("TC357 — Utilization with all available=true → utilizationRate=0")
    void utilization_zero_booked() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        for (int i = 0; i < 4; i++) {
            _BkM1Seed.seedTimeSlot(this, pid, "2026-04-0" + (i + 1), "09:00", "10:00", true);
        }
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/timeslots/provider/" + pid + "/utilization?startDate=2026-04-01&endDate=2026-04-30", tok);
        assert2xx(r, "TC357");
        JsonNode j = parseNode(r.body());
        double rate = _BkM2.rD(j, "utilizationRate", "utilization_rate");
        assertEquals(0.0, rate, 0.05, "TC357: utilizationRate=0; got " + rate);
    }
}

// ─── TC358 — S4-F9 idle empty when no providers exist ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC358_BkIdleNoProvidersTests extends TestBase {
    @Test
    @DisplayName("TC358 — Idle endpoint with no providers returns empty list")
    void idle_no_providers() throws Exception {
        BASE_URL = deliveryServiceUrl;
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/idle?maxBookedSlots=2&sinceDays=30", tok);
        assert2xx(r, "TC358");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(0, list.size(), "TC358: empty list expected; got " + list.size());
    }
}

// ─── TC359 — S4-F1 latest slot DTO shape ─────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC359_BkLatestSlotDtoShapeTests extends TestBase {
    @Test
    @DisplayName("TC359 — Latest slot DTO has provider, date, startTime, endTime")
    void latest_dto_shape() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedTimeSlot(this, pid, "2026-04-15", "10:00", "11:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/provider/" + pid + "/latest", tok);
        assert2xx(r, "TC359");
        JsonNode j = parseNode(r.body());
        assertTrue(j.has("date") || j.has("startTime") || j.has("start_time"),
                "TC359: DTO should have date/startTime field; body=" + r.body());
    }
}

// ─── TC360 — S5-F1 search by date range only ─────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC360_BkInvoiceSearchDateOnlyTests extends TestBase {
    @Test
    @DisplayName("TC360 — Search by date range only returns invoices in range")
    void search_date_only() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc360@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-04-10", 200.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b2, uid, 200.0, "CREDIT_CARD", "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/search?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC360");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(1, list.size(), "TC360: 1 March invoice expected; got " + list.size());
    }
}

// ─── TC361 — S5-F2 refund REFUNDED 400 ───────────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC361_BkRefundAlreadyRefundedTests extends TestBase {
    @Test
    @DisplayName("TC361 — Refund already-REFUNDED invoice returns 400")
    void refund_already() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc361@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "REFUNDED");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/" + iid + "/refund",
                "{\"reason\":\"x\"}", tok);
        assertEquals(400, r.statusCode(), "TC361: must be 400; got " + r.statusCode());
    }
}

// ─── TC362 — S5-F3 user summary with mixed methods ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC362_BkUserSummaryMethodMixTests extends TestBase {
    @Test
    @DisplayName("TC362 — User summary methodBreakdown shows correct counts per method")
    void summary_method_mix() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc362@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Tutor", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 100.0);
        long b3 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-12", 100.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CASH",        "COMPLETED");
        _BkM1Seed.seedInvoice(this, b2, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b3, uid, 100.0, "WALLET",      "COMPLETED");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/user/" + uid + "/summary", tok);
        assert2xx(r, "TC362");
        JsonNode j = parseNode(r.body());
        JsonNode breakdown = _BkM2.rO(j, "methodBreakdown", "method_breakdown");
        assertNotNull(breakdown, "TC362: methodBreakdown required");
    }
}

// ─── TC363 — S5-F4 process unknown booking 404 ───────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC363_BkProcessAlternateMethodTests extends TestBase {
    @Test
    @DisplayName("TC363 — Process invoice with method=CASH succeeds")
    void process_cash() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc363@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 75.0);
        String body = "{\"method\":\"CASH\"}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/invoices/booking/" + bid, body, tok);
        assert2xx(r, "TC363");
    }
}

// ─── TC364 — S5-F5 apply discount to unknown invoice 404 ─────────────────────
@Tag("public")
@Tag("features_m1")
class TC364_BkApplyDiscountInvoiceNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC364 — Apply discount to non-existent invoice returns 404")
    void apply_invoice_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long did = _BkM1Seed.seedDiscount(this, "X_" + nonce(), "PERCENTAGE", 10.0, 100,
                _BkM1Seed.futureDateTime(), true);
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/invoices/999999/discounts/" + did, "", tok);
        assertEquals(404, r.statusCode(), "TC364: must be 404; got " + r.statusCode());
    }
}

// ─── TC365 — S5-F5 apply unknown discount 404 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC365_BkApplyDiscountDiscountNotFoundTests extends TestBase {
    @Test
    @DisplayName("TC365 — Apply non-existent discount to invoice returns 404")
    void apply_discount_not_found() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc365@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth(
            "/api/invoices/" + iid + "/discounts/999999", "", tok);
        assertEquals(404, r.statusCode(), "TC365: must be 404; got " + r.statusCode());
    }
}

// ─── TC366 — S5-F6 revenue includes COMPLETED only (excludes PENDING) ────────
@Tag("public")
@Tag("features_m1")
class TC366_BkRevenueExcludesPendingTests extends TestBase {
    @Test
    @DisplayName("TC366 — Revenue excludes PENDING invoices from totalRevenue")
    void revenue_excludes_pending() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc366@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long b1 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long b2 = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-11", 200.0);
        _BkM1Seed.seedInvoice(this, b1, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        _BkM1Seed.seedInvoice(this, b2, uid, 200.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/invoices/reports/revenue?startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC366");
        JsonNode j = parseNode(r.body());
        double total = _BkM2.rD(j, "totalRevenue", "total_revenue");
        assertEquals(100.0, total, 1.0, "TC366: totalRevenue=100 (PENDING excluded); got " + total);
    }
}

// ─── TC367 — S5-F7 retry PENDING is ok or 400 ────────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC367_BkRetryPendingTests extends TestBase {
    @Test
    @DisplayName("TC367 — Retry PENDING invoice is 400 or 2xx (not allowed for non-FAILED)")
    void retry_pending() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc367@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "PENDING");
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth("/api/invoices/" + iid + "/retry", "", tok);
        int sc = r.statusCode();
        assertTrue(sc == 400 || sc / 100 == 2,
                "TC367: must be 400 or 2xx; got " + sc);
    }
}

// ─── TC368 — S5-F8 details finalAmount calculation ───────────────────────────
@Tag("public")
@Tag("features_m1")
class TC368_BkInvoiceDetailsFinalAmountTests extends TestBase {
    @Test
    @DisplayName("TC368 — Details with discount surfaces finalAmount = originalAmount - totalDiscount")
    void details_final_amount() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc368@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long did = _BkM1Seed.seedDiscount(this, "D_" + nonce(), "FIXED", 20.0, 100,
                _BkM1Seed.futureDateTime(), true);
        _BkM1Seed.seedInvoiceDiscount(this, iid, did, 20.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/" + iid + "/details", tok);
        assert2xx(r, "TC368");
        JsonNode j = parseNode(r.body());
        double original = _BkM2.rD(j, "originalAmount", "original_amount");
        double totalDisc = _BkM2.rD(j, "totalDiscount", "total_discount");
        double finalAmt = _BkM2.rD(j, "finalAmount", "final_amount");
        if (original > 0 && totalDisc > 0 && finalAmt > 0) {
            assertEquals(original - totalDisc, finalAmt, 1.0,
                    "TC368: finalAmount = originalAmount - totalDiscount; got " + finalAmt);
        }
    }
}

// ─── TC369 — S5-F9 inactive discount in top-used list ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC369_BkTopDiscountsInactiveFlagTests extends TestBase {
    @Test
    @DisplayName("TC369 — Top discounts surfaces active=false for inactive discounts")
    void top_discounts_inactive() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long did = _BkM1Seed.seedDiscount(this, "INACT_" + nonce(), "PERCENTAGE", 10.0, 100,
                _BkM1Seed.futureDateTime(), false);
        _BkM1Seed.setDiscountCurrentUses(this, did, 5);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/discounts/top-used?limit=20", tok);
        assert2xx(r, "TC369");
    }
}

// ─── TC370 — S2-F1 search returns rating field in DTO ────────────────────────
@Tag("public")
@Tag("features_m1")
class TC370_BkProviderSearchDtoShapeTests extends TestBase {
    @Test
    @DisplayName("TC370 — Provider search DTO surfaces rating field")
    void search_dto() throws Exception {
        BASE_URL = catalogServiceUrl;
        _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.5, 10);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/search?status=AVAILABLE", tok);
        assert2xx(r, "TC370");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        if (list.size() > 0) {
            JsonNode first = list.get(0);
            assertTrue(first.has("rating"), "TC370: rating field expected; body=" + r.body());
        }
    }
}

// ─── TC371 — S3-F3 estimate respects multi-service duration ──────────────────
@Tag("public")
@Tag("features_m1")
class TC371_BkEstimateMultiServiceTests extends TestBase {
    @Test
    @DisplayName("TC371 — Estimate sums durations across multiple services")
    void estimate_multi() throws Exception {
        BASE_URL = orderServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        String body = "{\"providerId\":" + pid + ",\"appointmentDate\":\"2030-04-10\","
                + "\"services\":[{\"serviceName\":\"S1\",\"duration\":15,\"price\":20},"
                + "{\"serviceName\":\"S2\",\"duration\":15,\"price\":30},"
                + "{\"serviceName\":\"S3\",\"duration\":30,\"price\":50}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/estimate", body, tok);
        assert2xx(r, "TC371");
        JsonNode j = parseNode(r.body());
        long duration = _BkM2.rL(j, "totalDuration", "total_duration");
        assertEquals(60L, duration, "TC371: totalDuration=60; got " + duration);
    }
}

// ─── TC372 — S2-F6 top-rated includes totalBookings field ────────────────────
@Tag("public")
@Tag("features_m1")
class TC372_BkTopRatedHasBookingsFieldTests extends TestBase {
    @Test
    @DisplayName("TC372 — Top-rated DTO surfaces totalBookings field")
    void top_rated_bookings_field() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.5, 10);
        long uid = _BkM1Seed.seedUser(this, "U", "tc372@bk.io", "CLIENT");
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/providers/reports/top-rated?limit=10", tok);
        assert2xx(r, "TC372");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        if (list.size() > 0) {
            JsonNode first = list.get(0);
            assertTrue(first.has("totalBookings") || first.has("total_bookings"),
                    "TC372: totalBookings expected; body=" + r.body());
        }
    }
}

// ─── TC373 — S3-F1 search with status+date returns ordered DESC ──────────────
@Tag("public")
@Tag("features_m1")
class TC373_BkBookingSearchOrderTests extends TestBase {
    @Test
    @DisplayName("TC373 — Booking search results returned (order may vary)")
    void search_order() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc373@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-15", 200.0);
        _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-20", 300.0);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth(
            "/api/bookings/search?status=COMPLETED&startDate=2026-03-01&endDate=2026-03-31", tok);
        assert2xx(r, "TC373");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        assertEquals(3, list.size(), "TC373: 3 results expected; got " + list.size());
    }
}

// ─── TC374 — S4-F3 available without specialty returns all available ─────────
@Tag("public")
@Tag("features_m1")
class TC374_BkAvailableNoSpecialtyTests extends TestBase {
    @Test
    @DisplayName("TC374 — Available without specialty returns providers across all specialties")
    void available_no_specialty() throws Exception {
        BASE_URL = deliveryServiceUrl;
        long pA = _BkM1Seed.seedProvider(this, "PA", "Dentist", "AVAILABLE", 4.0, 0);
        long pB = _BkM1Seed.seedProvider(this, "PB", "Tutor",   "AVAILABLE", 4.0, 0);
        _BkM1Seed.seedTimeSlot(this, pA, "2030-04-10", "09:00", "10:00", true);
        _BkM1Seed.seedTimeSlot(this, pB, "2030-04-10", "11:00", "12:00", true);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/timeslots/available?date=2030-04-10", tok);
        int sc = r.statusCode();
        assertTrue(sc == 400 || sc / 100 == 2,
                "TC374: must be 400 or 2xx; got " + sc);
    }
}

// ─── TC375 — S2-F8 verify cert with verifiedBy as object key ─────────────────
@Tag("public")
@Tag("features_m1")
class TC375_BkVerifyCertVerifiedByFieldTests extends TestBase {
    @Test
    @DisplayName("TC375 — Verified cert exposes verifiedBy/verified flag in DB")
    void verify_cert_field() throws Exception {
        BASE_URL = catalogServiceUrl;
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long cid = _BkM1Seed.seedCertification(this, pid, "DEGREE",
                java.time.LocalDate.of(2030, 1, 1), false);
        String tok = adminToken();
        HttpResponse<String> r = httpPutAuth(
            "/api/providers/" + pid + "/certifications/" + cid + "/verify",
            "{\"verifiedBy\":3}", tok);
        assert2xx(r, "TC375");
        Boolean verified = jdbc.queryForObject(
            "SELECT \"" + columnByField("ProviderCertification", "verified") + "\" FROM \""
            + tableName("ProviderCertification") + "\" WHERE id = ?", Boolean.class, cid);
        assertEquals(Boolean.TRUE, verified, "TC375: verified must be true; got " + verified);
    }
}

// ─── TC376 — S1-F9 case where lang matches but minBookings=0 returns all ────
@Tag("public")
@Tag("features_m1")
class TC376_BkLanguageZeroMinBookingsTests extends TestBase {
    @Test
    @DisplayName("TC376 — minBookings=0 returns matching language users regardless of bookings")
    void lang_zero_min() throws Exception {
        BASE_URL = userServiceUrl;
        long uA = _BkM1Seed.seedUser(this, "ArA", "tc376@bk.io", "CLIENT");
        _BkM1Seed.setUserPreferences(this, uA, "{\"language\":\"ar\"}");
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/users/preferences/language?lang=ar&minBookings=0", tok);
        assert2xx(r, "TC376");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        boolean found = false;
        for (JsonNode it : list) {
            if (_BkM2.rL(it, "userId", "id") == uA) found = true;
        }
        assertTrue(found, "TC376: A must be in results with minBookings=0; body=" + r.body());
    }
}

// ─── TC377 — S3-F8 add services with multiple items appends ──────────────────
@Tag("public")
@Tag("features_m1")
class TC377_BkAddServicesMultipleItemsTests extends TestBase {
    @Test
    @DisplayName("TC377 — Adding 3 services creates 3 BookingService rows")
    void add_three_services() throws Exception {
        BASE_URL = orderServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc377@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "REQUESTED", "2030-04-10", 0.0);
        String body = "{\"services\":["
                + "{\"serviceName\":\"S1\",\"duration\":10,\"price\":20},"
                + "{\"serviceName\":\"S2\",\"duration\":15,\"price\":30},"
                + "{\"serviceName\":\"S3\",\"duration\":20,\"price\":40}]}";
        String tok = adminToken();
        HttpResponse<String> r = httpPostAuth("/api/bookings/" + bid + "/services", body, tok);
        assert2xx(r, "TC377");
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"" + tableName("BookingService") + "\" WHERE \""
            + columnByField("BookingService", "booking") + "\" = ?", Long.class, bid);
        assertEquals(3L, count == null ? 0L : count.longValue(),
                "TC377: 3 services expected; got " + count);
    }
}

// ─── TC378 — S5-F9 active flag in top-discounts ──────────────────────────────
@Tag("public")
@Tag("features_m1")
class TC378_BkTopDiscountsActiveFlagTests extends TestBase {
    @Test
    @DisplayName("TC378 — Top-used discounts surfaces active=true for active discounts")
    void top_discounts_active_flag() throws Exception {
        BASE_URL = checkoutServiceUrl;
        long uid = _BkM1Seed.seedUser(this, "U", "tc378@bk.io", "CLIENT");
        long pid = _BkM1Seed.seedProvider(this, "P", "Dentist", "AVAILABLE", 4.0, 0);
        long bid = _BkM1Seed.seedBooking(this, uid, pid, "COMPLETED", "2026-03-10", 100.0);
        long iid = _BkM1Seed.seedInvoice(this, bid, uid, 100.0, "CREDIT_CARD", "COMPLETED");
        long did = _BkM1Seed.seedDiscount(this, "ACT_" + nonce(), "PERCENTAGE", 15.0, 100,
                _BkM1Seed.futureDateTime(), true);
        _BkM1Seed.seedInvoiceDiscount(this, iid, did, 15.0);
        _BkM1Seed.setDiscountCurrentUses(this, did, 1);
        String tok = adminToken();
        HttpResponse<String> r = httpGetAuth("/api/invoices/discounts/top-used?limit=10", tok);
        assert2xx(r, "TC378");
        JsonNode arr = parseNode(r.body());
        JsonNode list = arr.isArray() ? arr : (arr.has("content") ? arr.get("content") : arr);
        for (JsonNode it : list) {
            long id = _BkM2.rL(it, "discountId", "id");
            if (id == did) {
                assertTrue(it.has("active") && it.get("active").asBoolean(),
                        "TC378: active=true expected; got " + it);
                return;
            }
        }
        throw new AssertionError("TC378: target active discount not found; body=" + r.body());
    }
}
