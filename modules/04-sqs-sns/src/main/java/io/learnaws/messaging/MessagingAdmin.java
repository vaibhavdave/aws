package io.learnaws.messaging;

import java.util.Map;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Provisions the "task-events" fan-out: one SNS topic, two SQS subscriber queues, and a
 * dead-letter queue for the one that's allowed to fail.
 *
 * - task-events-audit-queue: subscribed with no filter, receives every event.
 * - task-events-notification-queue: subscribed with a FilterPolicy so it only receives
 *   events where status = DONE, and a RedrivePolicy sending anything it fails to process
 *   three times to task-events-notification-dlq.
 */
public final class MessagingAdmin {

    public static final String TOPIC_NAME = "task-events";
    public static final String AUDIT_QUEUE = "task-events-audit-queue";
    public static final String NOTIFICATION_QUEUE = "task-events-notification-queue";
    public static final String NOTIFICATION_DLQ = "task-events-notification-dlq";

    private MessagingAdmin() {
    }

    public record Resources(String topicArn, String auditQueueUrl, String notificationQueueUrl, String notificationDlqUrl) {
    }

    public static Resources bootstrap(SnsClient sns, SqsClient sqs) {
        String topicArn = sns.createTopic(b -> b.name(TOPIC_NAME)).topicArn();

        String auditQueueUrl = sqs.createQueue(b -> b.queueName(AUDIT_QUEUE)).queueUrl();

        String dlqUrl = sqs.createQueue(b -> b.queueName(NOTIFICATION_DLQ)).queueUrl();
        String dlqArn = queueArn(sqs, dlqUrl);

        // Short visibility timeout so a failed message becomes redeliverable (and eventually
        // DLQ-routed) quickly enough to observe in a test/demo, not production-realistic tuning.
        String redrivePolicy = """
                {"deadLetterTargetArn":"%s","maxReceiveCount":"3"}""".formatted(dlqArn);
        String notificationQueueUrl = sqs.createQueue(b -> b
                        .queueName(NOTIFICATION_QUEUE)
                        .attributes(Map.of(
                                QueueAttributeName.REDRIVE_POLICY, redrivePolicy,
                                QueueAttributeName.VISIBILITY_TIMEOUT, "1")))
                .queueUrl();

        subscribeQueueToTopic(sns, sqs, topicArn, auditQueueUrl, null);
        subscribeQueueToTopic(sns, sqs, topicArn, notificationQueueUrl, """
                {"status":["DONE"]}""");

        return new Resources(topicArn, auditQueueUrl, notificationQueueUrl, dlqUrl);
    }

    private static String queueArn(SqsClient sqs, String queueUrl) {
        return sqs.getQueueAttributes(b -> b
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);
    }

    private static void subscribeQueueToTopic(
            SnsClient sns, SqsClient sqs, String topicArn, String queueUrl, String filterPolicyJson) {
        String queueArn = queueArn(sqs, queueUrl);

        // The resource policy real AWS requires so SNS is allowed to deliver into this queue.
        sqs.setQueueAttributes(b -> b
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, snsDeliveryPolicy(topicArn, queueArn))));

        String subscriptionArn = sns.subscribe(b -> b
                        .topicArn(topicArn)
                        .protocol("sqs")
                        .endpoint(queueArn)
                        .returnSubscriptionArn(true))
                .subscriptionArn();

        // Deliver the raw message body instead of wrapping it in the SNS envelope, so
        // consumers can parse a TaskEvent straight out of the SQS message body.
        sns.setSubscriptionAttributes(b -> b
                .subscriptionArn(subscriptionArn)
                .attributeName("RawMessageDelivery")
                .attributeValue("true"));

        if (filterPolicyJson != null) {
            sns.setSubscriptionAttributes(b -> b
                    .subscriptionArn(subscriptionArn)
                    .attributeName("FilterPolicy")
                    .attributeValue(filterPolicyJson));
        }
    }

    private static String snsDeliveryPolicy(String topicArn, String queueArn) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"Service": "sns.amazonaws.com"},
                    "Action": "sqs:SendMessage",
                    "Resource": "%s",
                    "Condition": {"ArnEquals": {"aws:SourceArn": "%s"}}
                  }]
                }""".formatted(queueArn, topicArn);
    }
}
