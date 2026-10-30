package cn.wzbc.maintenance.common;

import cn.wzbc.maintenance.config.TestDatabaseGuard;
import cn.wzbc.maintenance.Application;
import cn.wzbc.maintenance.security.SecurityErrorHandler;
import java.nio.file.Path;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(classes = {Application.class, BootstrapIT.TestEndpoints.class, BootstrapIT.TestWebConfig.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class BootstrapIT {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final String TEST_URL = "jdbc:mysql://127.0.0.1:3307/campus_maintenance_test?connectionTimeZone=UTC";
    @Autowired DataSource datasource;
    @Autowired Flyway flyway;
    @Autowired Clock clock;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;

    @Test void target_jdk_is_21() { assertEquals(21, Runtime.version().feature()); }

    @Test void rejects_wrong_database_before_migration() {
        assertThrows(IllegalStateException.class, () -> check(Set.of("test"), TEST_URL.replace("_test", "_dev"), "campus_test", ROOT.resolve("data/attachments/test")));
    }
    @Test void rejects_wrong_profile_and_user() {
        assertThrows(IllegalStateException.class, () -> check(Set.of("dev", "test"), TEST_URL, "campus_test", ROOT.resolve("data/attachments/test")));
        assertThrows(IllegalStateException.class, () -> check(Set.of("test"), TEST_URL, "campus_dev", ROOT.resolve("data/attachments/test")));
    }
    @Test void rejects_unsafe_attachment_directory() {
        for (String path : new String[]{"data/attachments", "data/attachments/dev", "data/attachments/test-sibling", "data/attachments/test/../../dev"}) {
            assertThrows(IllegalStateException.class, () -> check(Set.of("test"), TEST_URL, "campus_test", ROOT.resolve(path)));
        }
    }
    @Test void accepts_exact_test_configuration() {
        assertDoesNotThrow(() -> check(Set.of("test"), TEST_URL, "campus_test", ROOT.resolve("data/attachments/test")));
    }
    private void check(Set<String> profiles, String url, String user, Path attachments) {
        TestDatabaseGuard.validateConfiguration(profiles, url, user, ROOT, attachments);
    }

    @Test void actual_mysql_identity_and_v001_are_verified() throws Exception {
        try (var connection = datasource.getConnection()) {
            TestDatabaseGuard.validateConnection(connection, "test");
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name<>'flyway_schema_history'")) {
                var tables = new java.util.HashSet<String>();
                while (result.next()) tables.add(result.getString(1));
                assertEquals(Set.of("user_account", "login_session", "registration_review", "account_event",
                        "campus_building", "campus_floor", "campus_location", "equipment", "equipment_event",
                        "equipment_location_history", "location_name_history", "campus_initialization",
                        "attachment_file", "repair_request", "repair_request_submission", "repair_request_event",
                        "work_order", "work_order_assignment", "repair_round", "repair_progress", "work_order_event"), tables);
            }
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM user_account")) {
                assertTrue(result.next()); assertEquals(0, result.getInt(1));
            }
        }
        assertEquals(6, flyway.info().applied().length);
        assertEquals("001", flyway.info().applied()[0].getVersion().getVersion());
    }

    @Test void repeat_migration_changes_nothing() {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals(6, flyway.info().applied().length);
    }

    @Test void wrong_runtime_config_stops_before_migration() {
        var application = new SpringApplication(Application.class);
        application.setLogStartupInfo(false);
        Exception error = assertThrows(Exception.class, () -> application.run("--server.port=0",
                "--spring.datasource.url=" + TEST_URL.replace("_test", "_dev")));
        assertTrue(causes(error).contains("隔离配置"));
        assertEquals(6, flyway.info().applied().length);
    }

    @Test void failed_sql_migration_stops_startup_and_only_its_failure_marker_is_removed() throws Exception {
        // 只在守卫已通过的test目录创建临时无DDL失败SQL；不调用clean或DROP。
        Path fixture = createMigrationFixture(ROOT.resolve("data/attachments/test/b01-migration-check-" + java.util.UUID.randomUUID()));
        Path script = fixture.resolve("R__b01_expected_failure.sql");
        assertFalse(Files.exists(script));
        try (var connection = datasource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE script='R__b01_expected_failure.sql'")) {
            TestDatabaseGuard.validateConnection(connection, "test");
            assertTrue(result.next()); assertEquals(0, result.getInt(1));
        }
        validateMigrationFixture(fixture);
        Files.writeString(script, "B01_INTENTIONALLY_INVALID_SQL;", java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        try {
            var application = new SpringApplication(Application.class);
            application.setLogStartupInfo(false);
            Exception error = assertThrows(Exception.class, () -> application.run("--server.port=0",
                    "--spring.flyway.locations=classpath:db/migration,filesystem:" + fixture.toString().replace('\\', '/')));
            assertTrue(causes(error).contains("b01_expected_failure"));
        } finally {
            validateMigrationFixture(fixture);
            if (!Files.isRegularFile(script, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || !script.toRealPath().getParent().equals(fixture.toRealPath())) {
                throw new java.io.IOException("拒绝清理非本次创建的迁移测试文件");
            }
            try (var connection = datasource.getConnection()) {
                TestDatabaseGuard.validateConnection(connection, "test");
                try (var statement = connection.prepareStatement("DELETE FROM flyway_schema_history WHERE script=? AND success=0 AND version IS NULL")) {
                    statement.setString(1, "R__b01_expected_failure.sql");
                    assertEquals(1, statement.executeUpdate());
                }
            }
            Files.delete(script);
            Files.delete(fixture);
        }
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }

    @Test void real_http_security_errors_have_safe_json_and_request_id() throws Exception {
        // The default-deny probe must not change the running HTTP chain's CSRF repository.
        authenticated_identity_cannot_enter_unimplemented_business();
        var client = HttpClient.newHttpClient();
        for (String method : new String[]{"GET", "POST"}) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                    .method(method, HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(method.equals("GET") ? 401 : 403, response.statusCode());
            var body = mapper.readTree(response.body());
            assertEquals(method.equals("GET") ? "AUTH_REQUIRED" : "CSRF_INVALID", body.get("code").asString());
            assertTrue(body.get("data").isNull());
            assertEquals(response.headers().firstValue("X-Request-Id").orElseThrow(), body.get("requestId").asString());
            assertEquals("private, no-store", response.headers().firstValue("Cache-Control").orElseThrow());
            assertFalse(response.headers().firstValue("Set-Cookie").orElse("").contains("JSESSIONID"));
            assertFalse(response.body().contains("password"));
        }
    }

    @Test void authenticated_identity_cannot_enter_unimplemented_business() throws Exception {
        // csrf() changes CsrfFilter to an HttpSession repository in security-test 7.1.1.
        // Use this application's actual cookie/header instead, preserving the live chain.
        var response=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        String token=mapper.readTree(response.getContentAsString()).get("data").get("token").asString();
        mvc.perform(post("/api/work-orders/1/accept").with(user("test"))
                .cookie(response.getCookie("XSRF-TOKEN")).header("X-CSRF-TOKEN",token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void validation_business_and_unexpected_errors_share_response_shape() throws Exception {
        mvc.perform(post("/__test/validate").with(csrf()).contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED")).andExpect(jsonPath("$.errors[0].field").value("name"));
        mvc.perform(post("/__test/validate").with(csrf()).contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/__test/conflict")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        var result = mvc.perform(get("/__test/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR")).andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("private-path"));
        assertNotNull(result.getResponse().getHeader("X-Request-Id"));
        mvc.perform(get("/__test/ok")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("OK")).andExpect(jsonPath("$.data").value("ready"));
    }

    @Test void clocks_use_utc_and_mutable_clock_shares_time_across_zones() {
        assertEquals(ZoneOffset.UTC, clock.getZone());
        var initial = Instant.parse("2026-10-12T00:00:00Z");
        var mutable = new MutableClock(initial);
        var shanghai = mutable.withZone(java.time.ZoneId.of("Asia/Shanghai"));
        mutable.set(initial.plusSeconds(28800));
        assertEquals(initial.plusSeconds(28800), shanghai.instant());
    }

    @Test void unknown_json_fields_are_rejected() throws Exception {
        mvc.perform(post("/__test/validate").with(csrf()).contentType("application/json")
                .content("{\"name\":\"测试\",\"unexpectedField\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test void migration_fixture_rejects_preexisting_targets() throws Exception {
        Path existing = Files.createTempDirectory(ROOT.resolve("data/attachments/test"), "b01-preexisting-");
        try {
            assertThrows(java.io.IOException.class, () -> createMigrationFixture(existing));
        } finally {
            Files.delete(existing);
        }
    }

    private static Path createMigrationFixture(Path path) throws java.io.IOException {
        Path attachments = ROOT.resolve("data/attachments/test");
        TestDatabaseGuard.validateConfiguration(Set.of("test"), TEST_URL, "campus_test", ROOT, attachments);
        if (!path.toAbsolutePath().normalize().getParent().equals(attachments)) {
            throw new java.io.IOException("临时目录必须位于已确认的test附件根");
        }
        // createDirectory拒绝一切已有对象（包括普通目录、symbolic link和junction）。
        Files.createDirectory(path);
        validateMigrationFixture(path);
        return path;
    }

    private static void validateMigrationFixture(Path path) throws java.io.IOException {
        Path attachments = ROOT.resolve("data/attachments/test");
        if (!path.toAbsolutePath().normalize().getParent().equals(attachments)
                || !Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)
                || !path.toRealPath().getParent().equals(attachments.toRealPath())) {
            throw new java.io.IOException("拒绝访问或清理test根外的迁移测试目录");
        }
    }

    private static String causes(Throwable error) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) messages.append(cause.getMessage()).append('\n');
        return messages.toString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestWebConfig {
        @Bean @Order(0) SecurityFilterChain testChain(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
            return http.securityMatcher("/__test/**").exceptionHandling(config -> config.accessDeniedHandler(errors))
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
    }
    // 仅测试上下文显式导入，不进入生产组件扫描/打包。
    @TestConfiguration(proxyBeanMethods = false) @RestController
    static class TestEndpoints {
        record Input(@NotBlank(message = "姓名不能为空") String name) {}
        @PostMapping("/__test/validate") ApiResponse<String> validate(@Valid @RequestBody Input input, HttpServletRequest request) { return ApiResponse.success(input.name(), request); }
        @GetMapping("/__test/conflict") void conflict() { throw new BusinessException(HttpStatus.CONFLICT, "VERSION_CONFLICT", "请刷新后重试"); }
        @GetMapping("/__test/unexpected") void unexpected() { throw new IllegalStateException("private-path/password must never appear in response"); }
        @GetMapping("/__test/ok") ApiResponse<String> ok(HttpServletRequest request) { return ApiResponse.success("ready", request); }
    }
}

