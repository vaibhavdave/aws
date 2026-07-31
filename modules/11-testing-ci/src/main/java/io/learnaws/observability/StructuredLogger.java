package io.learnaws.observability;

import java.time.Instant;
import java.util.Map;

/**
 * What "structured logging" actually means in Lambda/ECS/EC2: print JSON lines to stdout.
 * The platform (Lambda's runtime, the ECS/EC2 CloudWatch agent) captures stdout/stderr and
 * ships it to CloudWatch Logs automatically - your code never has to call PutLogEvents
 * itself. {@link CloudWatchLogShipper} exists only to make that automatic capture visible
 * and queryable locally against Floci, not as a pattern to copy into real handler code.
 */
public class StructuredLogger {

    private final String component;

    public StructuredLogger(String component) {
        this.component = component;
    }

    public StructuredLogEvent info(String message, Map<String, Object> fields) {
        return log("INFO", message, fields);
    }

    public StructuredLogEvent error(String message, Map<String, Object> fields) {
        return log("ERROR", message, fields);
    }

    private StructuredLogEvent log(String level, String message, Map<String, Object> fields) {
        StructuredLogEvent event = new StructuredLogEvent(
                Instant.now(), level, component, message, fields == null ? Map.of() : fields);
        System.out.println(event.toJson());
        return event;
    }
}
