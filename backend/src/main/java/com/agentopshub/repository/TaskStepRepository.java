package com.agentopshub.repository;

import com.agentopshub.domain.StepStatus;
import com.agentopshub.domain.TaskStepEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface TaskStepRepository {

    @Insert("""
        INSERT INTO task_steps(task_id, seq, step_type, status, input_summary, output_summary, started_at, ended_at, latency_ms, error_code, error_message, retry_count, created_at, updated_at)
        VALUES(#{taskId}, #{seq}, #{stepType}, #{status}, #{inputSummary}, #{outputSummary}, #{startedAt}, #{endedAt}, #{latencyMs}, #{errorCode}, #{errorMessage}, #{retryCount}, #{createdAt}, #{updatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskStepEntity step);

    @Update("""
        UPDATE task_steps
        SET status = #{status},
            output_summary = #{outputSummary},
            ended_at = #{endedAt},
            latency_ms = #{latencyMs},
            error_code = #{errorCode},
            error_message = #{errorMessage},
            retry_count = #{retryCount},
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    int updateOutcome(@Param("id") Long id,
                      @Param("status") StepStatus status,
                      @Param("outputSummary") String outputSummary,
                      @Param("endedAt") Instant endedAt,
                      @Param("latencyMs") Long latencyMs,
                      @Param("errorCode") String errorCode,
                      @Param("errorMessage") String errorMessage,
                      @Param("retryCount") Integer retryCount,
                      @Param("updatedAt") Instant updatedAt);

    @Select("""
        SELECT id, task_id, seq, step_type, status, input_summary, output_summary,
               started_at, ended_at, latency_ms, error_code, error_message, retry_count,
               created_at, updated_at
        FROM task_steps
        WHERE task_id = #{taskId}
        ORDER BY seq ASC, started_at ASC, id ASC
        """)
    List<TaskStepEntity> findByTaskIdOrdered(@Param("taskId") String taskId);

    @Select("""
        SELECT CASE WHEN COUNT(1) > 0 THEN TRUE ELSE FALSE END
        FROM task_steps WHERE task_id = #{taskId}
        """)
    boolean existsByTaskId(@Param("taskId") String taskId);
}
