package io.learnaws.rds;

import software.amazon.awssdk.services.rds.RdsClient;
import software.amazon.awssdk.services.rds.model.DbInstanceNotFoundException;
import software.amazon.awssdk.services.rds.model.DBInstance;

/**
 * Provisions a real Postgres instance - Floci runs RDS as an actual Docker container
 * (postgres:16-alpine by default), not an in-process emulation, so this is closer to
 * integration-testing real Postgres than mocking it ever would be.
 */
public final class RdsProvisioner {

    public static final String INSTANCE_ID = "task-tracker-db";
    private static final String DATABASE_NAME = "tasktracker";
    private static final String USERNAME = "tasktracker";
    private static final String PASSWORD = "tasktracker-demo-password";

    private RdsProvisioner() {
    }

    public record ConnectionInfo(String host, int port, String database, String username, String password) {
        public String jdbcUrl() {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
    }

    public static ConnectionInfo provision(RdsClient rds) {
        if (!instanceExists(rds)) {
            rds.createDBInstance(b -> b
                    .dbInstanceIdentifier(INSTANCE_ID)
                    .engine("postgres")
                    .dbInstanceClass("db.t3.micro")
                    .allocatedStorage(20)
                    .masterUsername(USERNAME)
                    .masterUserPassword(PASSWORD)
                    .dbName(DATABASE_NAME));
        }

        rds.waiter().waitUntilDBInstanceAvailable(b -> b.dbInstanceIdentifier(INSTANCE_ID));

        DBInstance instance = rds.describeDBInstances(b -> b.dbInstanceIdentifier(INSTANCE_ID))
                .dbInstances()
                .get(0);

        return new ConnectionInfo(
                instance.endpoint().address(), instance.endpoint().port(), DATABASE_NAME, USERNAME, PASSWORD);
    }

    private static boolean instanceExists(RdsClient rds) {
        try {
            rds.describeDBInstances(b -> b.dbInstanceIdentifier(INSTANCE_ID));
            return true;
        } catch (DbInstanceNotFoundException notFound) {
            return false;
        }
    }
}
