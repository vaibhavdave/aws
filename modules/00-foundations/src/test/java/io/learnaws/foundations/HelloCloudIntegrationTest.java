package io.learnaws.foundations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

/**
 * Integration test against a real Floci instance, started fresh by Testcontainers.
 * Requires Docker. Excluded from the default `mvn test` run - see the "floci" group.
 *
 * Run with: mvn test -Pfloci -pl modules/00-foundations
 */
@Tag("floci")
@Testcontainers
class HelloCloudIntegrationTest {

    @Container
    static FlociContainer floci = new FlociContainer();

    @Test
    void getCallerIdentityReturnsAnAccountAndArn() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        try (StsClient sts = endpoint.configure(StsClient.builder())) {
            GetCallerIdentityResponse identity = sts.getCallerIdentity();

            assertThat(identity.account()).isNotBlank();
            assertThat(identity.arn()).isNotBlank();
            assertThat(identity.userId()).isNotBlank();
        }
    }
}
