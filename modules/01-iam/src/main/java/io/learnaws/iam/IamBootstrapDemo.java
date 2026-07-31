package io.learnaws.iam;

import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * Run with: mvn -pl modules/01-iam compile exec:java
 *
 * Bootstraps a least-privilege role + policy, assumes the role via STS,
 * and prints the caller identity as seen "from inside" that assumed role.
 */
public final class IamBootstrapDemo {

    public static void main(String[] args) {
        FlociEndpoint floci = FlociEndpoint.local();

        try (IamClient iam = floci.configure(IamClient.builder());
                StsClient sts = floci.configure(StsClient.builder())) {

            IamBootstrap.BootstrapResult result = IamBootstrap.bootstrap(iam);
            System.out.println("Created/verified policy : " + result.policyArn());
            System.out.println("Created/verified role   : " + result.roleArn());

            AwsSessionCredentials sessionCredentials =
                    RoleAssumer.assumeRole(sts, result.roleArn(), "iam-bootstrap-demo");
            System.out.println("Assumed role, session token starts with: "
                    + sessionCredentials.sessionToken().substring(0, Math.min(12, sessionCredentials.sessionToken().length()))
                    + "...");

            try (StsClient assumedSts = StsClient.builder()
                    .endpointOverride(floci.getEndpoint())
                    .region(floci.getRegion())
                    .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                    .build()) {

                GetCallerIdentityResponse identity = assumedSts.getCallerIdentity();
                System.out.println("Caller identity under the assumed role:");
                System.out.println("  ARN    : " + identity.arn());
                System.out.println("  UserId : " + identity.userId());
            }
        }
    }
}
