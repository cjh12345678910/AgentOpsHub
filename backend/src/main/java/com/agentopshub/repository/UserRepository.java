package com.agentopshub.repository;

import com.agentopshub.domain.UserEntity;
import com.agentopshub.domain.RoleEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserRepository {

    @Select("""
        SELECT id, username, password_hash, email, status, created_at, updated_at
        FROM users WHERE username = #{username}
        """)
    UserEntity findByUsername(@Param("username") String username);

    @Select("""
        SELECT id, username, password_hash, email, status, created_at, updated_at
        FROM users WHERE id = #{id}
        """)
    UserEntity findById(@Param("id") Long id);

    @Select("""
        SELECT r.id, r.name, r.description, r.created_at, r.updated_at
        FROM roles r
        JOIN user_roles ur ON r.id = ur.role_id
        WHERE ur.user_id = #{userId}
        """)
    List<RoleEntity> findRolesByUserId(@Param("userId") Long userId);

    @Insert("""
        INSERT INTO users(username, password_hash, email, status, created_at, updated_at)
        VALUES(#{username}, #{passwordHash}, #{email}, #{status}, #{createdAt}, #{updatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntity user);
}
