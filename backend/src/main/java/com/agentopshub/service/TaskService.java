package com.agentopshub.service;

import com.agentopshub.domain.StepStatus;
import com.agentopshub.domain.StepType;
import com.agentopshub.domain.TaskEntity;
import com.agentopshub.domain.TaskStatus;
import com.agentopshub.domain.TaskStepEntity;
import com.agentopshub.domain.ToolCallLogEntity;
import com.agentopshub.dto.AgentRunRequest;
import com.agentopshub.dto.AgentRunResponse;
import com.agentopshub.dto.AgentStepResult;
import com.agentopshub.dto.AgentToolCallResult;
import com.agentopshub.dto.CreateTaskRequest;
import com.agentopshub.dto.TaskCreatedResponse;
import com.agentopshub.dto.TaskDispatchMessage;
import com.agentopshub.dto.TaskResultResponse;
import com.agentopshub.dto.TaskStatusResponse;
import com.agentopshub.dto.TaskArtifactResponse;
import com.agentopshub.dto.TaskTraceResponse;
import com.agentopshub.dto.ToolCallTraceResponse;
import com.agentopshub.dto.TraceStepResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.TaskRepository;
import com.agentopshub.repository.TaskStepRepository;
import com.agentopshub.repository.ToolCallLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.agentopshub.security.UserPrincipal;

