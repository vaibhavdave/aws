package io.learnaws.rds;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.learnaws.foundations.FlociEndpoint;
import io.learnaws.rds.RdsProvisioner.ConnectionInfo;
import software.amazon.awssdk.services.rds.RdsClient;

/**
 * Provisions a real Postgres instance via Floci's Docker-backed RDS and runs the actual
 * Flyway migration + a JDBC round-trip against it. Requires Docker.
 *
 * Like Modules 05/06's deployment tests, this targets the docker-compose Floci instance
 * directly rather than an ephemeral Testcontainers one, since the RDS container Floci
 * launches needs a predictable route back to the host for JDBC to reach it.
 *
 * Run with: docker compose up -d, then mvn test -Pfloci -pl modules/07-rds
 * (RDS provisioning can take a minute the first time - pulling postgres:16-alpine.)
 */
@Tag("floci")
class RdsIntegrationTest {

    @Test
    void flywayMigratesAndTheUsersTableAcceptsARoundTrip() throws Exception {
        try (RdsClient rds = FlociEndpoint.local().configure(RdsClient.builder())) {
            ConnectionInfo db = RdsProvisioner.provision(rds);

            Flyway.configure()
                    .dataSource(db.jdbcUrl(), db.username(), db.password())
                    .load()
                    .migrate();

            UUID id = UUID.randomUUID();
            try (Connection connection = DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password())) {
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO users (id, username, email, created_at) VALUES (?, ?, ?, ?)")) {
                    insert.setObject(1, id);
                    insert.setString(2, "alice");
                    insert.setString(3, "alice@example.com");
                    insert.setObject(4, java.sql.Timestamp.from(Instant.now()));
                    insert.executeUpdate();
                }

                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT username, email FROM users WHERE id = ?")) {
                    select.setObject(1, id);
                    try (ResultSet resultSet = select.executeQuery()) {
                        assertThat(resultSet.next()).isTrue();
                        assertThat(resultSet.getString("username")).isEqualTo("alice");
                        assertThat(resultSet.getString("email")).isEqualTo("alice@example.com");
                    }
                }
            }
        }
    }
}
