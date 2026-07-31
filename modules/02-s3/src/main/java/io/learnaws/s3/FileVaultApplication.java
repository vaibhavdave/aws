package io.learnaws.s3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Run with: mvn -pl modules/02-s3 spring-boot:run
 * Then, e.g.: curl -F file=@somefile.txt http://localhost:8082/api/files
 */
@SpringBootApplication
public class FileVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileVaultApplication.class, args);
    }
}
