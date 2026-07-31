package io.learnaws.cloudmart;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;

/** Wires the order-processing queue to invoke OrderProcessorHandler automatically. */
public final class CloudMartEventSourceWiring {

    private CloudMartEventSourceWiring() {
    }

    public static void wireQueueToFunction(LambdaClient lambda, String queueArn, String functionArn) {
        try {
            lambda.createEventSourceMapping(b -> b
                    .eventSourceArn(queueArn)
                    .functionName(functionArn)
                    .batchSize(10)
                    .enabled(true));
        } catch (ResourceConflictException alreadyMapped) {
            // wired on a previous run
        }
    }
}
