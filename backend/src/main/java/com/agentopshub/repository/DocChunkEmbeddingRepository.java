package com.agentopshub.repository;

import com.agentopshub.domain.DocChunkEmbeddingEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocChunkEmbeddingRepository {

    @Insert("""
        INSERT INTO doc_chunk_embeddings(chunk_id, provider, model, dimension, embedding_json, status, error_message, created_at, updated_at)
        VALUES(#{chunkId}, #{provider}, #{model}, #{dimension}, #{embeddingJson}, #{status}, #{errorMessage}, #{createdAt}, #{updatedAt})
        ON CONFLICT (chunk_id) DO UPDATE SET
          provider = EXCLUDED.provider,
          model = EXCLUDED.model,
          dimension = EXCLUDED.dimension,
          embedding_json = EXCLUDED.embedding_json,
          status = EXCLUDED.status,
          error_message = EXCLUDED.error_message,
          updated_at = EXCLUDED.updated_at
        """)
    int upsert(DocChunkEmbeddingEntity embedding);

    @Select("""
        <script>
        SELECT chunk_id, provider, model, dimension, embedding_json, status, error_message, created_at, updated_at
        FROM doc_chunk_embeddings
        WHERE chunk_id IN
        <foreach item='chunkId' collection='chunkIds' open='(' separator=',' close=')'>
            #{chunkId}
        </foreach>
        </script>
        """)
    List<DocChunkEmbeddingEntity> findByChunkIds(@Param("chunkIds") List<String> chunkIds);
}
