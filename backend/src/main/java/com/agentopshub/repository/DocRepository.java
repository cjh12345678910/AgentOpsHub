package com.agentopshub.repository;

import com.agentopshub.domain.DocEntity;
import com.agentopshub.domain.DocStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.Instant;
import java.util.List;

@Mapper
public interface DocRepository {

    @Insert("""
        INSERT INTO docs(id, name, content_type, status, file_size, content_hash, error_message, created_at, updated_at)
        VALUES(#{id}, #{name}, #{contentType}, #{status}, #{fileSize}, #{contentHash}, #{errorMessage}, #{createdAt}, #{updatedAt})
        """)
    int insert(DocEntity doc);

    @Update("""
        UPDATE docs
        SET status = #{status}, error_message = #{errorMessage}, updated_at = #{updatedAt}
        WHERE id = #{docId}
        """)
    int updateStatus(@Param("docId") String docId,
                     @Param("status") DocStatus status,
                     @Param("errorMessage") String errorMessage,
                     @Param("updatedAt") Instant updatedAt);

    @Select("""
        SELECT id, name, content_type, status, file_size, content_hash, error_message, created_at, updated_at
        FROM docs
        WHERE id = #{docId}
        """)
    DocEntity findById(@Param("docId") String docId);

    @Select("""
        <script>
        SELECT id, name, content_type, status, file_size, content_hash, error_message, created_at, updated_at
        FROM docs
        WHERE id IN
        <foreach item='id' collection='docIds' open='(' separator=',' close=')'>
            #{id}
        </foreach>
        </script>
        """)
    List<DocEntity> findByIds(@Param("docIds") List<String> docIds);

    @Select("""
        SELECT id, name, content_type, status, file_size, content_hash, error_message, created_at, updated_at
        FROM docs
        ORDER BY created_at DESC
        """)
    List<DocEntity> findAllOrderByCreatedAtDesc();

    @Select("""
        SELECT id, name, content_type, status, file_size, content_hash, error_message, created_at, updated_at
        FROM docs
        WHERE status IN ('PROCESSED', 'INDEXED')
        ORDER BY updated_at ASC
        LIMIT #{limit}
        """)
    List<DocEntity> findProcessedByUpdatedAtAsc(@Param("limit") int limit);
}
