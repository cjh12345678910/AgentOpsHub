package com.agentopshub.repository;

import com.agentopshub.domain.ToolCallLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ToolCallLogRepository {

    @Insert("""
        INSERT INTO tool_call_logs(trace_id, task_id, step_id, call_order, tool_name, request_summary, response_summary,
                                   success, error_code, error_message, latency_ms,
                                   policy_decision, deny_reason, required_scope, safety_rule, target_path, size_bytes,
                                   created_at, updated_at)
        VALUES(#{traceId}, #{taskId}, #{stepId}, #{callOrder}, #{toolName}, #{requestSummary}, #{responseSummary},
               #{success}, #{errorCode}, #{errorMessage}, #{latencyMs},
               #{policyDecision}, #{denyReason}, #{requiredScope}, #{safetyRule}, #{targetPath}, #{sizeBytes},
               #{createdAt}, #{updatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ToolCallLogEntity log);

    @Select("""
        SELECT id, trace_id, task_id, step_id, call_order, tool_name, request_summary, response_summary,
               success, error_code, error_message, latency_ms,
               policy_decision, deny_reason, required_scope, safety_rule, target_path, size_bytes,
               created_at, updated_at
        FROM tool_call_logs
        WHERE task_id = #{taskId}
        ORDER BY created_at ASC, call_order ASC, id ASC
        """)
    List<ToolCallLogEntity> findByTaskIdOrdered(@Param("taskId") String taskId);

    @Select("""
        SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END
        FROM tool_call_logs WHERE task_id = #{taskId}
        """)
    boolean existsByTaskId(@Param("taskId") String taskId);

    @Select("""
        SELECT id, trace_id, task_id, step_id, call_order, tool_name, request_summary, response_summary,
               success, error_code, error_message, latency_ms,
               policy_decision, deny_reason, required_scope, safety_rule, target_path, size_bytes,
               created_at, updated_at
        FROM tool_call_logs
        WHERE (#{policyDecision} IS NULL OR policy_decision = #{policyDecision})
          AND (#{fromTs} IS NULL OR created_at >= #{fromTs})
          AND (#{toTs} IS NULL OR created_at <= #{toTs})
        ORDER BY created_at DESC, id DESC
        LIMIT #{limit}
        """)
    List<ToolCallLogEntity> findByPolicyAndTimeRange(@Param("policyDecision") String policyDecision,
                                                     @Param("fromTs") Instant fromTs,
                                                     @Param("toTs") Instant toTs,
                                                     @Param("limit") int limit);
}
