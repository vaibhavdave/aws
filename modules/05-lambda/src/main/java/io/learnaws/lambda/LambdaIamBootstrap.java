package io.learnaws.lambda;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;

/**
 * Unlike Module 01's task-tracker-app-role (trusted by the account root), this role is
 * trusted by the lambda.amazonaws.com service principal - the variant Module 01 flagged as
 * "we'll get to this in Module 05".
 */
public final class LambdaIamBootstrap {

    public static final String ROLE_NAME = "file-metadata-lambda-role";
    public static final String POLICY_NAME = "file-metadata-lambda-policy";
    private static final String DEFAULT_ACCOUNT_ID = "000000000000";

    private LambdaIamBootstrap() {
    }

    public record BootstrapResult(String roleArn, String policyArn) {
    }

    public static BootstrapResult bootstrap(IamClient iam) {
        String policyArn = createOrGetPolicy(iam);
        String roleArn = createOrGetRole(iam);
        iam.attachRolePolicy(r -> r.roleName(ROLE_NAME).policyArn(policyArn));
        return new BootstrapResult(roleArn, policyArn);
    }

    private static String createOrGetPolicy(IamClient iam) {
        try {
            CreatePolicyResponse response = iam.createPolicy(r -> r
                    .policyName(POLICY_NAME)
                    .policyDocument(load("policies/file-metadata-lambda-policy.json")));
            return response.policy().arn();
        } catch (EntityAlreadyExistsException alreadyExists) {
            return "arn:aws:iam::" + DEFAULT_ACCOUNT_ID + ":policy/" + POLICY_NAME;
        }
    }

    private static String createOrGetRole(IamClient iam) {
        try {
            CreateRoleResponse response = iam.createRole(r -> r
                    .roleName(ROLE_NAME)
                    .assumeRolePolicyDocument(load("policies/lambda-trust-policy.json")));
            return response.role().arn();
        } catch (EntityAlreadyExistsException alreadyExists) {
            return iam.getRole(r -> r.roleName(ROLE_NAME)).role().arn();
        }
    }

    private static String load(String classpathResource) {
        try (InputStream in = LambdaIamBootstrap.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Not found on classpath: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
