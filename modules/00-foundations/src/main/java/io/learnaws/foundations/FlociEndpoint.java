package io.learnaws.foundations;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/**
 * Points any AWS SDK v2 client builder at a Floci instance instead of real AWS.
 * Every module in this curriculum reuses this instead of repeating the
 * endpoint/region/credentials boilerplate on every client.
 */
public final class FlociEndpoint {

    public static final String DEFAULT_URL = "http://localhost:4566";
    public static final Region DEFAULT_REGION = Region.US_EAST_1;

    private final URI endpoint;
    private final Region region;
    private final AwsCredentialsProvider credentialsProvider;

    private FlociEndpoint(URI endpoint, Region region, AwsCredentialsProvider credentialsProvider) {
        this.endpoint = endpoint;
        this.region = region;
        this.credentialsProvider = credentialsProvider;
    }

    /** Assumes Floci is running locally via {@code docker compose up} on the default port. */
    public static FlociEndpoint local() {
        return of(DEFAULT_URL, DEFAULT_REGION, "test", "test");
    }

    /** For Testcontainers-managed instances, whose host/port/credentials are assigned at runtime. */
    public static FlociEndpoint of(String endpointUrl, Region region, String accessKey, String secretKey) {
        return new FlociEndpoint(
                URI.create(endpointUrl),
                region,
                StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    }

    /** Applies endpoint + region + credentials to any AWS SDK v2 client builder and builds it. */
    public <B extends AwsClientBuilder<B, C>, C> C configure(B builder) {
        return builder
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public Region getRegion() {
        return region;
    }

    public AwsCredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }
}
