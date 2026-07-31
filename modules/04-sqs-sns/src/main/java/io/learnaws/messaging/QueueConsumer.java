package io.learnaws.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * Polls a queue and hands each message to {@code handler}. A message is only deleted if the
 * handler returns normally; on failure it's left alone, so SQS makes it visible again after
 * the queue's visibility timeout, and eventually routes it to the queue's dead-letter queue
 * once its RedrivePolicy's maxReceiveCount is exceeded - no special DLQ code needed here.
 */
public class QueueConsumer {

    private final SqsClient sqs;
    private final String queueUrl;

    public QueueConsumer(SqsClient sqs, String queueUrl) {
        this.sqs = sqs;
        this.queueUrl = queueUrl;
    }

    public List<TaskEvent> poll(int maxMessages, Duration waitTime, Consumer<TaskEvent> handler) {
        ReceiveMessageResponse response = sqs.receiveMessage(b -> b
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .waitTimeSeconds((int) waitTime.toSeconds()));

        List<TaskEvent> processed = new ArrayList<>();
        for (Message message : response.messages()) {
            TaskEvent event = TaskEventCodec.fromJson(message.body());
            try {
                handler.accept(event);
                sqs.deleteMessage(b -> b.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
                processed.add(event);
            } catch (RuntimeException handlerFailed) {
                // leave it in the queue - see class Javadoc
            }
        }
        return processed;
    }
}
