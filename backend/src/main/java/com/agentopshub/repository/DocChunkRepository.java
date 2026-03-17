package com.agentopshub.repository;

import com.agentopshub.domain.DocChunkEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocChunkRepository {

    @Insert("""
        INSERT INTO doc_chunks(chunk_id, doc_id, chunk_index, content, metadata_json, created_at, updated_at)
        VALUES(#{chunkId}, #{docId}, #{chunkIndex}, #{content}, #{metadataJson}, #{createdAt}, #{updatedAt})
        """)
    int insert(DocChunkEntity chunk);

    @Select("""
        <script>
        SELECT chunk_id, doc_id, chunk_index, content, metadata_json, created_at, updated_at
        FROM doc_chunks
        WHERE doc_id IN
        <foreach item='id' collection='docIds' open='(' separator=',' close=')'>
            #{id}
        </foreach>
        ORDER BY doc_id ASC, chunk_index ASC
        </script>
        """)
    List<DocChunkEntity> findByDocIds(@Param("docIds") List<String> docIds);

    @Select("""
        SELECT chunk_id, doc_id, chunk_index, content, metadata_json, created_at, updated_at
        FROM doc_chunks
        WHERE doc_id = #{docId}
        ORDER BY chunk_index ASC
        """)
    List<DocChunkEntity> findByDocIdOrdered(@Param("docId") String docId);

    @Select("""
        SELECT chunk_id, doc_id, chunk_index, content, metadata_json, created_at, updated_at
        FROM doc_chunks
        WHERE chunk_id = #{chunkId}
        """)
    DocChunkEntity findByChunkId(@Param("chunkId") String chunkId);

    @Select("""
        <script>
        SELECT c.chunk_id, c.doc_id, c.chunk_index, c.content, c.metadata_json, c.created_at, c.updated_at
        FROM doc_chunks c
        LEFT JOIN doc_chunk_embeddings e ON e.chunk_id = c.chunk_id
        <choose>
            <when test='rebuild'>
                ORDER BY c.created_at ASC
            </when>
            <otherwise>
                WHERE e.chunk_id IS NULL OR e.status = 'FAILED'
                ORDER BY c.created_at ASC
            </otherwise>
        </choose>
        LIMIT #{limit}
        </script>
        """)
    List<DocChunkEntity> findBackfillCandidates(@Param("rebuild") boolean rebuild,
                                                @Param("limit") int limit);

    @org.apache.ibatis.annotations.Delete("""
        DELETE FROM doc_chunks
        WHERE doc_id = #{docId}
        """)
    int deleteByDocId(@Param("docId") String docId);
}
