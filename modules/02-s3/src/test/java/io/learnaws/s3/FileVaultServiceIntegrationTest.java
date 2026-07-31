package io.learnaws.s3;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.floci.testcontainers.FlociContainer;
import io.learnaws.foundations.FlociEndpoint;
import io.learnaws.s3.dto.UploadResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Exercises real S3 behavior (versioning, multipart upload, presigned URLs) against a
 * disposable Floci instance. Requires Docker.
 *
 * Run with: mvn test -Pfloci -pl modules/02-s3
 */
@Tag("floci")
@Testcontainers
class FileVaultServiceIntegrationTest {

    private static final String BUCKET = "task-tracker-files";

    @Container
    static FlociContainer floci = new FlociContainer();

    static FileVaultService fileVaultService;

    @BeforeAll
    static void setUpBucketAndService() {
        FlociEndpoint endpoint = FlociEndpoint.of(
                floci.getEndpoint(), Region.of(floci.getRegion()), floci.getAccessKey(), floci.getSecretKey());

        S3Client s3 = S3Client.builder()
                .endpointOverride(endpoint.getEndpoint())
                .region(endpoint.getRegion())
                .credentialsProvider(endpoint.getCredentialsProvider())
                .forcePathStyle(true)
                .build();

        S3Presigner presigner = S3Presigner.builder()
                .endpointOverride(endpoint.getEndpoint())
                .region(endpoint.getRegion())
                .credentialsProvider(endpoint.getCredentialsProvider())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        s3.createBucket(b -> b.bucket(BUCKET));
        s3.putBucketVersioning(b -> b
                .bucket(BUCKET)
                .versioningConfiguration(v -> v.status(BucketVersioningStatus.ENABLED)));

        fileVaultService = new FileVaultService(s3, presigner, BUCKET);
    }

    @Test
    void uploadListDownloadAndDeleteRoundTrip() {
        UploadResponse uploaded = fileVaultService.upload("hello.txt", "hello floci".getBytes(), "text/plain");
        assertThat(uploaded.versionId()).isNotBlank();

        assertThat(fileVaultService.list()).extracting("key").contains("hello.txt");
        assertThat(fileVaultService.download("hello.txt")).isEqualTo("hello floci".getBytes());

        fileVaultService.delete("hello.txt");
        assertThat(fileVaultService.list()).extracting("key").doesNotContain("hello.txt");
    }

    @Test
    void versioningKeepsEveryRevisionOfAKey() {
        fileVaultService.upload("versioned.txt", "v1".getBytes(), "text/plain");
        fileVaultService.upload("versioned.txt", "v2".getBytes(), "text/plain");

        List<String> versions = fileVaultService.listVersions("versioned.txt");
        assertThat(versions).hasSizeGreaterThanOrEqualTo(2);

        assertThat(fileVaultService.download("versioned.txt")).isEqualTo("v2".getBytes());
    }

    @Test
    void largeUploadsGoThroughTheMultipartProtocol() {
        byte[] sixMegabytes = new byte[6 * 1024 * 1024];
        java.util.Arrays.fill(sixMegabytes, (byte) 'x');

        fileVaultService.upload("big-file.bin", sixMegabytes, "application/octet-stream");

        assertThat(fileVaultService.download("big-file.bin")).hasSize(sixMegabytes.length);
    }

    @Test
    void presignedUrlGrantsTimeLimitedAccessWithoutSharingCredentials() {
        fileVaultService.upload("shared.txt", "share me".getBytes(), "text/plain");

        String url = fileVaultService.presignedGetUrl("shared.txt", Duration.ofMinutes(5));

        assertThat(url).startsWith("http");
        assertThat(url).contains("shared.txt");
        assertThat(url).contains("X-Amz-Signature");
    }
}
