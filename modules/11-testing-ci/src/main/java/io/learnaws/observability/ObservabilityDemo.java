package io.learnaws.observability;

import java.util.List;
import java.util.Map;

import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

/**
 * Run with: mvn -pl modules/11-testing-ci compile exec:java
 */
public final class ObservabilityDemo {

    private static final String LOG_GROUP = "/learnaws/task-tracker";
    private static final String LOG_STREAM = "observability-demo";

    public static void main(String[] args) {
        StructuredLogger logger = new StructuredLogger("observability-demo");

        StructuredLogEvent ok = logger.info("task created", Map.of("taskId", "t1", "owner", "alice"));
        StructuredLogEvent failure = logger.error("dynamodb write failed", Map.of("taskId", "t2", "retryable", true));

        try (CloudWatchLogsClient logs = FlociEndpoint.local().configure(CloudWatchLogsClient.builder())) {
            CloudWatchLogShipper.ensureLogGroupAndStream(logs, LOG_GROUP, LOG_STREAM);
            CloudWatchLogShipper.ship(logs, LOG_GROUP, LOG_STREAM, List.of(ok, failure));

            List<String> errors = CloudWatchLogShipper.query(logs, LOG_GROUP, "{ $.level = \"ERROR\" }");
            System.out.println("Queried " + errors.size() + " ERROR-level event(s) back from CloudWatch Logs:");
            errors.forEach(System.out::println);
        }
    }
}
