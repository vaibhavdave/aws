package io.learnaws.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;

/**
 * All the actual request handling, deliberately kept separate from {@link TaskApiHandler} so
 * it can be unit tested with a mocked {@link TaskRepository} - no Lambda runtime, no Floci,
 * no Docker needed to verify the routing and request/response mapping.
 */
public class TaskApiRouter {

    private final TaskRepository repository;

    public TaskApiRouter(TaskRepository repository) {
        this.repository = repository;
    }

    public APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent request) {
        String resource = request.getResource();
        String method = request.getHttpMethod();

        try {
            if ("/tasks".equals(resource) && "POST".equals(method)) {
                return create(request);
            }
            if ("/tasks".equals(resource) && "GET".equals(method)) {
                return listByOwner(request);
            }
            if ("/tasks/{id}".equals(resource) && "GET".equals(method)) {
                return getById(request);
            }
            if ("/tasks/{id}".equals(resource) && "PATCH".equals(method)) {
                return updateStatus(request);
            }
            if ("/tasks/{id}".equals(resource) && "DELETE".equals(method)) {
                return delete(request);
            }
            return response(404, Map.of("message", "no route for " + method + " " + resource));
        } catch (IllegalArgumentException badInput) {
            return response(400, Map.of("message", badInput.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent create(APIGatewayProxyRequestEvent request) {
        Map<String, Object> body = TaskApiCodec.parseMap(request.getBody());
        String title = requireString(body, "title");
        String description = body.getOrDefault("description", "").toString();
        String owner = requireString(body, "owner");

        Task task = Task.newTask(UUID.randomUUID().toString(), title, description, owner);
        repository.save(task);
        return response(201, task);
    }

    private APIGatewayProxyResponseEvent listByOwner(APIGatewayProxyRequestEvent request) {
        Map<String, String> query = request.getQueryStringParameters();
        String owner = query == null ? null : query.get("owner");
        if (owner == null || owner.isBlank()) {
            return response(400, Map.of("message", "?owner= query parameter is required"));
        }

        List<Task> tasks = repository.findByOwner(owner);
        return response(200, tasks);
    }

    private APIGatewayProxyResponseEvent getById(APIGatewayProxyRequestEvent request) {
        String id = request.getPathParameters().get("id");
        return repository.findById(id)
                .map(task -> response(200, task))
                .orElseGet(() -> response(404, Map.of("message", "no task with id " + id)));
    }

    private APIGatewayProxyResponseEvent updateStatus(APIGatewayProxyRequestEvent request) {
        String id = request.getPathParameters().get("id");
        Map<String, Object> body = TaskApiCodec.parseMap(request.getBody());
        String statusValue = requireString(body, "status");
        TaskStatus status = TaskStatus.valueOf(statusValue);

        Task updated = repository.updateStatus(id, status);
        return response(200, updated);
    }

    private APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent request) {
        String id = request.getPathParameters().get("id");
        repository.delete(id);
        return response(204, null);
    }

    private static String requireString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("\"" + field + "\" is required");
        }
        return s;
    }

    private static APIGatewayProxyResponseEvent response(int statusCode, Object body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"));
        return body == null ? response : response.withBody(TaskApiCodec.toJson(body));
    }
}
