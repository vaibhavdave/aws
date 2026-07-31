package io.learnaws.cdk;

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
import software.amazon.awscdk.services.dynamodb.StreamViewType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.constructs.Construct;

/**
 * Everything Module 06 built by hand (TaskTableAdmin's raw CreateTable call,
 * ApiIamBootstrap's JSON policy files, ApiGatewayAdmin's resource-by-resource REST API) -
 * expressed declaratively instead. Compare this file's line count to Module 06's
 * ApiGatewayAdmin + ApiIamBootstrap + the two policy JSON files combined.
 */
public class TaskTrackerStack extends Stack {

    public TaskTrackerStack(Construct scope, String id, StackProps props, String lambdaJarPath) {
        super(scope, id, props);

        Table taskTable = Table.Builder.create(this, "TaskTrackerTable")
                .tableName("task-tracker")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .stream(StreamViewType.NEW_AND_OLD_IMAGES)
                .build();

        taskTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("owner-index")
                .partitionKey(Attribute.builder().name("owner").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("createdAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        Function apiFunction = Function.Builder.create(this, "TaskApiFunction")
                .runtime(Runtime.JAVA_21)
                .handler("io.learnaws.api.TaskApiHandler::handleRequest")
                .code(Code.fromAsset(lambdaJarPath))
                .memorySize(512)
                .timeout(Duration.seconds(15))
                .environment(Map.of("AWS_ENDPOINT_URL", "http://floci:4566"))
                .build();

        // One line replaces Module 01's hand-authored least-privilege policy JSON: CDK
        // inspects what grantReadWriteData actually needs and generates the IAM policy for
        // exactly this table (and its indexes) and this function, no ARNs typed by hand.
        taskTable.grantReadWriteData(apiFunction);

        // proxy(true) creates a single greedy {proxy+} resource forwarding every path/method
        // to the Lambda, instead of Module 06's ApiGatewayAdmin building /tasks and
        // /tasks/{id} one putMethod/putIntegration call at a time.
        LambdaRestApi api = LambdaRestApi.Builder.create(this, "TaskTrackerApi")
                .handler(apiFunction)
                .proxy(true)
                .build();

        CfnOutput.Builder.create(this, "ApiUrl").value(api.getUrl()).build();
    }
}
