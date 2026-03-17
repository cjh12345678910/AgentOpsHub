package com.agentopshub.repository;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RolePermissionRepository {

    @Delete("""
        DELETE FROM role_permissions WHERE role_id = #{roleId}
        """)
    int deleteByRoleId(@Param("roleId") Long roleId);

    @Insert("""
        INSERT INTO role_permissions(role_id, permission_id)
        VALUES(#{roleId}, #{permissionId})
        ON CONFLICT (role_id, permission_id) DO NOTHING
        """)
    int insert(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}
