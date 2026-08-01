package io.learnaws.observability;

import java.util.List;

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResourceAlreadyExistsException;

/**
 * Stands in for what the CloudWatch Logs agent does automatically in real Lambda/ECS/EC2:
 * ships structured log lines into a log group/stream, then queries them back with a filter
 * pattern - the same mechanic CloudWatch Logs Insights queries use in production.
 */
public final class CloudWatchLogShipper {

    private CloudWatchLogShipper() {
    }

    public static void ensureLogGroupAndStream(CloudWatchLogsClient logs, String logGroup, String logStream) {
        try {
            logs.createLogGroup(b -> b.logGroupName(logGroup));
        } catch (ResourceAlreadyExistsException alreadyExists) {
            // fine
        }
        try {
            logs.createLogStream(b -> b.logGroupName(logGroup).logStreamName(logStream));
        } catch (ResourceAlreadyExistsException alreadyExists) {
            // fine
        }
    }

    public static void ship(CloudWatchLogsClient logs, String logGroup, String logStream, List<StructuredLogEvent> events) {
        logs.putLogEvents(b -> b
                .logGroupName(logGroup)
                .logStreamName(logStream)
                .logEvents(events.stream()
                        .map(e -> InputLogEvent.builder()
                                .timestamp(e.timestamp().toEpochMilli())
                                .message(e.toJson())
                                .build())
                        .toList()));
    }

    /**
     * Real AWS's filterPattern syntax supports JSON metric-filter expressions like
     * {@code { $.level = "ERROR" }}; Floci only matches filterPattern as a plain substring
     * of the message, so callers targeting Floci should pass a literal substring instead.
     */
    public static List<String> query(CloudWatchLogsClient logs, String logGroup, String filterPattern) {
        List<FilteredLogEvent> events = logs.filterLogEvents(b -> b
                        .logGroupName(logGroup)
                        .filterPattern(filterPattern))
                .events();

        return events.stream().map(FilteredLogEvent::message).toList();
    }
}
