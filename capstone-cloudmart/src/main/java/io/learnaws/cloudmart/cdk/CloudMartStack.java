package io.learnaws.cloudmart.cdk;

import java.util.Map;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigateway.LambdaRestApi;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.sns.Topic;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

/**
 * The declarative twin of CloudMartDemo's imperative bootstrap - same tables, bucket,
 * topic/queue, two functions, event source mapping, and API, expressed as one stack
 * instead of a dozen SDK calls. Compare its length to CloudMartDemo, CloudMartLambdaDeployer,
 * CloudMartApiGatewayAdmin, and CloudMartIamBootstrap combined - the same contrast Module 10
 * drew for the Task Tracker stack, now on a second, independent domain.
 */
public class CloudMartStack extends Stack {

    public CloudMartStack(Construct scope, String id, StackProps props, String lambdaJarPath) {
        super(scope, id, props);

        Table productsTable = Table.Builder.create(this, "ProductsTable")
                .tableName("cloudmart-products")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();

        Table ordersTable = Table.Builder.create(this, "OrdersTable")
                .tableName("cloudmart-orders")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();

        ordersTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("customer-index")
                .partitionKey(Attribute.builder().name("customerId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("createdAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        Bucket imagesBucket = Bucket.Builder.create(this, "ProductImagesBucket")
                .bucketName("cloudmart-product-images")
                .build();

        Topic orderEventsTopic = Topic.Builder.create(this, "OrderEventsTopic")
                .topicName("cloudmart-order-events")
                .build();

        Queue orderProcessingQueue = Queue.Builder.create(this, "OrderProcessingQueue")
                .queueName("cloudmart-order-processing-queue")
                .build();

        orderEventsTopic.addSubscription(new SqsSubscription(orderProcessingQueue));

        Function apiFunction = Function.Builder.create(this, "ApiFunction")
                .runtime(Runtime.JAVA_21)
                .handler("io.learnaws.cloudmart.api.CloudMartApiHandler::handleRequest")
                .code(Code.fromAsset(lambdaJarPath))
                .memorySize(512)
                .timeout(Duration.seconds(15))
                .environment(Map.of(
                        "AWS_ENDPOINT_URL", "http://floci:4566",
                        "ORDER_TOPIC_ARN", orderEventsTopic.getTopicArn()))
                .build();

        Function processorFunction = Function.Builder.create(this, "OrderProcessorFunction")
                .runtime(Runtime.JAVA_21)
                .handler("io.learnaws.cloudmart.processing.OrderProcessorHandler::handleRequest")
                .code(Code.fromAsset(lambdaJarPath))
                .memorySize(512)
                .timeout(Duration.seconds(15))
                .environment(Map.of("AWS_ENDPOINT_URL", "http://floci:4566"))
                .build();

        processorFunction.addEventSource(SqsEventSource.Builder.create(orderProcessingQueue).batchSize(10).build());

        productsTable.grantReadWriteData(apiFunction);
        ordersTable.grantReadWriteData(apiFunction);
        imagesBucket.grantReadWrite(apiFunction);
        orderEventsTopic.grantPublish(apiFunction);

        ordersTable.grantReadWriteData(processorFunction);
        orderProcessingQueue.grantConsumeMessages(processorFunction);

        LambdaRestApi api = LambdaRestApi.Builder.create(this, "CloudMartApi")
                .handler(apiFunction)
                .proxy(true)
                .build();

        CfnOutput.Builder.create(this, "ApiUrl").value(api.getUrl()).build();
    }
}
