package com.agentopshub.repository;

import com.agentopshub.domain.TaskEntity;
import com.agentopshub.domain.TaskStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;

@Mapper
public interface TaskRepository {

    @Insert("""
        INSERT INTO tasks(id, status, dispatch_status, message_id, retry_count, enqueued_at, dlq_reason, last_error_code,
                          prompt, doc_ids_json, output_format, result_json, result_md, error_message, created_at, updated_at)
        VALUES(#{id}, #{status}, #{dispatchStatus}, #{messageId}, #{retryCount}, #{enqueuedAt}, #{dlqReason}, #{lastErrorCode},
               #{prompt}, #{docIdsJson}, #{outputFormat}, #{resultJson}, #{resultMd}, #{errorMessage}, #{createdAt}, #{updatedAt})
        """)
    int insert(TaskEntity task);

    @Select("""
        SELECT id, status, dispatch_status, message_id, retry_count, enqueued_at, dlq_reason, last_error_code,
               prompt, doc_ids_json, output_format, result_json, result_md, error_message, created_at, updated_at
        FROM tasks WHERE id = #{taskId}
        """)
    TaskEntity findById(@Param("taskId") String taskId);

    @Update("""
        UPDATE tasks
        SET status = #{status},
            dispatch_status = #{dispatchStatus},
            updated_at = #{updatedAt}
        WHERE id = #{taskId}
        """)
    int updateStatus(@Param("taskId") String taskId,
                     @Param("status") TaskStatus status,
                     @Param("dispatchStatus") String dispatchStatus,
                     @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE tasks
        SET status = #{status},
            dispatch_status = #{dispatchStatus},
            result_md = #{resultMd},
            result_json = #{resultJson},
            error_message = NULL,
            updated_at = #{updatedAt}
        WHERE id = #{taskId}
        """)
    int updateResultAndStatus(@Param("taskId") String taskId,
                              @Param("status") TaskStatus status,
                              @Param("dispatchStatus") String dispatchStatus,
                              @Param("resultMd") String resultMd,
                              @Param("resultJson") String resultJson,
                              @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE tasks
        SET status = #{status},
            dispatch_status = #{dispatchStatus},
            error_message = #{errorMessage},
            last_error_code = #{lastErrorCode},
            updated_at = #{updatedAt}
        WHERE id = #{taskId}
        """)
    int updateErrorAndStatus(@Param("taskId") String taskId,
                             @Param("status") TaskStatus status,
                             @Param("dispatchStatus") String dispatchStatus,
                             @Param("errorMessage") String errorMessage,
                             @Param("lastErrorCode") String lastErrorCode,
                             @Param("updatedAt") Instant updatedAt);

    @Update("""
        UPDATE tasks
        SET message_id = #{messageId},
            dispatch_status = #{dispatchStatus},
            retry_count = #{retryCount},
            enqueued_at = #{enqueuedAt},
            dlq_reason = #{dlqReason},
            last_error_code = #{lastErrorCode},
            updated_at = #{updatedAt}
        WHERE id = #{taskId}
        """)
    int updateDispatchMetadata(@Param("taskId") String taskId,
                               @Param("messageId") String messageId,
                               @Param("dispatchStatus") String dispatchStatus,
                               @Param("retryCount") Integer retryCount,
                               @Param("enqueuedAt") Instant enqueuedAt,
                               @Param("dlqReason") String dlqReason,
                               @Param("lastErrorCode") String lastErrorCode,
                               @Param("updatedAt") Instant updatedAt);

    @Select("""
        SELECT id, status, dispatch_status, message_id, retry_count, enqueued_at, dlq_reason, last_error_code,
               prompt, doc_ids_json, output_format, result_json, result_md, error_message, created_at, updated_at
        FROM tasks
        WHERE dispatch_status = 'DLQ'
        ORDER BY updated_at DESC
        LIMIT #{limit}
        """)
    java.util.List<TaskEntity> findDlqTasks(@Param("limit") int limit);
}
