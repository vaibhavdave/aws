package io.learnaws.messaging;

import java.util.Map;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

public class TaskEventPublisher {

    private final SnsClient sns;
    private final String topicArn;

    public TaskEventPublisher(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    /**
     * The "status" message attribute is what the notification queue's FilterPolicy
     * matches against - SNS filters on attributes, not on the message body.
     */
    public void publish(TaskEvent event) {
        sns.publish(b -> b
                .topicArn(topicArn)
                .message(TaskEventCodec.toJson(event))
                .messageAttributes(Map.of(
                        "status", MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(event.status().name())
                                .build())));
    }
}
