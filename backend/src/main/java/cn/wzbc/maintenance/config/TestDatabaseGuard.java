package cn.wzbc.maintenance.config;

import java.nio.file.Path;
import java.util.Set;
import java.util.Arrays;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

@Configuration(proxyBeanMethods = false)
public final class TestDatabaseGuard {
    public TestDatabaseGuard() {}

    public static void validateConfiguration(Set<String> profiles, String url, String username,
                                             Path projectRoot, Path attachmentRoot) {
        if (profiles.size() != 1 || !Set.of("dev", "demo", "test").containsAll(profiles)) {
            throw rejected();
        }
        String profile = profiles.iterator().next();
        String prefix = "jdbc:mysql://127.0.0.1:3307/campus_maintenance_" + profile;
        if (!(url.equals(prefix) || url.startsWith(prefix + "?")) || !username.equals("campus_" + profile)) {
            throw rejected();
        }
        try {
            Path root = projectRoot.toRealPath();
            Path expected = root.resolve("data/attachments/" + profile);
            if (!projectRoot.isAbsolute() || !attachmentRoot.isAbsolute()
                    || !attachmentRoot.normalize().equals(projectRoot.normalize().resolve("data/attachments/" + profile))
                    || !java.nio.file.Files.isDirectory(attachmentRoot)
                    || !attachmentRoot.toRealPath().equals(expected)) {
                throw rejected();
            }
        } catch (IOException e) {
            throw rejected();
        }
    }

    public static void validateConnection(Connection connection, String profile) throws SQLException {
        String database = "campus_maintenance_" + profile;
        String user = "campus_" + profile + "@127.0.0.1";
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT VERSION(), DATABASE(), CURRENT_USER(), @@session.time_zone")) {
            if (!result.next() || !result.getString(1).startsWith("8.4.")
                    || !database.equals(result.getString(2)) || !user.equals(result.getString(3))
                    || !"+00:00".equals(result.getString(4))) {
                throw rejected();
            }
        }
        Set<String> required = Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "REFERENCES", "INDEX", "ALTER");
        boolean databaseGrant = false;
        try (var statement = connection.createStatement(); var grants = statement.executeQuery("SHOW GRANTS")) {
            while (grants.next()) {
                String grant = grants.getString(1);
                if (grant.startsWith("GRANT USAGE ON *.* TO ") && !grant.contains("WITH GRANT OPTION")) continue;
                String marker = " ON `" + database + "`.* TO ";
                int end = grant.indexOf(marker);
                if (!grant.startsWith("GRANT ") || end < 0 || grant.contains("WITH GRANT OPTION")) throw rejected();
                Set<String> privileges = Set.copyOf(Arrays.asList(grant.substring(6, end).split(", ")));
                if (!privileges.equals(required)) throw rejected();
                databaseGrant = true;
            }
        }
        if (!databaseGrant) throw rejected();
    }

    @Bean
    FlywayMigrationStrategy guardedMigration(Environment environment) {
        Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
        validateConfiguration(profiles, environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.datasource.username"),
                Path.of(environment.getRequiredProperty("campus.project-root")),
                Path.of(environment.getRequiredProperty("campus.attachment-root")));
        String profile = profiles.iterator().next();
        return flyway -> {
            try (Connection connection = flyway.getConfiguration().getDataSource().getConnection()) {
                validateConnection(connection, profile);
            } catch (SQLException e) {
                throw new IllegalStateException("数据库隔离身份验证失败，已停止迁移");
            }
            flyway.migrate();
        };
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("数据库或附件隔离配置不符合当前profile，已停止启动");
    }
}
