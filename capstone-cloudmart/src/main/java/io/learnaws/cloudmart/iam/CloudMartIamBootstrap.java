package io.learnaws.cloudmart.iam;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;

/**
 * Two roles, each scoped to only what its function does - the same least-privilege
 * discipline Module 01 introduced, applied to a second, independent domain.
 */
public final class CloudMartIamBootstrap {

    public static final String API_ROLE_NAME = "cloudmart-api-lambda-role";
    public static final String API_POLICY_NAME = "cloudmart-api-lambda-policy";
    public static final String PROCESSOR_ROLE_NAME = "cloudmart-processor-lambda-role";
    public static final String PROCESSOR_POLICY_NAME = "cloudmart-processor-lambda-policy";
    private static final String DEFAULT_ACCOUNT_ID = "000000000000";

    private CloudMartIamBootstrap() {
    }

    public record Roles(String apiRoleArn, String processorRoleArn) {
    }

    public static Roles bootstrap(IamClient iam) {
        String apiRoleArn = createOrGetRole(iam, API_ROLE_NAME);
        String apiPolicyArn = createOrGetPolicy(iam, API_POLICY_NAME, "policies/cloudmart-api-policy.json");
        iam.attachRolePolicy(r -> r.roleName(API_ROLE_NAME).policyArn(apiPolicyArn));

        String processorRoleArn = createOrGetRole(iam, PROCESSOR_ROLE_NAME);
        String processorPolicyArn = createOrGetPolicy(iam, PROCESSOR_POLICY_NAME, "policies/cloudmart-processor-policy.json");
        iam.attachRolePolicy(r -> r.roleName(PROCESSOR_ROLE_NAME).policyArn(processorPolicyArn));

        return new Roles(apiRoleArn, processorRoleArn);
    }

    private static String createOrGetRole(IamClient iam, String roleName) {
        try {
            return iam.createRole(r -> r
                            .roleName(roleName)
                            .assumeRolePolicyDocument(load("policies/lambda-trust-policy.json")))
                    .role()
                    .arn();
        } catch (EntityAlreadyExistsException alreadyExists) {
            return iam.getRole(r -> r.roleName(roleName)).role().arn();
        }
    }

    private static String createOrGetPolicy(IamClient iam, String policyName, String documentResource) {
        try {
            return iam.createPolicy(r -> r.policyName(policyName).policyDocument(load(documentResource)))
                    .policy()
                    .arn();
        } catch (EntityAlreadyExistsException alreadyExists) {
            return "arn:aws:iam::" + DEFAULT_ACCOUNT_ID + ":policy/" + policyName;
        }
    }

    private static String load(String classpathResource) {
        try (InputStream in = CloudMartIamBootstrap.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalArgumentException("Not found on classpath: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
