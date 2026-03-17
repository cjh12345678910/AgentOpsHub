package com.agentopshub.service;

import com.agentopshub.domain.TaskEntity;
import com.agentopshub.domain.TaskStatus;
import com.agentopshub.dto.AgentRunResponse;
import com.agentopshub.dto.TaskResultResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.TaskRepository;
import com.agentopshub.repository.TaskStepRepository;
import com.agentopshub.repository.ToolCallLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {
    private TaskRepository taskRepository;
    private TaskStepRepository taskStepRepository;
    private ToolCallLogRepository toolCallLogRepository;
    private StringRedisTemplate redisTemplate;
    private AgentRuntimeClient agentRuntimeClient;
    private LocalAgentRuntime localAgentRuntime;
    private TaskQueueProducer taskQueueProducer;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        taskStepRepository = mock(TaskStepRepository.class);
        toolCallLogRepository = mock(ToolCallLogRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);

        agentRuntimeClient = mock(AgentRuntimeClient.class);
        localAgentRuntime = mock(LocalAgentRuntime.class);
        taskQueueProducer = mock(TaskQueueProducer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<TaskQueueProducer> queueProvider = mock(ObjectProvider.class);
        when(queueProvider.getIfAvailable()).thenReturn(taskQueueProducer);
        taskService = new TaskService(
            taskRepository,
            taskStepRepository,
            toolCallLogRepository,
            redisTemplate,
            new ObjectMapper(),
            agentRuntimeClient,
            localAgentRuntime,
            queueProvider,
            300,
            0,
            false,
            "threadpool",
            2,
            50,
            3,
            5000,
            2,
            "/tmp/agentops_outputs",
            true
        );
    }

    @Test
    void getTaskResultSupportsFailedStatusAndParserDiagnostics() {
        TaskEntity failed = new TaskEntity();
        failed.setId("task-failed");
        failed.setStatus(TaskStatus.FAILED);
        failed.setResultMd("# Task Result\n\n- status: failed");
        failed.setResultJson(
            "{\"status\":\"failed\",\"parserStage\":\"parse\",\"rawResponseSnippet\":\"bad json\",\"usageUnavailableReason\":\"llm_timeout\",\"finalAnswer\":{\"text\":\"N/A\",\"format\":\"text\"}}"
        );
        when(taskRepository.findById(eq("task-failed"))).thenReturn(failed);

        TaskResultResponse response = taskService.getTaskResult("task-failed");
        assertEquals("FAILED", response.getStatus());
        assertEquals("parse", response.getParserStage());
        assertEquals("bad json", response.getRawResponseSnippet());
        assertEquals("llm_timeout", response.getUsageUnavailableReason());
        assertEquals("N/A", String.valueOf(response.getFinalAnswer().get("text")));
    }

    @Test
    void executeWithDispatchPersistsFailedResultPayload() {
        TaskEntity created = new TaskEntity();
        created.setId("task-1");
        created.setStatus(TaskStatus.CREATED);
        created.setPrompt("prompt");
        created.setOutputFormat("both");
        created.setCreatedAt(Instant.now());
        created.setUpdatedAt(Instant.now());

        TaskEntity failed = new TaskEntity();
        failed.setId("task-1");
        failed.setStatus(TaskStatus.FAILED);
        failed.setPrompt("prompt");
        failed.setOutputFormat("both");
        failed.setCreatedAt(created.getCreatedAt());
        failed.setUpdatedAt(Instant.now());

        when(taskRepository.findById(eq("task-1"))).thenReturn(created, failed, failed);

        AgentRunResponse runResponse = new AgentRunResponse();
        runResponse.setStatus("FAILED");
        runResponse.setErrorCode("llm_bad_response");
        runResponse.setErrorMessage("LLM JSON parse failed");
        runResponse.setParserStage("parse");
        runResponse.setRawResponseSnippet("{broken");
        runResponse.setUsageUnavailableReason("llm_bad_response");
        when(agentRuntimeClient.run(any())).thenReturn(runResponse);

        taskService.executeWithDispatch("task-1", "prompt", java.util.List.of("doc-1"), "both", null);

        ArgumentCaptor<String> mdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(taskRepository).updateResultAndStatus(eq("task-1"), eq(TaskStatus.FAILED), eq("FAILED"), mdCaptor.capture(), jsonCaptor.capture(), any());
        verify(taskRepository).updateErrorAndStatus(eq("task-1"), eq(TaskStatus.FAILED), eq("FAILED"), eq("LLM JSON parse failed"), eq("llm_bad_response"), any());
        assertNotNull(mdCaptor.getValue());
        assertNotNull(jsonCaptor.getValue());
        assertTrue(jsonCaptor.getValue().contains("\"status\":\"failed\""));
    }

    @Test
    void cancelTaskRejectsTerminalFailedTask() {
        TaskEntity task = new TaskEntity();
        task.setId("task-terminal");
        task.setStatus(TaskStatus.FAILED);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        when(taskRepository.findById(eq("task-terminal"))).thenReturn(task);

        ApiException ex = assertThrows(ApiException.class, () -> taskService.cancelTask("task-terminal"));
        assertEquals("TASK_CANNOT_CANCEL", ex.getCode());
    }
}
