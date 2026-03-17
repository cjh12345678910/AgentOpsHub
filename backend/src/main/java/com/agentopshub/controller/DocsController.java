package com.agentopshub.controller;

import com.agentopshub.dto.DocSummaryResponse;
import com.agentopshub.dto.DocUploadResponse;
import com.agentopshub.dto.RechunkResponse;
import com.agentopshub.service.DocsRagService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/docs")
public class DocsController {
    private final DocsRagService docsRagService;

    public DocsController(DocsRagService docsRagService) {
        this.docsRagService = docsRagService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return docsRagService.uploadDoc(file);
    }

    @GetMapping
    public List<DocSummaryResponse> listDocs() {
        return docsRagService.listDocs();
    }

    @PostMapping("/rechunk")
    public RechunkResponse rechunk(@RequestParam(defaultValue = "false") boolean rebuildEmbeddings,
                                   @RequestParam(required = false) Integer limit) {
        return docsRagService.rechunkDocs(rebuildEmbeddings, limit);
    }
}
