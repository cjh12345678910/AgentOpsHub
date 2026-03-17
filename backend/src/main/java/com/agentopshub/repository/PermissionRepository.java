package com.agentopshub.repository;

import com.agentopshub.domain.PermissionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionRepository {

    @Select("""
        SELECT DISTINCT p.scope
        FROM permissions p
        JOIN role_permissions rp ON p.id = rp.permission_id
        JOIN user_roles ur ON rp.role_id = ur.role_id
        JOIN users u ON ur.user_id = u.id
        WHERE u.username = #{username}
        """)
    List<String> findScopesByUsername(@Param("username") String username);

    @Select("""
        SELECT p.scope
        FROM permissions p
        JOIN role_permissions rp ON p.id = rp.permission_id
        WHERE rp.role_id = #{roleId}
        """)
    List<String> findScopesByRoleId(@Param("roleId") Long roleId);

    @Select("""
        SELECT id, resource, action, scope, description, created_at, updated_at
        FROM permissions
        """)
    List<PermissionEntity> findAll();

    @Select("""
        SELECT id, resource, action, scope, description, created_at, updated_at
        FROM permissions WHERE scope = #{scope}
        """)
    PermissionEntity findByScope(@Param("scope") String scope);

    @Insert("""
        INSERT INTO permissions(resource, action, scope, description, created_at, updated_at)
        VALUES(#{resource}, #{action}, #{scope}, #{description}, #{createdAt}, #{updatedAt})
        ON CONFLICT (scope) DO NOTHING
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PermissionEntity permission);
}
