package io.learnaws.ecs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.learnaws.dynamodb.Task;
import io.learnaws.dynamodb.TaskRepository;

/** Tests only the HTTP layer, TaskRepository mocked - no Floci, no Docker, no ECS. */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskRepository taskRepository;

    @Test
    void healthzReturnsOk() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void createReturnsTheSavedTask() throws Exception {
        when(taskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Containerize it\",\"description\":\"d\",\"owner\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Containerize it"))
                .andExpect(jsonPath("$.owner").value("alice"));
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(taskRepository.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/tasks/missing")).andExpect(status().isNotFound());
    }

    @Test
    void getByIdReturns200WhenFound() throws Exception {
        Task task = Task.newTask("t1", "Title", "d", "alice");
        when(taskRepository.findById("t1")).thenReturn(Optional.of(task));

        mockMvc.perform(get("/tasks/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"));
    }
}
