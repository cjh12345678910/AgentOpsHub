package com.agentopshub.repository;

import com.agentopshub.domain.RoleEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleRepository {

    @Select("""
        SELECT id, name, description, created_at, updated_at
        FROM roles
        """)
    List<RoleEntity> findAll();

    @Select("""
        SELECT id, name, description, created_at, updated_at
        FROM roles WHERE name = #{name}
        """)
    RoleEntity findByName(@Param("name") String name);

    @Insert("""
        INSERT INTO roles(name, description, created_at, updated_at)
        VALUES(#{name}, #{description}, #{createdAt}, #{updatedAt})
        ON CONFLICT (name) DO NOTHING
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RoleEntity role);
}
