package io.learnaws.s3;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.learnaws.s3.dto.FileSummary;
import io.learnaws.s3.dto.UploadResponse;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectVersionsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class FileVaultService {

    /** Every part after the first must be at least this large in real S3; keep uploads
     * under it as a single PutObject, matching {@link MultipartUploader#MIN_PART_SIZE}. */
    private static final long MULTIPART_THRESHOLD = MultipartUploader.MIN_PART_SIZE;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final MultipartUploader multipartUploader;
    private final String bucketName;

    public FileVaultService(S3Client s3, S3Presigner presigner, @Value("${file-vault.bucket-name}") String bucketName) {
        this.s3 = s3;
        this.presigner = presigner;
        this.multipartUploader = new MultipartUploader(s3);
        this.bucketName = bucketName;
    }

    public UploadResponse upload(String key, byte[] content, String contentType) {
        multipartUploader.upload(bucketName, key, content, MULTIPART_THRESHOLD);

        // Read back the version id assigned to what we just wrote (versioning is enabled
        // on the bucket by S3Config's startup runner).
        String versionId = s3.headObject(b -> b.bucket(bucketName).key(key)).versionId();
        return new UploadResponse(key, content.length, versionId);
    }

    public byte[] download(String key) {
        ResponseInputStream<GetObjectResponse> object = s3.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(key).build());
        try {
            return object.readAllBytes();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public List<FileSummary> list() {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(bucketName).build())
                .contents()
                .stream()
                .map(o -> new FileSummary(o.key(), o.size(), o.lastModified()))
                .toList();
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
    }

    public List<String> listVersions(String key) {
        return s3.listObjectVersions(ListObjectVersionsRequest.builder().bucket(bucketName).prefix(key).build())
                .versions()
                .stream()
                .filter(v -> v.key().equals(key))
                .map(v -> v.versionId())
                .toList();
    }

    public String presignedGetUrl(String key, Duration expiry) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucketName).key(key).build())
                .build();

        return presigner.presignGetObject(presignRequest).url().toString();
    }
}
