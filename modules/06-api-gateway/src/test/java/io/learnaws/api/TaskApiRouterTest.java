package io.learnaws.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;
import io.learnaws.dynamodb.TaskStatus;

/**
 * Tests routing and request/response mapping with TaskRepository mocked - no Floci, no
 * Lambda runtime, no Docker required. TaskApiHandler (the thin adapter that builds a real
 * TaskRepository) is what's actually exercised end-to-end by the Floci-backed IT instead.
 */
class TaskApiRouterTest {

    private TaskRepository repository;
    private TaskApiRouter router;

    @BeforeEach
    void setUp() {
        repository = mock(TaskRepository.class);
        router = new TaskApiRouter(repository);
    }

    private static APIGatewayProxyRequestEvent request(String method, String resource, Map<String, String> pathParams,
            Map<String, String> queryParams, String body) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod(method)
                .withResource(resource)
                .withPathParameters(pathParams)
                .withQueryStringParameters(queryParams)
                .withBody(body);
    }

    @Test
    void postTasksCreatesATaskAndReturns201() {
        APIGatewayProxyRequestEvent request = request("POST", "/tasks", null, null,
                """
                {"title":"Write tests","description":"cover the router","owner":"alice"}""");

        APIGatewayProxyResponseEvent response = router.route(request);

        assertThat(response.getStatusCode()).isEqualTo(201);
        assertThat(response.getBody()).contains("\"title\":\"Write tests\"").contains("\"owner\":\"alice\"");
        verify(repository).save(any(Task.class));
    }

    @Test
    void postTasksWithoutTitleReturns400() {
        APIGatewayProxyRequestEvent request = request("POST", "/tasks", null, null,
                """
                {"owner":"alice"}""");

        APIGatewayProxyResponseEvent response = router.route(request);

        assertThat(response.getStatusCode()).isEqualTo(400);
        verify(repository, never()).save(any());
    }

    @Test
    void getTasksWithoutOwnerQueryParamReturns400() {
        APIGatewayProxyResponseEvent response = router.route(request("GET", "/tasks", null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void getTasksWithOwnerReturnsThatOwnersTasks() {
        Task task = Task.newTask("t1", "Title", "d", "alice");
        when(repository.findByOwner("alice")).thenReturn(List.of(task));

        APIGatewayProxyResponseEvent response = router.route(
                request("GET", "/tasks", null, Map.of("owner", "alice"), null));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"id\":\"t1\"");
    }

    @Test
    void getTaskByIdReturns200WhenFound() {
        Task task = Task.newTask("t1", "Title", "d", "alice");
        when(repository.findById("t1")).thenReturn(Optional.of(task));

        APIGatewayProxyResponseEvent response = router.route(
                request("GET", "/tasks/{id}", Map.of("id", "t1"), null, null));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"id\":\"t1\"");
    }

    @Test
    void getTaskByIdReturns404WhenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        APIGatewayProxyResponseEvent response = router.route(
                request("GET", "/tasks/{id}", Map.of("id", "missing"), null, null));

        assertThat(response.getStatusCode()).isEqualTo(404);
    }

    @Test
    void patchUpdatesStatusAndReturns200() {
        Task updated = Task.newTask("t1", "Title", "d", "alice").withStatus(TaskStatus.DONE);
        when(repository.updateStatus("t1", TaskStatus.DONE)).thenReturn(updated);

        APIGatewayProxyResponseEvent response = router.route(
                request("PATCH", "/tasks/{id}", Map.of("id", "t1"), null, """
                        {"status":"DONE"}"""));

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"status\":\"DONE\"");
    }

    @Test
    void patchWithInvalidStatusReturns400() {
        APIGatewayProxyResponseEvent response = router.route(
                request("PATCH", "/tasks/{id}", Map.of("id", "t1"), null, """
                        {"status":"NOT_A_REAL_STATUS"}"""));

        assertThat(response.getStatusCode()).isEqualTo(400);
    }

    @Test
    void deleteRemovesTheTaskAndReturns204() {
        APIGatewayProxyResponseEvent response = router.route(
                request("DELETE", "/tasks/{id}", Map.of("id", "t1"), null, null));

        assertThat(response.getStatusCode()).isEqualTo(204);
        verify(repository).delete(eq("t1"));
    }

    @Test
    void unknownRouteReturns404() {
        APIGatewayProxyResponseEvent response = router.route(request("PUT", "/unknown", null, null, null));

        assertThat(response.getStatusCode()).isEqualTo(404);
    }
}
