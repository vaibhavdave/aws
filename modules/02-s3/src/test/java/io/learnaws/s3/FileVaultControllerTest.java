package io.learnaws.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.learnaws.s3.dto.FileSummary;
import io.learnaws.s3.dto.UploadResponse;

/**
 * Tests only the HTTP layer (routing, request/response mapping) with the service mocked
 * out - no Floci, no S3, no Docker required. This is the fast layer of the test pyramid;
 * FileVaultServiceIntegrationTest covers the real S3 behavior underneath it.
 */
@WebMvcTest(FileVaultController.class)
class FileVaultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileVaultService fileVaultService;

    @Test
    void uploadReturnsTheStoredKeyAndVersion() throws Exception {
        when(fileVaultService.upload(anyString(), org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(new UploadResponse("notes.txt", 11, "v1"));

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello world".getBytes());

        mockMvc.perform(multipart("/api/files").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("notes.txt"))
                .andExpect(jsonPath("$.versionId").value("v1"));
    }

    @Test
    void listReturnsFileSummariesAsJson() throws Exception {
        when(fileVaultService.list())
                .thenReturn(List.of(new FileSummary("a.txt", 3, Instant.parse("2026-01-01T00:00:00Z"))));

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("a.txt"))
                .andExpect(jsonPath("$[0].sizeBytes").value(3));
    }

    @Test
    void downloadReturnsRawBytes() throws Exception {
        when(fileVaultService.download("a.txt")).thenReturn("hello".getBytes());

        var result = mockMvc.perform(get("/api/files/a.txt"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("hello".getBytes());
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/files/a.txt"))
                .andExpect(status().isNoContent());
    }
}
