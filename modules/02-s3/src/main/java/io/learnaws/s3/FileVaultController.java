package io.learnaws.s3;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.learnaws.s3.dto.FileSummary;
import io.learnaws.s3.dto.UploadResponse;

@RestController
@RequestMapping("/api/files")
public class FileVaultController {

    private final FileVaultService fileVaultService;

    public FileVaultController(FileVaultService fileVaultService) {
        this.fileVaultService = fileVaultService;
    }

    @PostMapping
    public UploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return fileVaultService.upload(file.getOriginalFilename(), file.getBytes(), file.getContentType());
    }

    @GetMapping
    public List<FileSummary> list() {
        return fileVaultService.list();
    }

    @GetMapping("/{key}")
    public ResponseEntity<byte[]> download(@PathVariable String key) {
        return ResponseEntity.ok(fileVaultService.download(key));
    }

    @GetMapping("/{key}/presigned-url")
    public Map<String, String> presignedUrl(@PathVariable String key,
            @RequestParam(defaultValue = "PT15M") String expiry) {
        String url = fileVaultService.presignedGetUrl(key, Duration.parse(expiry));
        return Map.of("url", url);
    }

    @GetMapping("/{key}/versions")
    public List<String> versions(@PathVariable String key) {
        return fileVaultService.listVersions(key);
    }

    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String key) {
        fileVaultService.delete(key);
        return ResponseEntity.noContent().build();
    }
}
