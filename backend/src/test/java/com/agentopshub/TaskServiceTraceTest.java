package com.agentopshub;

import com.agentopshub.domain.StepStatus;
import com.agentopshub.domain.StepType;
import com.agentopshub.domain.TaskEntity;
import com.agentopshub.domain.TaskStatus;
import com.agentopshub.domain.TaskStepEntity;
import com.agentopshub.domain.ToolCallLogEntity;
import com.agentopshub.dto.TaskStatusResponse;
import com.agentopshub.dto.TaskTraceResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.TaskRepository;
import com.agentopshub.repository.TaskStepRepository;
import com.agentopshub.repository.ToolCallLogRepository;
import com.agentopshub.service.AgentRuntimeClient;
import com.agentopshub.service.LocalAgentRuntime;
import com.agentopshub.service.TaskService;
import com.agentopshub.service.TaskQueueProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTraceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskStepRepository taskStepRepository;

    @Mock
    private ToolCallLogRepository toolCallLogRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AgentRuntimeClient agentRuntimeClient;

    @Mock
    private LocalAgentRuntime localAgentRuntime;

    @Mock
    private TaskQueueProducer taskQueueProducer;

    private TaskService taskService;

    @Mock
    private ObjectProvider<TaskQueueProducer> taskQueueProducerProvider;

    @BeforeEach
    void setUp() {
        lenient().when(taskQueueProducerProvider.getIfAvailable()).thenReturn(taskQueueProducer);
        taskService = new TaskService(
            taskRepository,
            taskStepRepository,
            toolCallLogRepository,
            redisTemplate,
            new ObjectMapper(),
            agentRuntimeClient,
            localAgentRuntime,
            taskQueueProducerProvider,
            300,
            2,
            true,
            "threadpool",
            2,
            50,
            3,
            5000,
            2,
            "/tmp/agentops_outputs",
            true
        );
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getTaskTraceReturnsOrderedStepsAndToolCalls() {
        TaskEntity task = buildTask("task-1", TaskStatus.SUCCEEDED);
        when(taskRepository.findById("task-1")).thenReturn(task);

        TaskStepEntity step2 = buildStep(2L, 2, StepType.TOOL, StepStatus.SUCCEEDED, null, null);
        TaskStepEntity step1 = buildStep(1L, 1, StepType.PLAN, StepStatus.SUCCEEDED, null, null);
        when(taskStepRepository.findByTaskIdOrdered("task-1")).thenReturn(List.of(step2, step1));

        ToolCallLogEntity call2 = buildCall(2L, 2L, 2, true, null, null);
        ToolCallLogEntity call1 = buildCall(1L, 2L, 1, true, null, null);
        when(toolCallLogRepository.findByTaskIdOrdered("task-1")).thenReturn(List.of(call2, call1));

        TaskTraceResponse trace = taskService.getTaskTrace("task-1");

        assertEquals(2, trace.getSteps().size());
        assertEquals(1, trace.getSteps().get(0).getSeq());
        assertEquals(2, trace.getSteps().get(1).getSeq());
        assertEquals(2, trace.getSteps().get(1).getToolCalls().size());
        assertEquals(1, trace.getSteps().get(1).getToolCalls().get(0).getCallOrder());
        assertEquals(2, trace.getSteps().get(1).getToolCalls().get(1).getCallOrder());
    }

    @Test
    void getTaskTraceIncludesFailureDiagnostics() {
        TaskEntity task = buildTask("task-2", TaskStatus.FAILED);
        when(taskRepository.findById("task-2")).thenReturn(task);

        TaskStepEntity step = buildStep(10L, 2, StepType.TOOL, StepStatus.FAILED, "MOCK_TOOL_FAILURE", "tool failed");
        when(taskStepRepository.findByTaskIdOrdered("task-2")).thenReturn(List.of(step));

        ToolCallLogEntity call = buildCall(100L, 10L, 1, false, "MOCK_TOOL_FAILURE", "tool failed");
        when(toolCallLogRepository.findByTaskIdOrdered("task-2")).thenReturn(List.of(call));

        TaskTraceResponse trace = taskService.getTaskTrace("task-2");

        assertEquals("FAILED", trace.getStatus());
        assertEquals("MOCK_TOOL_FAILURE", trace.getSteps().get(0).getErrorCode());
        assertFalse(trace.getSteps().get(0).getToolCalls().get(0).getSuccess());
        assertEquals("MOCK_TOOL_FAILURE", trace.getSteps().get(0).getToolCalls().get(0).getErrorCode());
    }

    @Test
    void getTaskTraceWithoutAuditThrowsTraceNotAvailable() {
        TaskEntity task = buildTask("task-3", TaskStatus.CREATED);
        when(taskRepository.findById("task-3")).thenReturn(task);
        when(taskStepRepository.findByTaskIdOrdered("task-3")).thenReturn(List.of());
        when(toolCallLogRepository.findByTaskIdOrdered("task-3")).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> taskService.getTaskTrace("task-3"));
        assertEquals("TRACE_NOT_AVAILABLE", ex.getCode());
    }

    @Test
    void getTaskStatusIncludesTraceAvailabilityHint() {
        TaskEntity task = buildTask("task-4", TaskStatus.RUNNING);
        TaskStepEntity runningStep = buildStep(33L, 1, StepType.PLAN, StepStatus.RUNNING, null, null);
        runningStep.setOutputSummary("{\"summary\":\"repair step\",\"round\":1}");
        when(taskRepository.findById("task-4")).thenReturn(task);
        when(taskStepRepository.findByTaskIdOrdered("task-4")).thenReturn(List.of(runningStep));
        when(taskStepRepository.existsByTaskId("task-4")).thenReturn(true);
        when(valueOperations.get(any())).thenReturn(null);

        TaskStatusResponse status = taskService.getTaskStatus("task-4");
        assertTrue(status.isTraceAvailable());
        assertEquals("PLAN", status.getPhase());
        assertEquals(1, status.getCurrentRound());
    }

    @Test
    void getTaskTraceParsesVerifierAndRoundMetadata() {
        TaskEntity task = buildTask("task-5", TaskStatus.SUCCEEDED);
        when(taskRepository.findById("task-5")).thenReturn(task);

        TaskStepEntity step = buildStep(20L, 3, StepType.VERIFY, StepStatus.SUCCEEDED, null, null);
        step.setOutputSummary("{\"summary\":\"verification passed\",\"citations\":[\"doc-1-c-1\"],\"round\":2,\"modelUsage\":{\"model\":\"mock\",\"tokenIn\":2},\"verifierReport\":{\"overallPass\":true},\"diffSummary\":\"updated\"}");
        when(taskStepRepository.findByTaskIdOrdered("task-5")).thenReturn(List.of(step));
        when(toolCallLogRepository.findByTaskIdOrdered("task-5")).thenReturn(List.of());

        TaskTraceResponse trace = taskService.getTaskTrace("task-5");
        assertEquals(1, trace.getSteps().size());
        assertEquals(2, trace.getSteps().get(0).getRound());
        assertEquals("verification passed", trace.getSteps().get(0).getOutputSummary());
        assertNotNull(trace.getSteps().get(0).getVerifierReport());
        assertNotNull(trace.getSteps().get(0).getModelUsage());
    }

    @Test
    void getTaskTraceCancelledWithoutAuditStillReturnsTraceNotAvailable() {
        TaskEntity task = buildTask("task-cancel-empty", TaskStatus.CANCELLED);
        when(taskRepository.findById("task-cancel-empty")).thenReturn(task);
        when(taskStepRepository.findByTaskIdOrdered("task-cancel-empty")).thenReturn(List.of());
        when(toolCallLogRepository.findByTaskIdOrdered("task-cancel-empty")).thenReturn(List.of());

        ApiException ex = assertThrows(ApiException.class, () -> taskService.getTaskTrace("task-cancel-empty"));
        assertEquals("TRACE_NOT_AVAILABLE", ex.getCode());
    }

    @Test
    void getTaskTraceCancelledWithPartialTraceIsDistinguishable() {
        TaskEntity task = buildTask("task-cancel-partial", TaskStatus.CANCELLED);
        when(taskRepository.findById("task-cancel-partial")).thenReturn(task);

        TaskStepEntity partial = buildStep(30L, 2, StepType.EXECUTE, StepStatus.RUNNING, null, null);
        partial.setEndedAt(null);
        when(taskStepRepository.findByTaskIdOrdered("task-cancel-partial")).thenReturn(List.of(partial));
        when(toolCallLogRepository.findByTaskIdOrdered("task-cancel-partial")).thenReturn(List.of());

        TaskTraceResponse trace = taskService.getTaskTrace("task-cancel-partial");
        assertEquals("CANCELLED", trace.getStatus());
        assertTrue(trace.isIncomplete());
        assertEquals(1, trace.getSteps().size());
        assertEquals("RUNNING", trace.getSteps().get(0).getStatus());
    }

    @Test
    void getTaskTraceCancelledDuringRuntimeKeepsOrderedConsistentTimeline() {
        TaskEntity task = buildTask("task-cancel-runtime", TaskStatus.CANCELLED);
        when(taskRepository.findById("task-cancel-runtime")).thenReturn(task);

        TaskStepEntity runningStep = buildStep(42L, 2, StepType.EXECUTE, StepStatus.RUNNING, null, null);
        runningStep.setStartedAt(Instant.parse("2026-03-03T10:00:05Z"));
        runningStep.setEndedAt(null);
        TaskStepEntity doneStep = buildStep(41L, 1, StepType.PLAN, StepStatus.SUCCEEDED, null, null);
        doneStep.setStartedAt(Instant.parse("2026-03-03T10:00:00Z"));
        doneStep.setEndedAt(Instant.parse("2026-03-03T10:00:01Z"));
        when(taskStepRepository.findByTaskIdOrdered("task-cancel-runtime")).thenReturn(List.of(runningStep, doneStep));

        ToolCallLogEntity call2 = buildCall(202L, 42L, 2, true, null, null);
        call2.setCreatedAt(Instant.parse("2026-03-03T10:00:07Z"));
        ToolCallLogEntity call1 = buildCall(201L, 42L, 1, true, null, null);
        call1.setCreatedAt(Instant.parse("2026-03-03T10:00:06Z"));
        when(toolCallLogRepository.findByTaskIdOrdered("task-cancel-runtime")).thenReturn(List.of(call2, call1));

        TaskTraceResponse trace = taskService.getTaskTrace("task-cancel-runtime");
        assertEquals("CANCELLED", trace.getStatus());
        assertTrue(trace.isIncomplete());
        assertEquals(2, trace.getSteps().size());
        assertEquals(1, trace.getSteps().get(0).getSeq());
        assertEquals(2, trace.getSteps().get(1).getSeq());
        assertEquals("SUCCEEDED", trace.getSteps().get(0).getStatus());
        assertEquals("RUNNING", trace.getSteps().get(1).getStatus());
        assertNull(trace.getSteps().get(1).getEndedAt());
        assertEquals(2, trace.getSteps().get(1).getToolCalls().size());
        assertEquals(1, trace.getSteps().get(1).getToolCalls().get(0).getCallOrder());
        assertEquals(2, trace.getSteps().get(1).getToolCalls().get(1).getCallOrder());
    }

    private TaskEntity buildTask(String id, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        task.setStatus(status);
        task.setPrompt("prompt");
        task.setOutputFormat("both");
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }

    private TaskStepEntity buildStep(Long id, int seq, StepType stepType, StepStatus status, String errorCode, String errorMessage) {
        TaskStepEntity step = new TaskStepEntity();
        step.setId(id);
        step.setTaskId("task");
        step.setSeq(seq);
        step.setStepType(stepType);
        step.setStatus(status);
        step.setInputSummary("input");
        step.setOutputSummary("output");
        step.setStartedAt(Instant.parse("2026-03-03T10:00:00Z"));
        step.setEndedAt(Instant.parse("2026-03-03T10:00:01Z"));
        step.setLatencyMs(1000L);
        step.setErrorCode(errorCode);
        step.setErrorMessage(errorMessage);
        step.setRetryCount(0);
        return step;
    }

    private ToolCallLogEntity buildCall(Long id, Long stepId, int order, boolean success, String errorCode, String errorMessage) {
        ToolCallLogEntity call = new ToolCallLogEntity();
        call.setId(id);
        call.setTraceId("trace-task");
        call.setTaskId("task");
        call.setStepId(stepId);
        call.setCallOrder(order);
        call.setToolName("mock_executor");
        call.setRequestSummary("request");
        call.setResponseSummary("response");
        call.setSuccess(success);
        call.setErrorCode(errorCode);
        call.setErrorMessage(errorMessage);
        call.setLatencyMs(200L);
        call.setCreatedAt(Instant.parse("2026-03-03T10:00:00Z").plusMillis(order));
        return call;
    }
}
