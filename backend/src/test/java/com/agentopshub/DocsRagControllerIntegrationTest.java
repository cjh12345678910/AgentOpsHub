package com.agentopshub;

import com.agentopshub.controller.DocsController;
import com.agentopshub.controller.RagController;
import com.agentopshub.dto.DocSummaryResponse;
import com.agentopshub.dto.DocUploadResponse;
import com.agentopshub.dto.EmbeddingBackfillResponse;
import com.agentopshub.dto.RechunkResponse;
import com.agentopshub.dto.RagChunkDetailResponse;
import com.agentopshub.dto.RagSearchItemResponse;
import com.agentopshub.dto.RagSearchResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.service.DocsRagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({DocsController.class, RagController.class})
class DocsRagControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocsRagService docsRagService;

    @Test
    void uploadDocSuccess() throws Exception {
        when(docsRagService.uploadDoc(any()))
            .thenReturn(new DocUploadResponse("doc-1", "PROCESSED", "a.txt", 100L, 2, Instant.now(), Instant.now()));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "a.txt",
            "text/plain",
            "hello world".getBytes()
        );

        mockMvc.perform(multipart("/api/docs/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docId").value("doc-1"))
            .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void uploadPdfSuccess() throws Exception {
        when(docsRagService.uploadDoc(any()))
            .thenReturn(new DocUploadResponse("doc-2", "INDEXED", "a.pdf", 120L, 3, Instant.now(), Instant.now()));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "a.pdf",
            "application/pdf",
            "%PDF-1.4".getBytes()
        );

        mockMvc.perform(multipart("/api/docs/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docId").value("doc-2"))
            .andExpect(jsonPath("$.status").value("INDEXED"));
    }

    @Test
    void uploadDocFormatError() throws Exception {
        when(docsRagService.uploadDoc(any()))
            .thenThrow(new ApiException("DOC_FORMAT_NOT_SUPPORTED", "Only .txt, .md and .pdf files are supported", Map.of(), HttpStatus.BAD_REQUEST.value()));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "a.exe",
            "application/octet-stream",
            "bad".getBytes()
        );

        mockMvc.perform(multipart("/api/docs/upload").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("DOC_FORMAT_NOT_SUPPORTED"));
    }

    @Test
    void listDocsSuccess() throws Exception {
        when(docsRagService.listDocs())
            .thenReturn(List.of(new DocSummaryResponse("doc-1", "a.txt", "text/plain", "PROCESSED", 12L, null, Instant.now(), Instant.now())));

        mockMvc.perform(get("/api/docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].docId").value("doc-1"));
    }

    @Test
    void ragSearchSuccess() throws Exception {
        RagSearchItemResponse item = new RagSearchItemResponse("doc-1-c-1", "doc-1", "a.txt", 1, 0.8D, "cosine_similarity", 1, "hello snippet");
        when(docsRagService.search(any()))
            .thenReturn(new RagSearchResponse("hello", 5, "embedding_similarity", "cosine_similarity", null, false, 10, true, "openai-compatible", List.of(0.8), List.of(item)));

        mockMvc.perform(post("/api/rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"hello\",\"docIds\":[\"doc-1\"],\"topK\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].chunkId").value("doc-1-c-1"))
            .andExpect(jsonPath("$.retrievalMode").value("embedding_similarity"))
            .andExpect(jsonPath("$.items[0].scoreType").value("cosine_similarity"));
    }

    @Test
    void ragSearchBadRequest() throws Exception {
        when(docsRagService.search(any()))
            .thenThrow(new ApiException("RAG_BAD_REQUEST", "topK is out of range", Map.of("field", "topK"), HttpStatus.BAD_REQUEST.value()));

        mockMvc.perform(post("/api/rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"hello\",\"docIds\":[\"doc-1\"],\"topK\":99}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("RAG_BAD_REQUEST"));
    }

    @Test
    void ragChunkNotFound() throws Exception {
        when(docsRagService.getChunk(eq("missing")))
            .thenThrow(new ApiException("CHUNK_NOT_FOUND", "Chunk not found", Map.of("chunkId", "missing"), HttpStatus.NOT_FOUND.value()));

        mockMvc.perform(get("/api/rag/chunk/{chunkId}", "missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHUNK_NOT_FOUND"));
    }

    @Test
    void ragChunkSuccess() throws Exception {
        when(docsRagService.getChunk(eq("doc-1-c-1")))
            .thenReturn(new RagChunkDetailResponse("doc-1-c-1", "doc-1", "a.txt", 1, "hello", "{}", Instant.now()));

        mockMvc.perform(get("/api/rag/chunk/{chunkId}", "doc-1-c-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.docId").value("doc-1"));
    }

    @Test
    void ragEmbeddingBackfillSuccess() throws Exception {
        when(docsRagService.backfillEmbeddings(eq(false), eq(100)))
            .thenReturn(new EmbeddingBackfillResponse(100, 25, 24, 1, "local-hash", "local-hash-v1"));

        mockMvc.perform(post("/api/rag/embeddings/backfill?rebuild=false&limit=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processed").value(25))
            .andExpect(jsonPath("$.provider").value("local-hash"));
    }

    @Test
    void rechunkDocsSuccess() throws Exception {
        when(docsRagService.rechunkDocs(eq(true), eq(20)))
            .thenReturn(new RechunkResponse(20, 5, 30, 0));

        mockMvc.perform(post("/api/docs/rechunk?rebuildEmbeddings=true&limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedDocs").value(5))
            .andExpect(jsonPath("$.rebuiltChunks").value(30));
    }
}
