package io.learnaws.s3;

import io.learnaws.foundations.FlociEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Value("${file-vault.bucket-name}")
    private String bucketName;

    /**
     * forcePathStyle is what lets a bucket named "task-tracker-files" be addressed as
     * http://localhost:4566/task-tracker-files/key instead of requiring a DNS-resolvable
     * virtual-hosted subdomain - the simplest way to point the S3 SDK at a local emulator.
     */
    @Bean
    public S3Client s3Client() {
        FlociEndpoint floci = FlociEndpoint.local();
        return S3Client.builder()
                .endpointOverride(floci.getEndpoint())
                .region(floci.getRegion())
                .credentialsProvider(floci.getCredentialsProvider())
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        FlociEndpoint floci = FlociEndpoint.local();
        return S3Presigner.builder()
                .endpointOverride(floci.getEndpoint())
                .region(floci.getRegion())
                .credentialsProvider(floci.getCredentialsProvider())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /** Ensures the bucket exists and has versioning on before the app serves any requests. */
    @Bean
    public ApplicationRunner bucketInitializer(S3Client s3Client) {
        return (ApplicationArguments args) -> {
            try {
                s3Client.headBucket(b -> b.bucket(bucketName));
            } catch (NoSuchBucketException notFound) {
                s3Client.createBucket(b -> b.bucket(bucketName));
            }
            s3Client.putBucketVersioning(b -> b
                    .bucket(bucketName)
                    .versioningConfiguration(v -> v.status(BucketVersioningStatus.ENABLED)));
        };
    }
}
