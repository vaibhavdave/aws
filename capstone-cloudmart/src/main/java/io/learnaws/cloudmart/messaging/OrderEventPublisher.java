package io.learnaws.cloudmart.messaging;

import software.amazon.awssdk.services.sns.SnsClient;

public class OrderEventPublisher {

    private final SnsClient sns;
    private final String topicArn;

    public OrderEventPublisher(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    public void publish(OrderEvent event) {
        sns.publish(b -> b.topicArn(topicArn).message(OrderEventCodec.toJson(event)));
    }
}
