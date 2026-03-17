package com.agentopshub.controller;

import com.agentopshub.dto.RagChunkDetailResponse;
import com.agentopshub.dto.EmbeddingBackfillResponse;
import com.agentopshub.dto.RagSearchRequest;
import com.agentopshub.dto.RagSearchResponse;
import com.agentopshub.service.DocsRagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {
    private final DocsRagService docsRagService;

    public RagController(DocsRagService docsRagService) {
        this.docsRagService = docsRagService;
    }

    @PostMapping("/search")
    public RagSearchResponse search(@Valid @RequestBody RagSearchRequest request) {
        return docsRagService.search(request);
    }

    @GetMapping("/chunk/{chunkId}")
    public RagChunkDetailResponse getChunk(@PathVariable String chunkId) {
        return docsRagService.getChunk(chunkId);
    }

    @PostMapping("/embeddings/backfill")
    public EmbeddingBackfillResponse backfillEmbeddings(@RequestParam(defaultValue = "false") boolean rebuild,
                                                        @RequestParam(required = false) Integer limit) {
        return docsRagService.backfillEmbeddings(rebuild, limit);
    }
}
