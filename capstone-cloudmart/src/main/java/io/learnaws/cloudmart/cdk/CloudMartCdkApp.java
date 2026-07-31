package io.learnaws.cloudmart.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

/**
 * The `cdk` CLI's entry point (see cdk.json, at the capstone module root). Run:
 *
 *   mvn -pl capstone-cloudmart -am package   (builds target/cloudmart-lambda.jar first)
 *   cd capstone-cloudmart
 *   cdk bootstrap aws://000000000000/us-east-1 --toolkit-stack-name CDKToolkit
 *   cdk deploy
 *   cdk destroy
 *
 * against a running `docker compose up -d` Floci instance, with AWS_ENDPOINT_URL,
 * AWS_ACCESS_KEY_ID=test, AWS_SECRET_ACCESS_KEY=test, AWS_DEFAULT_REGION=us-east-1 exported -
 * see Module 10's README for the full explanation of this workflow.
 */
public final class CloudMartCdkApp {

    public static void main(String[] args) {
        App app = new App();

        new CloudMartStack(app, "CloudMartStack", StackProps.builder().build(), "target/cloudmart-lambda.jar");

        app.synth();
    }
}
