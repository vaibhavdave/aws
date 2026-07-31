package io.learnaws.s3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

/**
 * Demonstrates S3's multipart upload flow explicitly (createMultipartUpload -> uploadPart* ->
 * completeMultipartUpload) instead of hiding it behind a high-level transfer manager, since
 * understanding this three-step protocol is the point of this part of the module.
 *
 * Real S3 requires every part except the last to be at least 5 MiB; {@link #MIN_PART_SIZE}
 * models that constraint.
 */
public class MultipartUploader {

    public static final long MIN_PART_SIZE = 5L * 1024 * 1024;

    private final S3Client s3;

    public MultipartUploader(S3Client s3) {
        this.s3 = s3;
    }

    /** Splits [0, totalSize) into contiguous [start, end) ranges of at most partSize bytes each. */
    public static List<long[]> planParts(long totalSize, long partSize) {
        if (totalSize <= 0) {
            return List.of();
        }
        List<long[]> parts = new ArrayList<>();
        long offset = 0;
        while (offset < totalSize) {
            long end = Math.min(offset + partSize, totalSize);
            parts.add(new long[] {offset, end});
            offset = end;
        }
        return parts;
    }

    /**
     * Uploads {@code content} to {@code bucket/key}, using a single PutObject when it's smaller
     * than {@code partSize}, or the full multipart protocol otherwise.
     */
    public void upload(String bucket, String key, byte[] content, long partSize) {
        if (content.length <= partSize) {
            s3.putObject(b -> b.bucket(bucket).key(key), RequestBody.fromBytes(content));
            return;
        }

        CreateMultipartUploadResponse created = s3.createMultipartUpload(b -> b.bucket(bucket).key(key));
        String uploadId = created.uploadId();

        try {
            List<CompletedPart> completedParts = new ArrayList<>();
            List<long[]> plan = planParts(content.length, partSize);

            for (int i = 0; i < plan.size(); i++) {
                int partNumber = i + 1;
                long[] range = plan.get(i);
                byte[] chunk = Arrays.copyOfRange(content, (int) range[0], (int) range[1]);

                UploadPartResponse partResponse = s3.uploadPart(
                        b -> b.bucket(bucket).key(key).uploadId(uploadId).partNumber(partNumber),
                        RequestBody.fromBytes(chunk));

                completedParts.add(CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(partResponse.eTag())
                        .build());
            }

            s3.completeMultipartUpload(b -> b
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder().parts(completedParts).build()));
        } catch (RuntimeException e) {
            s3.abortMultipartUpload(b -> b.bucket(bucket).key(key).uploadId(uploadId));
            throw e;
        }
    }
}
