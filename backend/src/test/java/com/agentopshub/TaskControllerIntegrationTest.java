package com.agentopshub;

import com.agentopshub.controller.TaskController;
import com.agentopshub.dto.CreateTaskRequest;
import com.agentopshub.dto.TaskCreatedResponse;
import com.agentopshub.dto.TaskResultResponse;
import com.agentopshub.dto.TaskStatusResponse;
import com.agentopshub.dto.TaskTraceResponse;
import com.agentopshub.dto.TraceStepResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.service.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    @Test
    void createTaskSuccess() throws Exception {
        when(taskService.createTask(any(CreateTaskRequest.class)))
            .thenReturn(new TaskCreatedResponse("task-1", "CREATED"));

        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"test prompt\",\"outputFormat\":\"both\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("task-1"))
            .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void getTaskStatusSuccess() throws Exception {
        when(taskService.getTaskStatus(eq("task-1")))
            .thenReturn(new TaskStatusResponse("task-1", "RUNNING", "prompt", "both", "RUNNING", 0, "ACT", "RUNNING", 1, Instant.now(), Instant.now(), true));

        mockMvc.perform(get("/api/tasks/{taskId}", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("task-1"))
            .andExpect(jsonPath("$.status").value("RUNNING"))
            .andExpect(jsonPath("$.phase").value("ACT"))
            .andExpect(jsonPath("$.currentRound").value(1))
            .andExpect(jsonPath("$.traceAvailable").value(true));
    }

    @Test
    void cancelTaskSuccess() throws Exception {
        when(taskService.cancelTask(eq("task-1")))
            .thenReturn(new TaskStatusResponse("task-1", "CANCELLED", "prompt", "both", "CANCELLED", 0, "ACT", "RUNNING", 1, Instant.now(), Instant.now(), true));

        mockMvc.perform(post("/api/tasks/{taskId}/cancel", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("task-1"))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelTaskConflict() throws Exception {
        when(taskService.cancelTask(eq("task-1")))
            .thenThrow(new ApiException("TASK_CANNOT_CANCEL", "Task is already in terminal status", Map.of("taskId", "task-1", "status", "SUCCEEDED"), HttpStatus.CONFLICT.value()));

        mockMvc.perform(post("/api/tasks/{taskId}/cancel", "task-1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("TASK_CANNOT_CANCEL"));
    }

    @Test
    void getTaskResultSuccess() throws Exception {
        when(taskService.getTaskResult(eq("task-1")))
            .thenReturn(new TaskResultResponse(
                "task-1",
                "SUCCEEDED",
                "# Task Result",
                "{\"status\":\"succeeded\"}",
                List.of("doc-1-c-1"),
                Map.of("overallPass", true),
                List.of(),
                "passed_first_try",
                List.of(),
                null,
                null,
                null,
                Map.of("text", "final answer", "format", "text"),
                List.of()
            ));

        mockMvc.perform(get("/api/tasks/{taskId}/result", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("task-1"))
            .andExpect(jsonPath("$.status").value("SUCCEEDED"))
            .andExpect(jsonPath("$.citations[0]").value("doc-1-c-1"))
            .andExpect(jsonPath("$.finalDecision").value("passed_first_try"))
            .andExpect(jsonPath("$.finalAnswer.text").value("final answer"));
    }

    @Test
    void getTaskTraceSuccess() throws Exception {
        TraceStepResponse step = new TraceStepResponse();
        step.setStepId(10L);
        step.setSeq(1);
        step.setStepType("GENERATE");
        step.setStatus("SUCCEEDED");
        step.setFinalAnswerText("final generated answer");

        when(taskService.getTaskTrace(eq("task-1")))
            .thenReturn(new TaskTraceResponse("task-1", "SUCCEEDED", "trace-task-1", false, List.of(step)));

        mockMvc.perform(get("/api/tasks/{taskId}/trace", "task-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value("task-1"))
            .andExpect(jsonPath("$.traceId").value("trace-task-1"))
            .andExpect(jsonPath("$.steps[0].stepType").value("GENERATE"))
            .andExpect(jsonPath("$.steps[0].finalAnswerText").value("final generated answer"));
    }

    @Test
    void getTaskTraceNotAvailable() throws Exception {
        when(taskService.getTaskTrace(eq("task-2")))
            .thenThrow(new ApiException("TRACE_NOT_AVAILABLE", "Trace is not available for this task yet", Map.of("taskId", "task-2"), HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/tasks/{taskId}/trace", "task-2"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TRACE_NOT_AVAILABLE"));
    }

    @Test
    void downloadArtifactSuccess() throws Exception {
        when(taskService.downloadArtifact(eq("task-1"), eq("reports/out.md")))
            .thenReturn(new TaskService.ArtifactPayload("out.md", "hello".getBytes(), "text/markdown; charset=utf-8"));

        mockMvc.perform(get("/api/tasks/{taskId}/artifacts/download", "task-1")
                .param("path", "reports/out.md"))
            .andExpect(status().isOk())
            .andExpect(content().bytes("hello".getBytes()));
    }

    @Test
    void createTaskValidationError() throws Exception {
        mockMvc.perform(post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"prompt\":\"\",\"outputFormat\":\"bad\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void taskNotFoundError() throws Exception {
        when(taskService.getTaskStatus(eq("missing-id")))
            .thenThrow(new ApiException("TASK_NOT_FOUND", "Task not found", Map.of("taskId", "missing-id"), HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/tasks/{taskId}", "missing-id"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }
}
