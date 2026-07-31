package io.learnaws.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

/**
 * Requires Docker. Run with: mvn test -Pfloci -pl modules/11-testing-ci
 */
@Tag("floci")
@Testcontainers
class CloudWatchLogShipperIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void shippedErrorEventsAreQueryableByFilterPattern() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());
        CloudWatchLogsClient logs = endpoint.configure(CloudWatchLogsClient.builder());

        String logGroup = "/learnaws/test";
        String logStream = "shipper-test";
        CloudWatchLogShipper.ensureLogGroupAndStream(logs, logGroup, logStream);

        StructuredLogger logger = new StructuredLogger("shipper-test");
        StructuredLogEvent info = logger.info("all good", Map.of("taskId", "t1"));
        StructuredLogEvent error = logger.error("something broke", Map.of("taskId", "t2"));

        CloudWatchLogShipper.ship(logs, logGroup, logStream, List.of(info, error));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<String> errorMessages = CloudWatchLogShipper.query(logs, logGroup, "{ $.level = \"ERROR\" }");
            assertThat(errorMessages).hasSize(1);
            assertThat(errorMessages.get(0)).contains("something broke").contains("\"taskId\":\"t2\"");
        });
    }
}
