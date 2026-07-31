package io.learnaws.cloudmart.cdk;

import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Match;
import software.amazon.awscdk.assertions.Template;

/**
 * Same reasoning as Module 10's TaskTrackerStackIT: pure in-process synthesis, needs
 * target/cloudmart-lambda.jar (hence a failsafe IT, run after `package`), no Docker.
 *
 * Run with: mvn -pl capstone-cloudmart -am verify
 */
class CloudMartStackIT {

    @Test
    void synthesizesTablesBucketTopicQueueAndBothFunctions() {
        App app = new App();
        CloudMartStack stack = new CloudMartStack(app, "TestStack", StackProps.builder().build(),
                "target/cloudmart-lambda.jar");

        Template template = Template.fromStack(stack);

        template.resourceCountIs("AWS::DynamoDB::Table", 2);
        template.hasResourceProperties("AWS::DynamoDB::Table", Map.of("TableName", "cloudmart-products"));
        template.hasResourceProperties("AWS::DynamoDB::Table", Map.of(
                "TableName", "cloudmart-orders",
                "GlobalSecondaryIndexes", Match.arrayWith(java.util.List.of(
                        Match.objectLike(Map.of("IndexName", "customer-index"))))));

        template.hasResourceProperties("AWS::S3::Bucket", Map.of("BucketName", "cloudmart-product-images"));
        template.hasResourceProperties("AWS::SNS::Topic", Map.of("TopicName", "cloudmart-order-events"));
        template.hasResourceProperties("AWS::SQS::Queue", Map.of("QueueName", "cloudmart-order-processing-queue"));
        template.resourceCountIs("AWS::SNS::Subscription", 1);

        template.resourceCountIs("AWS::Lambda::Function", 2);
        template.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Handler", "io.learnaws.cloudmart.api.CloudMartApiHandler::handleRequest"));
        template.hasResourceProperties("AWS::Lambda::Function", Map.of(
                "Handler", "io.learnaws.cloudmart.processing.OrderProcessorHandler::handleRequest"));

        // The SQS -> processor Lambda event source mapping.
        template.resourceCountIs("AWS::Lambda::EventSourceMapping", 1);

        template.resourceCountIs("AWS::ApiGateway::RestApi", 1);
        template.hasResourceProperties("AWS::ApiGateway::Resource", Map.of("PathPart", "{proxy+}"));
    }
}
