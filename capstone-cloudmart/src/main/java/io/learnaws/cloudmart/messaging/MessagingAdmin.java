package io.learnaws.cloudmart.messaging;

import java.util.Map;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * A single-purpose version of Module 04's fan-out: one topic, one subscriber queue that
 * the order-processing Lambda consumes from (via an event source mapping - see
 * CloudMartAdmin/CloudMartStack). No filter policy here since there's only one consumer.
 */
public final class MessagingAdmin {

    public static final String TOPIC_NAME = "cloudmart-order-events";
    public static final String QUEUE_NAME = "cloudmart-order-processing-queue";

    private MessagingAdmin() {
    }

    public record Resources(String topicArn, String queueUrl, String queueArn) {
    }

    public static Resources bootstrap(SnsClient sns, SqsClient sqs) {
        String topicArn = sns.createTopic(b -> b.name(TOPIC_NAME)).topicArn();
        String queueUrl = sqs.createQueue(b -> b.queueName(QUEUE_NAME)).queueUrl();
        String queueArn = sqs.getQueueAttributes(b -> b
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes()
                .get(QueueAttributeName.QUEUE_ARN);

        sqs.setQueueAttributes(b -> b
                .queueUrl(queueUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, snsDeliveryPolicy(topicArn, queueArn))));

        String subscriptionArn = sns.subscribe(b -> b
                        .topicArn(topicArn)
                        .protocol("sqs")
                        .endpoint(queueArn)
                        .returnSubscriptionArn(true))
                .subscriptionArn();

        sns.setSubscriptionAttributes(b -> b
                .subscriptionArn(subscriptionArn)
                .attributeName("RawMessageDelivery")
                .attributeValue("true"));

        return new Resources(topicArn, queueUrl, queueArn);
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
