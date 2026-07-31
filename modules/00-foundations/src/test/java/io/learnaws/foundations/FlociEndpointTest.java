package io.learnaws.foundations;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

/**
 * Plain unit tests: no Floci instance required, runs on every {@code mvn test}.
 */
class FlociEndpointTest {

    @Test
    void localPointsAtTheDefaultFlociEndpointWithDummyCredentials() {
        FlociEndpoint floci = FlociEndpoint.local();

        assertThat(floci.getEndpoint()).isEqualTo(URI.create("http://localhost:4566"));
        assertThat(floci.getRegion()).isEqualTo(Region.US_EAST_1);

        AwsCredentials credentials = floci.getCredentialsProvider().resolveCredentials();
        assertThat(credentials.accessKeyId()).isEqualTo("test");
        assertThat(credentials.secretAccessKey()).isEqualTo("test");
    }

    @Test
    void ofBuildsACustomEndpointForTestcontainersInstances() {
        FlociEndpoint floci = FlociEndpoint.of("http://localhost:32771", Region.EU_WEST_1, "abc", "xyz");

        assertThat(floci.getEndpoint()).isEqualTo(URI.create("http://localhost:32771"));
        assertThat(floci.getRegion()).isEqualTo(Region.EU_WEST_1);

        AwsCredentials credentials = floci.getCredentialsProvider().resolveCredentials();
        assertThat(credentials.accessKeyId()).isEqualTo("abc");
        assertThat(credentials.secretAccessKey()).isEqualTo("xyz");
    }

    @Test
    void configureAppliesEndpointRegionAndCredentialsToAnySdkClientBuilder() {
        FlociEndpoint floci = FlociEndpoint.local();

        try (StsClient sts = floci.configure(StsClient.builder())) {
            assertThat(sts).isNotNull();
        }
    }
}
