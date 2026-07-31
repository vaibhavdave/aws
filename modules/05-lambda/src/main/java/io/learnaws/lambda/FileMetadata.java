package io.learnaws.lambda;

import java.time.Instant;

public record FileMetadata(String bucket, String key, long sizeBytes, Instant processedAt) {
}
