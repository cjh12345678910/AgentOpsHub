package com.agentopshub.controller;

import com.agentopshub.dto.TaskTraceResponse;
import com.agentopshub.dto.ToolCallTraceResponse;
import com.agentopshub.dto.TraceStepResponse;
import com.agentopshub.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskTraceReplayContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @Test
    void shouldExposePolicyFieldsInReplayTrace() throws Exception {
        ToolCallTraceResponse call = new ToolCallTraceResponse(
            1L,
            11L,
            1,
            "chunk_fetch",
            "{}",
            "{}",
            false,
            "TOOL_SCOPE_DENIED",
            "missing scope",
            9L,
            "trace-task-1",
            Instant.parse("2026-03-03T10:00:00Z"),
            List.of("doc-1-c-1"),
            "deny",
            "missing_scope",
            "rag:chunk:read",
            "scope_gate",
            null,
            null
        );

        TraceStepResponse step = new TraceStepResponse(
            11L,
            2,
            "TOOL",
            "FAILED",
            "input",
            "output",
            Instant.parse("2026-03-03T10:00:00Z"),
            Instant.parse("2026-03-03T10:00:01Z"),
            1000L,
            "TOOL_SCOPE_DENIED",
            "missing scope",
            0,
            List.of("doc-1-c-1"),
            List.of(call),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        TaskTraceResponse response = new TaskTraceResponse(
            "task-1",
            "FAILED",
            "trace-task-1",
            false,
            List.of(step)
        );

        Mockito.when(taskService.getTaskTrace("task-1")).thenReturn(response);

        mockMvc.perform(get("/api/tasks/task-1/trace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.steps[0].toolCalls[0].policyDecision").value("deny"))
            .andExpect(jsonPath("$.steps[0].toolCalls[0].denyReason").value("missing_scope"))
            .andExpect(jsonPath("$.steps[0].toolCalls[0].requiredScope").value("rag:chunk:read"))
            .andExpect(jsonPath("$.steps[0].toolCalls[0].safetyRule").value("scope_gate"));
    }

    @Test
    void shouldExposeCancelledPartialTraceSemantics() throws Exception {
        TraceStepResponse step = new TraceStepResponse(
            15L,
            2,
            "EXECUTE",
            "RUNNING",
            "input",
            "partial output",
            Instant.parse("2026-03-03T10:00:00Z"),
            null,
            null,
            null,
            null,
            0,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        TaskTraceResponse response = new TaskTraceResponse(
            "task-cancelled",
            "CANCELLED",
            "trace-task-cancelled",
            true,
            List.of(step)
        );
        Mockito.when(taskService.getTaskTrace("task-cancelled")).thenReturn(response);

        mockMvc.perform(get("/api/tasks/task-cancelled/trace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.incomplete").value(true))
            .andExpect(jsonPath("$.steps[0].status").value("RUNNING"));
    }
}
