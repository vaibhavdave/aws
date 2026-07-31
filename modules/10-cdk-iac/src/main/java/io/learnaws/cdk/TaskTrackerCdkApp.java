package io.learnaws.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

/**
 * The `cdk` CLI's entry point (see cdk.json). Run:
 *
 *   mvn -pl modules/10-cdk-iac -am package   (builds target/task-tracker-lambda.jar first)
 *   cd modules/10-cdk-iac
 *   cdk bootstrap aws://000000000000/us-east-1 --toolkit-stack-name CDKToolkit
 *   cdk deploy
 *   cdk destroy
 *
 * against a running `docker compose up -d` Floci instance, with AWS_ENDPOINT_URL,
 * AWS_ACCESS_KEY_ID=test, AWS_SECRET_ACCESS_KEY=test, and AWS_DEFAULT_REGION=us-east-1
 * exported so the CDK CLI's own AWS SDK calls (and the CloudFormation deployment they
 * trigger) land on Floci instead of real AWS.
 */
public final class TaskTrackerCdkApp {

    public static void main(String[] args) {
        App app = new App();

        new TaskTrackerStack(app, "TaskTrackerStack", StackProps.builder().build(),
                "target/task-tracker-lambda.jar");

        app.synth();
    }
}
