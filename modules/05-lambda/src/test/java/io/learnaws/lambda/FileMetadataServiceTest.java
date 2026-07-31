package io.learnaws.lambda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Pure logic, no Floci, no Docker, no deployment jar required. */
class FileMetadataServiceTest {

    @Test
    void fromS3RecordCapturesBucketKeyAndSize() {
        FileMetadata metadata = FileMetadataService.fromS3Record("task-tracker-files", "notes.txt", 42L);

        assertThat(metadata.bucket()).isEqualTo("task-tracker-files");
        assertThat(metadata.key()).isEqualTo("notes.txt");
        assertThat(metadata.sizeBytes()).isEqualTo(42L);
        assertThat(metadata.processedAt()).isNotNull();
    }
}
