package io.learnaws.ecs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application that gets containerized and run as an ECS service - see Dockerfile and
 * EcsDeployer. Deliberately identical in spirit to Module 06's Task Tracker API, but as a
 * long-running container instead of a Lambda function.
 */
@SpringBootApplication
public class TaskTrackerContainerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskTrackerContainerApplication.class, args);
    }
}
