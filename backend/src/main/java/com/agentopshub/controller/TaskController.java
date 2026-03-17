package com.agentopshub.controller;

import com.agentopshub.dto.CreateTaskRequest;
import com.agentopshub.dto.DlqTaskItemResponse;
import com.agentopshub.dto.DlqTaskListResponse;
import com.agentopshub.dto.TaskCreatedResponse;
import com.agentopshub.dto.TaskResultResponse;
import com.agentopshub.dto.TaskStatusResponse;
import com.agentopshub.dto.TaskTraceResponse;
import com.agentopshub.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public TaskCreatedResponse createTask(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    @GetMapping("/{taskId}")
    public TaskStatusResponse getStatus(@PathVariable String taskId) {
        return taskService.getTaskStatus(taskId);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskStatusResponse cancelTask(@PathVariable String taskId) {
        return taskService.cancelTask(taskId);
    }

    @GetMapping("/{taskId}/result")
    public TaskResultResponse getResult(@PathVariable String taskId) {
        return taskService.getTaskResult(taskId);
    }

    @GetMapping("/{taskId}/trace")
    public TaskTraceResponse getTrace(@PathVariable String taskId) {
        return taskService.getTaskTrace(taskId);
    }

    @GetMapping("/{taskId}/artifacts/download")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable String taskId,
                                                   @RequestParam("path") String relativePath) {
        TaskService.ArtifactPayload payload = taskService.downloadArtifact(taskId, relativePath);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
            .contentType(MediaType.parseMediaType(payload.contentType()))
            .body(payload.content());
    }

    @GetMapping("/dlq")
    public DlqTaskListResponse listDlqTasks(@RequestParam(defaultValue = "20") int limit) {
        return new DlqTaskListResponse(taskService.listDlqTasks(limit).stream()
            .map(task -> new DlqTaskItemResponse(
                task.getId(),
                task.getStatus() == null ? null : task.getStatus().name(),
                task.getDispatchStatus(),
                task.getRetryCount(),
                task.getDlqReason(),
                task.getLastErrorCode(),
                task.getUpdatedAt()
            ))
            .toList());
    }

    @PostMapping("/dlq/{taskId}/replay")
    public TaskStatusResponse replayDlqTask(@PathVariable String taskId) {
        taskService.replayDlqTask(taskId);
        return taskService.getTaskStatus(taskId);
    }

    @PostMapping("/dlq/{taskId}/discard")
    public TaskStatusResponse discardDlqTask(@PathVariable String taskId,
                                             @RequestParam(defaultValue = "discarded_by_admin") String reason) {
        taskService.discardDlqTask(taskId, reason);
        return taskService.getTaskStatus(taskId);
    }
}
