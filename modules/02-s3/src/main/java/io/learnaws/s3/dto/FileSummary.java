package io.learnaws.s3.dto;

import java.time.Instant;

public record FileSummary(String key, long sizeBytes, Instant lastModified) {
}
