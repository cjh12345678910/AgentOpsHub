package com.agentopshub.repository;

import com.agentopshub.domain.AuthAuditLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface AuthAuditLogRepository {

    @Insert("""
        INSERT INTO auth_audit_logs(user_id, username, event_type, resource, action, decision, reason, ip_address, user_agent, created_at)
        VALUES(#{userId}, #{username}, #{eventType}, #{resource}, #{action}, #{decision}, #{reason}, #{ipAddress}, #{userAgent}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AuthAuditLogEntity log);
}