@Service
public class TaskService {
    private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
        TaskStatus.CREATED, Set.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED),
        TaskStatus.RUNNING, Set.of(TaskStatus.SUCCEEDED, TaskStatus.FAILED, TaskStatus.CANCELLED),
        TaskStatus.SUCCEEDED, Set.of(),
        TaskStatus.FAILED, Set.of(),
        TaskStatus.CANCELLED, Set.of()
    );
    private static final String TASK_STATUS_CACHE_PREFIX = "task:status:";

    private final TaskRepository taskRepository;
    private final TaskStepRepository taskStepRepository;
    private final ToolCallLogRepository toolCallLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentRuntimeClient agentRuntimeClient;
    private final LocalAgentRuntime localAgentRuntime;
    private final TaskQueueProducer taskQueueProducer;
    private final Duration statusCacheTtl;
    private final int runtimeRetryMax;
    private final boolean localFallbackEnabled;
    private final String dispatchMode;
    private final int queueRetryMax;
    private final int queueBaseBackoffMs;
    private final int queueBackoffMultiplier;
    private final Path artifactBaseDir;
    private final boolean artifactDownloadEnabled;
    private final ThreadPoolExecutor dispatchExecutor;
    private final Map<String, Future<?>> dispatchJobs = new ConcurrentHashMap<>();

    public TaskService(TaskRepository taskRepository,
                       TaskStepRepository taskStepRepository,
                       ToolCallLogRepository toolCallLogRepository,
                       StringRedisTemplate redisTemplate,
                       ObjectMapper objectMapper,
                       AgentRuntimeClient agentRuntimeClient,
                       LocalAgentRuntime localAgentRuntime,
                       ObjectProvider<TaskQueueProducer> taskQueueProducerProvider,
                       @Value("${app.cache.task-status-ttl-seconds:300}") long statusCacheTtlSeconds,
                       @Value("${app.agent.runtime.retry-max:2}") int runtimeRetryMax,
                       @Value("${app.agent.runtime.local-fallback-enabled:true}") boolean localFallbackEnabled,
                       @Value("${app.agent.dispatch.mode:threadpool}") String dispatchMode,
                       @Value("${app.agent.dispatch.pool-size:4}") int dispatchPoolSize,
                       @Value("${app.agent.dispatch.queue-capacity:200}") int dispatchQueueCapacity,
                       @Value("${app.task.queue.max-retries:3}") int queueRetryMax,
                       @Value("${app.task.queue.base-backoff-ms:5000}") int queueBaseBackoffMs,
                       @Value("${app.task.queue.backoff-multiplier:2}") int queueBackoffMultiplier,
                       @Value("${app.task.artifacts.base-dir:/tmp/agentops_outputs}") String artifactBaseDir,
                       @Value("${app.task.artifacts.download-enabled:true}") boolean artifactDownloadEnabled) {
        this.taskRepository = taskRepository;
        this.taskStepRepository = taskStepRepository;
        this.toolCallLogRepository = toolCallLogRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.agentRuntimeClient = agentRuntimeClient;
        this.localAgentRuntime = localAgentRuntime;
        this.taskQueueProducer = taskQueueProducerProvider.getIfAvailable();
        this.statusCacheTtl = Duration.ofSeconds(statusCacheTtlSeconds);
        this.runtimeRetryMax = runtimeRetryMax;
        this.localFallbackEnabled = localFallbackEnabled;
        this.dispatchMode = dispatchMode == null ? "threadpool" : dispatchMode.trim().toLowerCase();
        this.queueRetryMax = Math.max(0, queueRetryMax);
        this.queueBaseBackoffMs = Math.max(1000, queueBaseBackoffMs);
        this.queueBackoffMultiplier = Math.max(1, queueBackoffMultiplier);
        this.artifactBaseDir = Paths.get(artifactBaseDir).toAbsolutePath().normalize();
        this.artifactDownloadEnabled = artifactDownloadEnabled;
        int safePoolSize = Math.max(1, dispatchPoolSize);
        int safeQueueCapacity = Math.max(10, dispatchQueueCapacity);
        this.dispatchExecutor = new ThreadPoolExecutor(
            safePoolSize,
            safePoolSize,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(safeQueueCapacity),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @Transactional
    public TaskCreatedResponse createTask(CreateTaskRequest request) {
        TaskEntity task = new TaskEntity();
        Instant now = Instant.now();
        task.setId(UUID.randomUUID().toString());
        task.setPrompt(request.getPrompt());
        task.setDocIdsJson(toDocIdsJson(normalizeDocIds(request.getDocIds())));
        task.setOutputFormat(request.getOutputFormat());
        task.setStatus(TaskStatus.CREATED);
        task.setDispatchStatus("CREATED");
        task.setRetryCount(0);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskRepository.insert(task);
        cacheStatusSnapshot(toStatusResponse(task));

        String userToken = extractCurrentUserToken();
        String userRole = extractCurrentUserRole();
        if ("mq".equals(dispatchMode)) {
            enqueueTask(task.getId());
        } else {
            scheduleDispatch(task.getId(), request.getPrompt(), request.getDocIds(), request.getOutputFormat(), userToken, userRole);
        }
        return new TaskCreatedResponse(task.getId(), TaskStatus.CREATED.name());
    }

    private String extractCurrentUserToken() {
        try {
            org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                jakarta.servlet.http.HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract user token from request context", e);
        }
        return null;
    }

    private String extractCurrentUserRole() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                return "operator";
            }

            Object principal = authentication.getPrincipal();
            if (principal instanceof UserPrincipal userPrincipal) {
                List<String> roles = userPrincipal.getRoles();
                if (roles != null && !roles.isEmpty()) {
                    String role = roles.get(0);
                    if (role != null && !role.isBlank()) {
                        return role.trim().toLowerCase(Locale.ROOT);
                    }
                }
            }

            for (GrantedAuthority authority : authentication.getAuthorities()) {
                if (authority == null || authority.getAuthority() == null) {
                    continue;
                }
                String value = authority.getAuthority().trim();
                if (value.startsWith("ROLE_") && value.length() > 5) {
                    return value.substring(5).toLowerCase(Locale.ROOT);
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to extract user role from security context", ex);
        }
        return "operator";
    }

    @Transactional(readOnly = true)
    public TaskStatusResponse getTaskStatus(String taskId) {
        TaskStatusResponse cached = loadStatusCache(taskId);
        if (cached != null) {
            return cached;
        }
        TaskEntity task = loadTask(taskId);
        TaskStatusResponse response = toStatusResponse(task);
        cacheStatusSnapshot(response);
        return response;
    }

    @Transactional
    public TaskStatusResponse cancelTask(String taskId) {
        TaskEntity task = loadTask(taskId);
        if (task.getStatus().isTerminal()) {
            if (task.getStatus() == TaskStatus.CANCELLED) {
                return toStatusResponse(task);
            }
            throw new ApiException(
                "TASK_CANNOT_CANCEL",
                "Task is already in terminal status",
                Map.of("taskId", taskId, "status", task.getStatus().name()),
                HttpStatus.CONFLICT.value()
            );
        }

        transitionStatus(task, TaskStatus.CANCELLED);
        markDispatchMetadata(taskId, task.getMessageId(), "CANCELLED", safeRetryCount(task), task.getEnqueuedAt(),
            "cancelled_by_user", "TASK_CANCELLED");
        Future<?> job = dispatchJobs.remove(taskId);
        if (job != null) {
            job.cancel(true);
        }
        appendCancellationTrace(taskId, "Cancelled by user request");
        TaskEntity latest = loadTask(taskId);
        return toStatusResponse(latest);
    }

    @Transactional(readOnly = true)
    public TaskResultResponse getTaskResult(String taskId) {
        TaskEntity task = loadTask(taskId);
        if (task.getStatus() != TaskStatus.SUCCEEDED && task.getStatus() != TaskStatus.FAILED) {
            throw new ApiException(
                "RESULT_NOT_READY",
                "Task result is not ready yet",
                Map.of("taskId", taskId, "status", task.getStatus().name()),
                HttpStatus.CONFLICT.value()
            );
        }
        ResultEnvelope resultEnvelope = extractResultEnvelope(task.getResultJson());
        List<TaskArtifactResponse> artifacts = collectArtifacts(taskId, resultEnvelope.artifacts());
        return new TaskResultResponse(
            task.getId(),
            task.getStatus().name(),
            task.getResultMd(),
            task.getResultJson(),
            resultEnvelope.citations,
            resultEnvelope.verifierReport,
            resultEnvelope.repairRounds,
            resultEnvelope.finalDecision,
            resultEnvelope.modelUsage,
            resultEnvelope.parserStage,
            resultEnvelope.rawResponseSnippet,
            resultEnvelope.usageUnavailableReason,
            resultEnvelope.finalAnswer,
            artifacts
        );
    }

    @Transactional(readOnly = true)
    public TaskTraceResponse getTaskTrace(String taskId) {
        TaskEntity task = loadTask(taskId);
        List<TaskStepEntity> steps = new ArrayList<>(taskStepRepository.findByTaskIdOrdered(taskId));
        List<ToolCallLogEntity> toolCalls = new ArrayList<>(toolCallLogRepository.findByTaskIdOrdered(taskId));

        if (steps.isEmpty() && toolCalls.isEmpty()) {
            throw new ApiException(
                "TRACE_NOT_AVAILABLE",
                "Trace is not available for this task yet",
                Map.of("taskId", taskId),
                HttpStatus.NOT_FOUND.value()
            );
        }

        steps.sort(Comparator
            .comparing(TaskStepEntity::getSeq, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(TaskStepEntity::getStartedAt, Comparator.nullsLast(Instant::compareTo))
            .thenComparing(TaskStepEntity::getId, Comparator.nullsLast(Long::compareTo)));

        Map<Long, List<ToolCallLogEntity>> groupedCalls = toolCalls.stream()
            .sorted(Comparator
                .comparing(ToolCallLogEntity::getCreatedAt, Comparator.nullsLast(Instant::compareTo))
                .thenComparing(ToolCallLogEntity::getCallOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(ToolCallLogEntity::getId, Comparator.nullsLast(Long::compareTo)))
            .collect(Collectors.groupingBy(ToolCallLogEntity::getStepId, LinkedHashMap::new, Collectors.toList()));

        List<TraceStepResponse> stepResponses = new ArrayList<>();
        for (TaskStepEntity step : steps) {
            StepEnvelope stepEnvelope = parseStepEnvelope(step.getOutputSummary());
            List<ToolCallTraceResponse> callResponses = groupedCalls.getOrDefault(step.getId(), List.of()).stream()
                .map(call -> new ToolCallTraceResponse(
                    call.getId(),
                    call.getStepId(),
                    call.getCallOrder(),
                    call.getToolName(),
                    call.getRequestSummary(),
                    call.getResponseSummary(),
                    call.getSuccess(),
                    call.getErrorCode(),
                    call.getErrorMessage(),
                    call.getLatencyMs(),
                    call.getTraceId(),
                    call.getCreatedAt(),
                    extractCitations(call.getResponseSummary()),
                    call.getPolicyDecision(),
                    call.getDenyReason(),
                    call.getRequiredScope(),
                    call.getSafetyRule(),
                    call.getTargetPath(),
                    call.getSizeBytes()
                ))
                .toList();

            stepResponses.add(new TraceStepResponse(
                step.getId(),
                step.getSeq(),
                step.getStepType() == null ? null : step.getStepType().name(),
                step.getStatus() == null ? null : step.getStatus().name(),
                step.getInputSummary(),
                stepEnvelope.summary() == null ? step.getOutputSummary() : stepEnvelope.summary(),
                step.getStartedAt(),
                step.getEndedAt(),
                step.getLatencyMs(),
                step.getErrorCode(),
                step.getErrorMessage(),
                step.getRetryCount(),
                stepEnvelope.citations(),
                callResponses,
                stepEnvelope.round(),
                stepEnvelope.modelUsage(),
                stepEnvelope.verifierReport(),
                stepEnvelope.diffSummary(),
                stepEnvelope.parserStage(),
                stepEnvelope.rawResponseSnippet(),
                stepEnvelope.usageUnavailableReason(),
                stepEnvelope.finalAnswerText()
            ));
        }

        String traceId = toolCalls.stream()
            .map(ToolCallLogEntity::getTraceId)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(buildTraceId(taskId));

        boolean incomplete = task.getStatus() == TaskStatus.CANCELLED;
        return new TaskTraceResponse(task.getId(), task.getStatus().name(), traceId, incomplete, stepResponses);
    }

    @Transactional
    public void executeWithDispatch(String taskId, String prompt, List<String> docIds, String outputFormat, String token) {
        executeWithDispatch(taskId, prompt, docIds, outputFormat, token, "operator");
    }

    @Transactional
    public void executeWithDispatch(String taskId, String prompt, List<String> docIds, String outputFormat, String token, String role) {
        TaskEntity task = loadTask(taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) {
            LOG.info("Skip dispatch for cancelled task. taskId={}", taskId);
            return;
        }
        if (task.getStatus() == TaskStatus.CREATED) {
            transitionStatus(task, TaskStatus.RUNNING);
            markDispatchMetadata(taskId, task.getMessageId(), "RUNNING", safeRetryCount(task), task.getEnqueuedAt(), null, null);
        } else if (task.getStatus() != TaskStatus.RUNNING) {
            LOG.info("Skip dispatch for non-runnable task. taskId={}, status={}", taskId, task.getStatus());
            return;
        }
        String traceId = buildTraceId(taskId);

        AgentRunRequest request = new AgentRunRequest(taskId, prompt, normalizeDocIds(docIds), outputFormat);
        request.setToken(token);
        request.setRole(role == null || role.isBlank() ? "operator" : role.trim().toLowerCase(Locale.ROOT));
        AgentRunResponse runResponse = dispatchWithRetry(request);
        persistRunTrace(taskId, traceId, runResponse);

        if (isTaskCancelled(taskId)) {
            LOG.info("Task cancelled after runtime dispatch. ignore result update. taskId={}", taskId);
            return;
        }

        if ("SUCCEEDED".equalsIgnoreCase(runResponse.getStatus())) {
            updateResultAndStatus(taskId, TaskStatus.SUCCEEDED, runResponse.getResultMd(), runResponse.getResultJson());
            return;
        }

        String failMessage = runResponse.getErrorMessage() == null ? "Agent runtime failed" : runResponse.getErrorMessage();
        String failedResultMd = runResponse.getResultMd();
        if (failedResultMd == null || failedResultMd.isBlank()) {
            failedResultMd = "# Task Result\n\n- status: failed\n- reason: " + failMessage;
        }
        String failedResultJson = runResponse.getResultJson();
        if (failedResultJson == null || failedResultJson.isBlank()) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("status", "failed");
            fallback.put("reason", failMessage);
            fallback.put("errorCode", runResponse.getErrorCode());
            fallback.put("errorMessage", failMessage);
            fallback.put("parserStage", runResponse.getParserStage());
            fallback.put("rawResponseSnippet", runResponse.getRawResponseSnippet());
            fallback.put("usageUnavailableReason", runResponse.getUsageUnavailableReason());
            fallback.put("modelUsage", runResponse.getModelUsage());
            try {
                failedResultJson = objectMapper.writeValueAsString(fallback);
            } catch (Exception ignored) {
                failedResultJson = "{\"status\":\"failed\"}";
            }
        }

        updateResultAndStatus(taskId, TaskStatus.FAILED, failedResultMd, failedResultJson);
        if (!isTaskCancelled(taskId)) {
            updateFailureAndStatus(taskId, failMessage, runResponse.getErrorCode());
        }
    }

    @Transactional
    public QueueDispatchResult processQueuedTask(TaskDispatchMessage message) {
        String taskId = message.getTaskId();
        TaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            LOG.warn("Drop unknown queue task message, taskId={}", taskId);
            return QueueDispatchResult.ack();
        }
        if (task.getStatus() == TaskStatus.CANCELLED) {
            markDispatchMetadata(taskId, task.getMessageId(), "CANCELLED", safeRetryCount(task), task.getEnqueuedAt(),
                "cancelled_before_consume", "TASK_CANCELLED");
            appendCancellationTrace(taskId, "Cancelled before worker dispatch");
            return QueueDispatchResult.ack();
        }
        if (task.getStatus().isTerminal()) {
            LOG.info("Skip terminal queued task, taskId={}, status={}", taskId, task.getStatus());
            return QueueDispatchResult.ack();
        }

        int retryCount = safeRetryCount(task);
        String token = extractCurrentUserToken();
        String role = extractCurrentUserRole();
        List<String> docIds = readDocIds(task.getDocIdsJson());
        try {
            executeWithDispatch(task.getId(), task.getPrompt(), docIds, task.getOutputFormat(), token, role);
            return QueueDispatchResult.ack();
        } catch (ApiException ex) {
            String code = ex.getCode();
            if (retryCount < queueRetryMax && isRetryableCode(code)) {
                int nextRetry = retryCount + 1;
                long delay = computeRetryDelayMs(nextRetry);
                markDispatchMetadata(taskId, task.getMessageId(), "RETRYING", nextRetry, task.getEnqueuedAt(), null, code);
                return QueueDispatchResult.retry(nextRetry, delay);
            }
            markDispatchMetadata(taskId, task.getMessageId(), "DLQ", retryCount, task.getEnqueuedAt(), ex.getMessage(), code);
            updateFailureAndStatus(taskId, ex.getMessage(), code);
            return QueueDispatchResult.dlq(retryCount);
        } catch (RuntimeException ex) {
            String errorCode = "TASK_DISPATCH_FAILED";
            if (retryCount < queueRetryMax) {
                int nextRetry = retryCount + 1;
                long delay = computeRetryDelayMs(nextRetry);
                markDispatchMetadata(taskId, task.getMessageId(), "RETRYING", nextRetry, task.getEnqueuedAt(), null, errorCode);
                return QueueDispatchResult.retry(nextRetry, delay);
            }
            markDispatchMetadata(taskId, task.getMessageId(), "DLQ", retryCount, task.getEnqueuedAt(), ex.getMessage(), errorCode);
            updateFailureAndStatus(taskId, ex.getMessage() == null ? "Task dispatch failed" : ex.getMessage(), errorCode);
            return QueueDispatchResult.dlq(retryCount);
        }
    }

    private void scheduleDispatch(String taskId, String prompt, List<String> docIds, String outputFormat, String token, String role) {
        Runnable runnable = () -> {
            try {
                executeWithDispatch(taskId, prompt, docIds, outputFormat, token, role);
            } catch (RuntimeException ex) {
                LOG.error("Task dispatch failed unexpectedly. taskId={}", taskId, ex);
                if (!isTaskCancelled(taskId)) {
                    try {
                        updateFailureAndStatus(taskId, ex.getMessage() == null ? "Task dispatch failed" : ex.getMessage(), "TASK_DISPATCH_FAILED");
                    } catch (Exception nested) {
                        LOG.warn("Failed to update task failure status after dispatch exception. taskId={}", taskId, nested);
                    }
                }
            } finally {
                dispatchJobs.remove(taskId);
            }
        };

        Runnable submitAction = () -> {
            try {
                dispatchJobs.put(taskId, dispatchExecutor.submit(runnable));
            } catch (RejectedExecutionException ex) {
                LOG.error("Dispatch queue is full. taskId={}", taskId, ex);
                if (!isTaskCancelled(taskId)) {
                    updateFailureAndStatus(taskId, "Dispatch queue is full", "DISPATCH_QUEUE_FULL");
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitAction.run();
                }
            });
            return;
        }
        submitAction.run();
    }

    @PreDestroy
    public void shutdownDispatchExecutor() {
        dispatchExecutor.shutdown();
    }

    private boolean isTaskCancelled(String taskId) {
        try {
            TaskEntity task = taskRepository.findById(taskId);
            return task != null && task.getStatus() == TaskStatus.CANCELLED;
        } catch (Exception ex) {
            LOG.warn("Failed to check cancelled status. taskId={}", taskId, ex);
            return false;
        }
    }

    private AgentRunResponse dispatchWithRetry(AgentRunRequest request) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= runtimeRetryMax + 1; attempt++) {
            try {
                AgentRunResponse response = agentRuntimeClient.run(request);
                LOG.info("Agent runtime dispatch succeeded. taskId={}, attempt={}", request.getTaskId(), attempt);
                return response;
            } catch (RuntimeException ex) {
                lastError = ex;
                LOG.warn("Agent runtime dispatch failed. taskId={}, attempt={}, reason={}",
                    request.getTaskId(), attempt, ex.getMessage());
            }
        }

        if (localFallbackEnabled) {
            LOG.warn("Agent runtime dispatch exhausted retries, fallback to local runtime. taskId={}", request.getTaskId());
            return localAgentRuntime.run(request);
        }

        throw new ApiException(
            "AGENT_DISPATCH_FAILED",
            "Agent runtime dispatch failed",
            Map.of("taskId", request.getTaskId(), "reason", lastError == null ? "unknown" : lastError.getMessage()),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
    }

    private void persistRunTrace(String taskId, String traceId, AgentRunResponse runResponse) {
        if (runResponse == null || runResponse.getSteps() == null) {
            return;
        }

        for (AgentStepResult stepResult : runResponse.getSteps()) {
            StepType stepType = parseStepType(stepResult.getStepType());
            StepStatus stepStatus = parseStepStatus(stepResult.getStatus());
            TaskStepEntity step = startStep(taskId,
                stepResult.getSeq() == null ? 0 : stepResult.getSeq(),
                stepType,
                stepResult.getInputSummary(),
                null);

            String outputSummary = withStepEnvelope(
                stepResult.getOutputSummary(),
                stepResult.getCitations(),
                stepResult.getRound(),
                stepResult.getModelUsage(),
                stepResult.getVerifierReport(),
                stepResult.getDiffSummary(),
                stepResult.getParserStage(),
                stepResult.getRawResponseSnippet(),
                stepResult.getUsageUnavailableReason(),
                stepResult.getFinalAnswerText()
            );
            completeStep(step,
                stepStatus,
                outputSummary,
                stepResult.getErrorCode(),
                stepResult.getErrorMessage(),
                stepResult.getRetryCount() == null ? 0 : stepResult.getRetryCount());

            List<AgentToolCallResult> toolCalls = stepResult.getToolCalls() == null ? List.of() : stepResult.getToolCalls();
            for (AgentToolCallResult callResult : toolCalls) {
                FileWriteAudit fileWriteAudit = extractFileWriteAudit(
                    callResult.getToolName(),
                    callResult.getRequestSummary(),
                    callResult.getResponseSummary()
                );
                writeToolCall(
                    traceId,
                    taskId,
                    step,
                    callResult.getCallOrder() == null ? 1 : callResult.getCallOrder(),
                    callResult.getToolName(),
                    callResult.getRequestSummary(),
                    withStepEnvelope(callResult.getResponseSummary(), callResult.getCitations(), null, null, null, null, null, null, null, null),
                    callResult.getSuccess() == null || callResult.getSuccess(),
                    callResult.getErrorCode(),
                    callResult.getErrorMessage(),
                    callResult.getLatencyMs() == null ? 0L : callResult.getLatencyMs(),
                    callResult.getPolicyDecision(),
                    callResult.getDenyReason(),
                    callResult.getRequiredScope(),
                    callResult.getSafetyRule(),
                    fileWriteAudit.targetPath(),
                    fileWriteAudit.sizeBytes()
                );
            }
        }
    }

    private StepType parseStepType(String stepType) {
        if (stepType == null || stepType.isBlank()) {
            return StepType.TOOL;
        }
        try {
            return StepType.valueOf(stepType.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return StepType.TOOL;
        }
    }

    private StepStatus parseStepStatus(String status) {
        if (status == null || status.isBlank()) {
            return StepStatus.SUCCEEDED;
        }
        try {
            return StepStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return StepStatus.FAILED;
        }
    }

    private List<String> normalizeDocIds(List<String> docIds) {
        if (docIds == null) {
            return List.of();
        }
        return docIds.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    private String withStepEnvelope(String summary,
                                    List<String> citations,
                                    Integer round,
                                    Map<String, Object> modelUsage,
                                    Map<String, Object> verifierReport,
                                    String diffSummary,
                                    String parserStage,
                                    String rawResponseSnippet,
                                    String usageUnavailableReason,
                                    String finalAnswerText) {
        if ((citations == null || citations.isEmpty())
            && round == null
            && modelUsage == null
            && verifierReport == null
            && (diffSummary == null || diffSummary.isBlank())
            && (parserStage == null || parserStage.isBlank())
            && (rawResponseSnippet == null || rawResponseSnippet.isBlank())
            && (usageUnavailableReason == null || usageUnavailableReason.isBlank())
            && (finalAnswerText == null || finalAnswerText.isBlank())) {
            return trimSummary(summary);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("summary", summary);
        envelope.put("citations", citations);
        envelope.put("round", round);
        envelope.put("modelUsage", modelUsage);
        envelope.put("verifierReport", verifierReport);
        envelope.put("diffSummary", diffSummary);
        envelope.put("parserStage", parserStage);
        envelope.put("rawResponseSnippet", rawResponseSnippet);
        envelope.put("usageUnavailableReason", usageUnavailableReason);
        envelope.put("finalAnswerText", finalAnswerText);
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception ex) {
            return trimSummary(summary);
        }
    }

    private List<String> extractCitations(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode citations = node.get("citations");
            if (citations == null || !citations.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            citations.forEach(item -> {
                if (item.isTextual()) {
                    values.add(item.asText());
                }
            });
            return values;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private ResultEnvelope extractResultEnvelope(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ResultEnvelope(List.of(), null, List.of(), null, List.of(), null, null, null, null, List.of());
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return new ResultEnvelope(
                readStringList(node.get("citations")),
                readObjectMap(node.get("verifierReport")),
                readObjectList(node.get("repairRounds")),
                readText(node.get("finalDecision")),
                readObjectList(node.get("modelUsage")),
                readText(node.get("parserStage")),
                readText(node.get("rawResponseSnippet")),
                readText(node.get("usageUnavailableReason")),
                readObjectMap(node.get("finalAnswer")),
                readObjectList(node.get("artifacts"))
            );
        } catch (Exception ignored) {
            return new ResultEnvelope(extractCitations(raw), null, List.of(), null, List.of(), null, null, null, null, List.of());
        }
    }

    private StepEnvelope parseStepEnvelope(String raw) {
        if (raw == null || raw.isBlank()) {
            return new StepEnvelope(extractSummary(raw), List.of(), null, null, null, null, null, null, null, null);
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (!node.isObject() || !node.has("summary")) {
                return new StepEnvelope(extractSummary(raw), extractCitations(raw), null, null, null, null, null, null, null, null);
            }
            return new StepEnvelope(
                readText(node.get("summary")),
                readStringList(node.get("citations")),
                readInteger(node.get("round")),
                readObjectMap(node.get("modelUsage")),
                readObjectMap(node.get("verifierReport")),
                readText(node.get("diffSummary")),
                readText(node.get("parserStage")),
                readText(node.get("rawResponseSnippet")),
                readText(node.get("usageUnavailableReason")),
                readText(node.get("finalAnswerText"))
            );
        } catch (Exception ignored) {
            return new StepEnvelope(extractSummary(raw), extractCitations(raw), null, null, null, null, null, null, null, null);
        }
    }

    private String extractSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.isObject() && node.has("summary")) {
                return readText(node.get("summary"));
            }
        } catch (Exception ignored) {
            // 非 JSON 直接回传原文。
        }
        return raw;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private Map<String, Object> readObjectMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private List<Map<String, Object>> readObjectList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, new TypeReference<List<Map<String, Object>>>() {});
    }

    private Integer readInteger(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asInt();
    }

    private String readText(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return null;
        }
        return node.asText();
    }

    private List<TaskArtifactResponse> collectArtifacts(String taskId, List<Map<String, Object>> resultArtifacts) {
        List<TaskArtifactResponse> merged = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (resultArtifacts != null) {
            for (Map<String, Object> artifact : resultArtifacts) {
                TaskArtifactResponse mapped = toArtifactResponse(taskId, artifact, null, "result_payload");
                if (mapped == null) {
                    continue;
                }
                String key = mapped.getRelativePath() + "|" + mapped.getSourceTool();
                if (seen.add(key)) {
                    merged.add(mapped);
                }
            }
        }

        List<ToolCallLogEntity> logs = toolCallLogRepository.findByTaskIdOrdered(taskId);
        for (ToolCallLogEntity log : logs) {
            if (!Boolean.TRUE.equals(log.getSuccess())) {
                continue;
            }
            if (!"file_write".equalsIgnoreCase(log.getToolName())) {
                continue;
            }
            Map<String, Object> payload = extractArtifactPayloadFromToolResponse(log.getResponseSummary());
            TaskArtifactResponse mapped = toArtifactResponse(taskId, payload, log.getCreatedAt(), "file_write");
            if (mapped == null) {
                continue;
            }
            String key = mapped.getRelativePath() + "|" + mapped.getSourceTool();
            if (seen.add(key)) {
                merged.add(mapped);
            }
        }
        return merged;
    }

    private TaskArtifactResponse toArtifactResponse(String taskId,
                                                    Map<String, Object> payload,
                                                    Instant createdAt,
                                                    String defaultSourceTool) {
        if (payload == null) {
            return null;
        }
        String relativePath = asString(payload.get("relativePath"));
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        String name = asString(payload.get("name"));
        if (name == null || name.isBlank()) {
            Path path = Paths.get(relativePath);
            name = path.getFileName() == null ? relativePath : path.getFileName().toString();
        }
        Long sizeBytes = asLong(payload.get("sizeBytes"));
        String sourceTool = asString(payload.get("sourceTool"));
        if (sourceTool == null || sourceTool.isBlank()) {
            sourceTool = defaultSourceTool;
        }
        String downloadUrl = "/api/tasks/" + taskId + "/artifacts/download?path="
            + URLEncoder.encode(relativePath, StandardCharsets.UTF_8);
        Instant ts = createdAt == null ? Instant.now() : createdAt;
        return new TaskArtifactResponse(name, relativePath, downloadUrl, sizeBytes, ts, sourceTool);
    }

    private Map<String, Object> extractArtifactPayloadFromToolResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            if (node.has("summary") && node.get("summary").isTextual()) {
                String summaryText = node.get("summary").asText();
                JsonNode summaryNode = objectMapper.readTree(summaryText);
                if (summaryNode.isObject()) {
                    return objectMapper.convertValue(summaryNode, new TypeReference<Map<String, Object>>() {});
                }
            }
            if (node.isObject()) {
                return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Transactional(readOnly = true)
    public ArtifactPayload downloadArtifact(String taskId, String relativePath) {
        if (!artifactDownloadEnabled) {
            throw new ApiException(
                "ARTIFACT_DOWNLOAD_DISABLED",
                "Artifact download is disabled",
                Map.of("taskId", taskId),
                HttpStatus.CONFLICT.value()
            );
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new ApiException(
                "ARTIFACT_PATH_REQUIRED",
                "Artifact path is required",
                Map.of("taskId", taskId),
                HttpStatus.BAD_REQUEST.value()
            );
        }

        TaskEntity task = loadTask(taskId);
        ResultEnvelope resultEnvelope = extractResultEnvelope(task.getResultJson());
        List<TaskArtifactResponse> artifacts = collectArtifacts(taskId, resultEnvelope.artifacts());
        boolean declared = artifacts.stream().anyMatch(item -> relativePath.equals(item.getRelativePath()));
        if (!declared) {
            throw new ApiException(
                "ARTIFACT_NOT_FOUND",
                "Artifact not found for this task",
                Map.of("taskId", taskId, "path", relativePath),
                HttpStatus.NOT_FOUND.value()
            );
        }

        Path target = artifactBaseDir.resolve(relativePath).normalize();
        if (!target.startsWith(artifactBaseDir)) {
            throw new ApiException(
                "ARTIFACT_PATH_OUT_OF_SCOPE",
                "Artifact path escapes allowed base directory",
                Map.of("taskId", taskId, "path", relativePath),
                HttpStatus.BAD_REQUEST.value()
            );
        }
        if (!Files.exists(target) || !Files.isRegularFile(target)) {
            throw new ApiException(
                "ARTIFACT_FILE_MISSING",
                "Artifact file does not exist on server",
                Map.of("taskId", taskId, "path", relativePath),
                HttpStatus.NOT_FOUND.value()
            );
        }
        try {
            byte[] content = Files.readAllBytes(target);
            String fileName = target.getFileName() == null ? "artifact.bin" : target.getFileName().toString();
            return new ArtifactPayload(fileName, content, detectContentType(fileName));
        } catch (IOException ex) {
            throw new ApiException(
                "ARTIFACT_READ_ERROR",
                "Failed to read artifact file",
                Map.of("taskId", taskId, "path", relativePath),
                HttpStatus.INTERNAL_SERVER_ERROR.value()
            );
        }
    }

    private String detectContentType(String fileName) {
        String lowered = fileName.toLowerCase();
        if (lowered.endsWith(".md")) {
            return "text/markdown; charset=utf-8";
        }
        if (lowered.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        if (lowered.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lowered.endsWith(".csv")) {
            return "text/csv; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private FileWriteAudit extractFileWriteAudit(String toolName, String requestSummary, String responseSummary) {
        if (!"file_write".equalsIgnoreCase(toolName)) {
            return new FileWriteAudit(null, null);
        }

        String targetPath = null;
        Long sizeBytes = null;
        try {
            if (requestSummary != null && !requestSummary.isBlank()) {
                JsonNode requestNode = objectMapper.readTree(requestSummary);
                targetPath = readText(requestNode.get("path"));
            }
        } catch (Exception ignored) {
            // ignore malformed payload from runtime
        }

        Map<String, Object> payload = extractArtifactPayloadFromToolResponse(responseSummary);
        if (payload != null) {
            String relativePath = asString(payload.get("relativePath"));
            if (relativePath != null && !relativePath.isBlank()) {
                targetPath = relativePath;
            }
            sizeBytes = asLong(payload.get("sizeBytes"));
        }
        return new FileWriteAudit(targetPath, sizeBytes);
    }

    private void transitionStatus(TaskEntity task, TaskStatus next) {
        TaskStatus current = task.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new ApiException(
                "STATUS_CONFLICT",
                "Invalid status transition",
                Map.of("from", current.name(), "to", next.name()),
                HttpStatus.CONFLICT.value()
            );
        }
        Instant now = Instant.now();
        taskRepository.updateStatus(task.getId(), next, normalizeDispatchStatus(next), now);
        task.setStatus(next);
        task.setDispatchStatus(normalizeDispatchStatus(next));
        task.setUpdatedAt(now);
        cacheStatusSnapshot(toStatusResponse(task));
    }

    private void updateResultAndStatus(String taskId, TaskStatus status, String resultMd, String resultJson) {
        Instant now = Instant.now();
        taskRepository.updateResultAndStatus(taskId, status, normalizeDispatchStatus(status), resultMd, resultJson, now);
        TaskEntity latestTask = loadTask(taskId);
        cacheStatusSnapshot(toStatusResponse(latestTask));
    }

    private void updateFailureAndStatus(String taskId, String errorMessage, String errorCode) {
        Instant now = Instant.now();
        taskRepository.updateErrorAndStatus(taskId, TaskStatus.FAILED, "FAILED", errorMessage, errorCode, now);
        TaskEntity latestTask = loadTask(taskId);
        cacheStatusSnapshot(toStatusResponse(latestTask));
    }

    private TaskEntity loadTask(String taskId) {
        TaskEntity task = taskRepository.findById(taskId);
        if (task == null) {
            throw new ApiException(
                "TASK_NOT_FOUND",
                "Task not found",
                Map.of("taskId", taskId),
                HttpStatus.NOT_FOUND.value()
            );
        }
        return task;
    }

    private void enqueueTask(String taskId) {
        ensureMqProducerAvailable();
        TaskEntity task = loadTask(taskId);
        String messageId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try {
            TaskDispatchMessage payload = new TaskDispatchMessage(taskId, messageId, 0, now.toEpochMilli());
            taskQueueProducer.publish(payload);
            markDispatchMetadata(taskId, messageId, "QUEUED", 0, now, null, null);
        } catch (RuntimeException ex) {
            markDispatchMetadata(taskId, messageId, "FAILED", 0, null, ex.getMessage(), "TASK_ENQUEUE_FAILED");
            updateFailureAndStatus(taskId, "Task enqueue failed: " + ex.getMessage(), "TASK_ENQUEUE_FAILED");
            throw new ApiException(
                "TASK_ENQUEUE_FAILED",
                "Failed to enqueue task",
                Map.of("taskId", taskId, "reason", ex.getMessage() == null ? "unknown" : ex.getMessage()),
                HttpStatus.SERVICE_UNAVAILABLE.value()
            );
        }
    }

    private void markDispatchMetadata(String taskId,
                                      String messageId,
                                      String dispatchStatus,
                                      int retryCount,
                                      Instant enqueuedAt,
                                      String dlqReason,
                                      String lastErrorCode) {
        taskRepository.updateDispatchMetadata(taskId, messageId, dispatchStatus, retryCount, enqueuedAt, dlqReason, lastErrorCode, Instant.now());
    }

    private int safeRetryCount(TaskEntity task) {
        return task.getRetryCount() == null ? 0 : Math.max(task.getRetryCount(), 0);
    }

    private boolean isRetryableCode(String code) {
        if (code == null || code.isBlank()) {
            return true;
        }
        return !"PAYLOAD_VALIDATION_FAILED".equalsIgnoreCase(code)
            && !"TASK_NOT_FOUND".equalsIgnoreCase(code)
            && !"TASK_CANCELLED".equalsIgnoreCase(code);
    }

    private long computeRetryDelayMs(int retryCount) {
        long delay = queueBaseBackoffMs;
        for (int i = 1; i < retryCount; i++) {
            delay *= queueBackoffMultiplier;
            if (delay > 120_000L) {
                return 120_000L;
            }
        }
        return delay;
    }

    private String normalizeDispatchStatus(TaskStatus status) {
        if (status == null) {
            return "CREATED";
        }
        return switch (status) {
            case CREATED -> "CREATED";
            case RUNNING -> "RUNNING";
            case SUCCEEDED -> "SUCCEEDED";
            case FAILED -> "FAILED";
            case CANCELLED -> "CANCELLED";
        };
    }

    private String toDocIdsJson(List<String> docIds) {
        try {
            return objectMapper.writeValueAsString(docIds == null ? List.of() : docIds);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readDocIds(String docIdsJson) {
        if (docIdsJson == null || docIdsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(docIdsJson, new TypeReference<List<String>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void appendCancellationTrace(String taskId, String reason) {
        TaskStepEntity step = startStep(taskId, 9999, StepType.EXECUTE, "cancel", reason);
        completeStep(step, StepStatus.FAILED, reason, "TASK_CANCELLED", reason, 0);
    }

    @Transactional(readOnly = true)
    public List<TaskEntity> listDlqTasks(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return taskRepository.findDlqTasks(safeLimit);
    }

    @Transactional
    public void replayDlqTask(String taskId) {
        ensureMqProducerAvailable();
        TaskEntity task = loadTask(taskId);
        if (!"DLQ".equalsIgnoreCase(task.getDispatchStatus())) {
            throw new ApiException(
                "TASK_NOT_IN_DLQ",
                "Task is not in DLQ status",
                Map.of("taskId", taskId, "dispatchStatus", task.getDispatchStatus() == null ? "UNKNOWN" : task.getDispatchStatus()),
                HttpStatus.CONFLICT.value()
            );
        }
        String messageId = UUID.randomUUID().toString();
        taskQueueProducer.publish(new TaskDispatchMessage(taskId, messageId, 0, System.currentTimeMillis()));
        markDispatchMetadata(taskId, messageId, "QUEUED", 0, Instant.now(), null, null);
        taskRepository.updateStatus(taskId, TaskStatus.CREATED, "QUEUED", Instant.now());
    }

    @Transactional
    public void discardDlqTask(String taskId, String reason) {
        TaskEntity task = loadTask(taskId);
        if (!"DLQ".equalsIgnoreCase(task.getDispatchStatus())) {
            throw new ApiException(
                "TASK_NOT_IN_DLQ",
                "Task is not in DLQ status",
                Map.of("taskId", taskId, "dispatchStatus", task.getDispatchStatus() == null ? "UNKNOWN" : task.getDispatchStatus()),
                HttpStatus.CONFLICT.value()
            );
        }
        String discardReason = reason == null || reason.isBlank() ? "discarded_by_admin" : reason;
        markDispatchMetadata(taskId, task.getMessageId(), "DISCARDED", safeRetryCount(task), task.getEnqueuedAt(), discardReason, "DLQ_DISCARDED");
    }

    private void ensureMqProducerAvailable() {
        if (taskQueueProducer == null) {
            throw new ApiException(
                "MQ_NOT_ENABLED",
                "MQ dispatch mode is not enabled",
                Map.of("dispatchMode", dispatchMode),
                HttpStatus.CONFLICT.value()
            );
        }
    }

    private TaskStatusResponse toStatusResponse(TaskEntity task) {
        PhaseInfo phaseInfo = resolvePhaseInfo(task.getId());
        return new TaskStatusResponse(
            task.getId(),
            task.getStatus().name(),
            task.getPrompt(),
            task.getOutputFormat(),
            task.getDispatchStatus(),
            safeRetryCount(task),
            phaseInfo.phase,
            phaseInfo.phaseStatus,
            phaseInfo.currentRound,
            task.getCreatedAt(),
            task.getUpdatedAt(),
            hasTraceData(task.getId())
        );
    }

    private PhaseInfo resolvePhaseInfo(String taskId) {
        List<TaskStepEntity> steps = taskStepRepository.findByTaskIdOrdered(taskId);
        if (steps == null || steps.isEmpty()) {
            return new PhaseInfo(null, null, null);
        }
        TaskStepEntity latest = steps.stream()
            .sorted(Comparator.comparing(TaskStepEntity::getSeq, Comparator.nullsLast(Integer::compareTo)).reversed())
            .findFirst()
            .orElse(steps.get(0));
        StepEnvelope latestEnvelope = parseStepEnvelope(latest.getOutputSummary());
        return new PhaseInfo(
            latest.getStepType() == null ? null : latest.getStepType().name(),
            latest.getStatus() == null ? null : latest.getStatus().name(),
            latestEnvelope.round()
        );
    }

    private TaskStepEntity startStep(String taskId, int seq, StepType stepType, String inputSummary, String outputSummary) {
        TaskStepEntity step = new TaskStepEntity();
        Instant now = Instant.now();
        step.setTaskId(taskId);
        step.setSeq(seq);
        step.setStepType(stepType);
        step.setStatus(StepStatus.RUNNING);
        step.setInputSummary(trimSummary(inputSummary));
        step.setOutputSummary(trimSummary(outputSummary));
        step.setStartedAt(now);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        step.setRetryCount(0);

        try {
            taskStepRepository.insert(step);
        } catch (Exception ex) {
            // 审计写入失败时不中断主流程，避免任务因为可观测性组件失败而不可用。
            LOG.warn("Failed to insert step audit log, taskId={}, seq={}, stepType={}", taskId, seq, stepType, ex);
            return null;
        }
        return step;
    }

    private void completeStep(TaskStepEntity step,
                              StepStatus status,
                              String outputSummary,
                              String errorCode,
                              String errorMessage,
                              int retryCount) {
        if (step == null || step.getId() == null) {
            return;
        }
        Instant end = Instant.now();
        long latency = Duration.between(step.getStartedAt(), end).toMillis();
        try {
            taskStepRepository.updateOutcome(
                step.getId(),
                status,
                trimSummary(outputSummary),
                end,
                latency,
                errorCode,
                trimSummary(errorMessage),
                retryCount,
                end
            );
        } catch (Exception ex) {
            LOG.warn("Failed to update step audit log, stepId={}", step.getId(), ex);
        }
    }

    private void writeToolCall(String traceId,
                               String taskId,
                               TaskStepEntity step,
                               int callOrder,
                               String toolName,
                               String requestSummary,
                               String responseSummary,
                               boolean success,
                               String errorCode,
                               String errorMessage,
                               long latencyMs,
                               String policyDecision,
                               String denyReason,
                               String requiredScope,
                               String safetyRule,
                               String targetPath,
                               Long sizeBytes) {
        if (step == null || step.getId() == null) {
            return;
        }

        ToolCallLogEntity log = new ToolCallLogEntity();
        Instant now = Instant.now();
        log.setTraceId(traceId);
        log.setTaskId(taskId);
        log.setStepId(step.getId());
        log.setCallOrder(callOrder);
        log.setToolName(toolName);
        log.setRequestSummary(trimSummary(requestSummary));
        log.setResponseSummary(trimSummary(responseSummary));
        log.setSuccess(success);
        log.setErrorCode(errorCode);
        log.setErrorMessage(trimSummary(errorMessage));
        log.setLatencyMs(latencyMs);
        log.setPolicyDecision(policyDecision);
        log.setDenyReason(trimSummary(denyReason));
        log.setRequiredScope(trimSummary(requiredScope));
        log.setSafetyRule(trimSummary(safetyRule));
        log.setTargetPath(trimSummary(targetPath));
        log.setSizeBytes(sizeBytes);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);

        try {
            toolCallLogRepository.insert(log);
        } catch (Exception ex) {
            LOG.warn("Failed to insert tool call audit log, taskId={}, stepId={}", taskId, step.getId(), ex);
        }
    }

    private boolean hasTraceData(String taskId) {
        try {
            return taskStepRepository.existsByTaskId(taskId) || toolCallLogRepository.existsByTaskId(taskId);
        } catch (Exception ex) {
            LOG.warn("Failed to check trace availability, taskId={}", taskId, ex);
            return false;
        }
    }

    private void cacheStatusSnapshot(TaskStatusResponse response) {
        String key = TASK_STATUS_CACHE_PREFIX + response.getTaskId();
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, payload, statusCacheTtl);
        } catch (Exception ignored) {
            // Redis/序列化异常时降级到 DB，不中断主流程。
        }
    }

    private TaskStatusResponse loadStatusCache(String taskId) {
        String key = TASK_STATUS_CACHE_PREFIX + taskId;
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return null;
            }
            return objectMapper.readValue(payload, TaskStatusResponse.class);
        } catch (Exception ignored) {
            redisTemplate.delete(key);
            return null;
        }
    }

    private String buildTraceId(String taskId) {
        return "trace-" + taskId;
    }

    private String trimSummary(String text) {
        if (text == null) {
            return null;
        }
        int maxLen = 1024;
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...(truncated)";
    }

    private record PhaseInfo(String phase, String phaseStatus, Integer currentRound) {}
    public record QueueDispatchResult(QueueAction action, int retryCount, long retryDelayMs) {
        public static QueueDispatchResult ack() {
            return new QueueDispatchResult(QueueAction.ACK, 0, 0);
        }

        public static QueueDispatchResult retry(int retryCount, long retryDelayMs) {
            return new QueueDispatchResult(QueueAction.RETRY, retryCount, retryDelayMs);
        }

        public static QueueDispatchResult dlq(int retryCount) {
            return new QueueDispatchResult(QueueAction.DLQ, retryCount, 0);
        }
    }
    public enum QueueAction {
        ACK,
        RETRY,
        DLQ
    }
    private record StepEnvelope(String summary,
                                List<String> citations,
                                Integer round,
                                Map<String, Object> modelUsage,
                                Map<String, Object> verifierReport,
                                String diffSummary,
                                String parserStage,
                                String rawResponseSnippet,
                                String usageUnavailableReason,
                                String finalAnswerText) {}
    private record ResultEnvelope(List<String> citations,
                                  Map<String, Object> verifierReport,
                                  List<Map<String, Object>> repairRounds,
                                  String finalDecision,
                                  List<Map<String, Object>> modelUsage,
                                  String parserStage,
                                  String rawResponseSnippet,
                                  String usageUnavailableReason,
                                  Map<String, Object> finalAnswer,
                                  List<Map<String, Object>> artifacts) {}
    private record FileWriteAudit(String targetPath, Long sizeBytes) {}
    public record ArtifactPayload(String fileName, byte[] content, String contentType) {}
}
