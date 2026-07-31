package io.learnaws.rds;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.learnaws.foundations.FlociEndpoint;
import io.learnaws.rds.RdsProvisioner.ConnectionInfo;
import software.amazon.awssdk.services.rds.RdsClient;

/**
 * Run with: mvn -pl modules/07-rds spring-boot:run
 *
 * Provisions the RDS Postgres instance (Docker-backed, may take a minute the first time
 * while Floci pulls postgres:16-alpine) BEFORE Spring's ApplicationContext starts, since
 * the JDBC host/port aren't known until the instance is available. System properties set
 * here land in Spring's environment ahead of application.yml, which deliberately has no
 * spring.datasource.* of its own.
 */
@SpringBootApplication
public class RdsUsersApplication {

    public static void main(String[] args) {
        try (RdsClient rds = FlociEndpoint.local().configure(RdsClient.builder())) {
            ConnectionInfo db = RdsProvisioner.provision(rds);
            System.out.println("RDS instance available at " + db.host() + ":" + db.port());

            System.setProperty("spring.datasource.url", db.jdbcUrl());
            System.setProperty("spring.datasource.username", db.username());
            System.setProperty("spring.datasource.password", db.password());
        }

        SpringApplication.run(RdsUsersApplication.class, args);
    }
}
