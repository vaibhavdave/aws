package io.learnaws.rds;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.learnaws.rds.UserTaskSummaryService.UserTaskSummary;

@RestController
@RequestMapping("/api/users")
public class UsersController {

    public record CreateUserRequest(String username, String email) {
    }

    private final UserRepository userRepository;
    private final UserTaskSummaryService summaryService;

    public UsersController(UserRepository userRepository, UserTaskSummaryService summaryService) {
        this.userRepository = userRepository;
        this.summaryService = summaryService;
    }

    @PostMapping
    public UserEntity create(@RequestBody CreateUserRequest request) {
        UserEntity user = new UserEntity(UUID.randomUUID(), request.username(), request.email(), Instant.now());
        return userRepository.save(user);
    }

    @GetMapping("/{username}/summary")
    public UserTaskSummary summary(@PathVariable String username) {
        return summaryService.summarize(username);
    }
}
