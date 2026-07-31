package io.learnaws.lambda;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.GetFunctionRequest;
import software.amazon.awssdk.services.lambda.model.ResourceConflictException;
import software.amazon.awssdk.services.lambda.model.ResourceNotFoundException;
import software.amazon.awssdk.services.lambda.model.Runtime;

public final class LambdaDeployer {

    private LambdaDeployer() {
    }

    public static byte[] readJar(Path jarPath) {
        try {
            return Files.readAllBytes(jarPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Build the deployment jar first: mvn -pl modules/05-lambda package", e);
        }
    }

    /** Creates the function if it doesn't exist yet, otherwise updates its code + config. */
    public static String deployOrUpdate(
            LambdaClient lambda, String functionName, byte[] jarBytes, String roleArn, Map<String, String> environment) {

        boolean exists = functionExists(lambda, functionName);

        if (!exists) {
            lambda.createFunction(b -> b
                    .functionName(functionName)
                    .runtime(Runtime.JAVA21)
                    .handler("io.learnaws.lambda.FileMetadataHandler::handleRequest")
                    .role(roleArn)
                    .code(c -> c.zipFile(SdkBytes.fromByteArray(jarBytes)))
                    .environment(e -> e.variables(environment))
                    .timeout(15)
                    .memorySize(512));
        } else {
            lambda.updateFunctionCode(b -> b.functionName(functionName).zipFile(SdkBytes.fromByteArray(jarBytes)));
            lambda.updateFunctionConfiguration(b -> b.functionName(functionName).environment(e -> e.variables(environment)));
        }

        lambda.waiter().waitUntilFunctionActiveV2(b -> b.functionName(functionName));

        return lambda.getFunction(b -> b.functionName(functionName)).configuration().functionArn();
    }

    private static boolean functionExists(LambdaClient lambda, String functionName) {
        try {
            lambda.getFunction(GetFunctionRequest.builder().functionName(functionName).build());
            return true;
        } catch (ResourceNotFoundException notFound) {
            return false;
        } catch (ResourceConflictException inProgress) {
            return true;
        }
    }
}
