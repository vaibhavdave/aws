package io.learnaws.cdk;

import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

/**
 * Synthesizes the stack to an in-memory CloudFormation template and asserts on its shape -
 * no AWS calls, no Docker, no Floci, and no `cdk` CLI needed for this. It DOES need
 * target/task-tracker-lambda.jar to already exist (Code.fromAsset reads it at synth time),
 * which is why this runs as a failsafe IT (after `package`) rather than a surefire test.
 *
 * Run with: mvn -pl modules/10-cdk-iac -am verify
 */
class TaskTrackerStackIT {

    @Test
    void synthesizesTheExpectedTableIndexFunctionAndApi() {
        App app = new App();
        TaskTrackerStack stack = new TaskTrackerStack(app, "TestStack", StackProps.builder().build(),
                "target/task-tracker-lambda.jar");

        Template template = Template.fromStack(stack);

        template.hasResourceProperties("AWS::DynamoDB::Table", Map.of(
                "TableName", "task-tracker",
                "BillingMode", "PAY_PER_REQUEST",
                "StreamSpecification", Map.of("StreamViewType", "NEW_AND_OLD_IMAGES")));

        template.hasResourceProperties("AWS::DynamoDB::Table", Map.of(
                "GlobalSecondaryIndexes", Match.arrayWith(java.util.List.of(
                        Match.objectLike(Map.of("IndexName", "owner-index"))))));

        template.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Handler", "io.learnaws.api.TaskApiHandler::handleRequest",
                "Runtime", "java21"));

        template.resourceCountIs("AWS::ApiGateway::RestApi", 1);

        // The proxy(true) greedy resource: a single {proxy+} resource capturing every path.
        template.hasResourceProperties("AWS::ApiGateway::Resource", Map.of("PathPart", "{proxy+}"));

        // grantReadWriteData should have produced an IAM policy scoped to this table (and
        // its indexes), not "Resource": "*" - the same intent as Module 01's hand-written
        // policy, generated instead of typed out.
        template.hasResourceProperties("AWS::IAM::Policy", Map.of(
                "PolicyDocument", Match.objectLike(Map.of(
                        "Statement", Match.arrayWith(java.util.List.of(
                                Match.objectLike(Map.of(
                                        "Action", Match.arrayWith(java.util.List.of("dynamodb:GetItem")))))))))
        );
    }
}
