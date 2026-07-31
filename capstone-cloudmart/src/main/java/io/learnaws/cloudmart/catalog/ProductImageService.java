package io.learnaws.cloudmart.catalog;

import java.time.Duration;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * A leaner version of Module 02's File Vault, scoped to what the catalog actually needs:
 * store a product image, hand back a time-limited URL for it.
 */
public class ProductImageService {

    public static final String BUCKET_NAME = "cloudmart-product-images";

    private final S3Client s3;
    private final S3Presigner presigner;

    public ProductImageService(S3Client s3, S3Presigner presigner) {
        this.s3 = s3;
        this.presigner = presigner;
    }

    public void ensureBucketExists() {
        try {
            s3.createBucket(b -> b.bucket(BUCKET_NAME));
        } catch (BucketAlreadyOwnedByYouException alreadyExists) {
            // fine
        }
    }

    public void upload(String key, byte[] content, String contentType) {
        s3.putObject(b -> b.bucket(BUCKET_NAME).key(key).contentType(contentType), RequestBody.fromBytes(content));
    }

    public String presignedUrl(String key, Duration expiry) {
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(b -> b.bucket(BUCKET_NAME).key(key))
                .build();
        return presigner.presignGetObject(request).url().toString();
    }
}
