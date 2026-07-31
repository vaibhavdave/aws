package io.learnaws.iam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.foundations.FlociEndpoint;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * Requires Docker. Run with: mvn test -Pfloci -pl modules/01-iam
 */
@Tag("floci")
@Testcontainers
class IamBootstrapIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void bootstrapsRoleAndPolicyThenSuccessfullyAssumesTheRole() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        try (IamClient iam = endpoint.configure(IamClient.builder());
                StsClient sts = endpoint.configure(StsClient.builder())) {

            IamBootstrap.BootstrapResult result = IamBootstrap.bootstrap(iam);
            assertThat(result.roleArn()).contains(IamBootstrap.ROLE_NAME);
            assertThat(result.policyArn()).contains(IamBootstrap.POLICY_NAME);

            AwsSessionCredentials sessionCredentials = RoleAssumer.assumeRole(sts, result.roleArn(), "test-session");
            assertThat(sessionCredentials.sessionToken()).isNotBlank();
            assertThat(sessionCredentials.accessKeyId()).isNotBlank();

            try (StsClient assumedSts = StsClient.builder()
                    .endpointOverride(endpoint.getEndpoint())
                    .region(endpoint.getRegion())
                    .credentialsProvider(StaticCredentialsProvider.create(sessionCredentials))
                    .build()) {

                GetCallerIdentityResponse identity = assumedSts.getCallerIdentity();
                assertThat(identity.arn()).contains(IamBootstrap.ROLE_NAME);
            }
        }
    }
}
