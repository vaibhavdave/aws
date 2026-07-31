package io.learnaws.iam;

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreatePolicyResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleResponse;
import software.amazon.awssdk.services.iam.model.EntityAlreadyExistsException;

/**
 * Provisions the least-privilege role + policy that later modules' demo apps run under:
 * a role trusted by the account root, with a policy scoped to exactly the DynamoDB table
 * (Module 03) and S3 bucket (Module 02) the Task Tracker app needs.
 */
public final class IamBootstrap {

    public static final String ROLE_NAME = "task-tracker-app-role";
    public static final String POLICY_NAME = "task-tracker-app-policy";

    /** Floci's default account id when credentials aren't a 12-digit access key. */
    private static final String DEFAULT_ACCOUNT_ID = "000000000000";

    private IamBootstrap() {
    }

    public record BootstrapResult(String roleArn, String policyArn, String roleName) {
    }

    public static BootstrapResult bootstrap(IamClient iam) {
        String policyArn = createOrGetPolicy(iam);
        String roleArn = createOrGetRole(iam);
        iam.attachRolePolicy(r -> r.roleName(ROLE_NAME).policyArn(policyArn));
        return new BootstrapResult(roleArn, policyArn, ROLE_NAME);
    }

    private static String createOrGetPolicy(IamClient iam) {
        try {
            CreatePolicyResponse response = iam.createPolicy(r -> r
                    .policyName(POLICY_NAME)
                    .description("Least-privilege permissions for the Task Tracker demo app")
                    .policyDocument(PolicyDocuments.load(PolicyDocuments.APP_POLICY_RESOURCE)));
            return response.policy().arn();
        } catch (EntityAlreadyExistsException alreadyExists) {
            return "arn:aws:iam::" + DEFAULT_ACCOUNT_ID + ":policy/" + POLICY_NAME;
        }
    }

    private static String createOrGetRole(IamClient iam) {
        try {
            CreateRoleResponse response = iam.createRole(r -> r
                    .roleName(ROLE_NAME)
                    .description("Least-privilege role for the Task Tracker demo app")
                    .assumeRolePolicyDocument(PolicyDocuments.load(PolicyDocuments.TRUST_POLICY_RESOURCE)));
            return response.role().arn();
        } catch (EntityAlreadyExistsException alreadyExists) {
            return iam.getRole(r -> r.roleName(ROLE_NAME)).role().arn();
        }
    }
}
