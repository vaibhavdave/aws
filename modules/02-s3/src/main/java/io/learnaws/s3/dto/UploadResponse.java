package io.learnaws.s3.dto;

public record UploadResponse(String key, long sizeBytes, String versionId) {
}
