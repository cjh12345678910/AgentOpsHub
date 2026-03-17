package com.agentopshub;

import com.agentopshub.domain.DocChunkEmbeddingEntity;
import com.agentopshub.domain.DocChunkEntity;
import com.agentopshub.domain.DocEntity;
import com.agentopshub.domain.DocStatus;
import com.agentopshub.domain.EmbeddingStatus;
import com.agentopshub.dto.EmbeddingBackfillResponse;
import com.agentopshub.dto.RagChunkDetailResponse;
import com.agentopshub.dto.RagSearchRequest;
import com.agentopshub.dto.RagSearchResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.DocChunkEmbeddingRepository;
import com.agentopshub.repository.DocChunkRepository;
import com.agentopshub.repository.DocRepository;
import com.agentopshub.service.DocsRagService;
import com.agentopshub.service.EmbeddingProvider;
import com.agentopshub.service.EmbeddingUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocsRagServiceTest {

    private DocRepository docRepository;
    private DocChunkRepository docChunkRepository;
    private DocChunkEmbeddingRepository docChunkEmbeddingRepository;
    private EmbeddingProvider embeddingProvider;
    private DocsRagService service;

    @BeforeEach
    void setUp() {
        docRepository = mock(DocRepository.class);
        docChunkRepository = mock(DocChunkRepository.class);
        docChunkEmbeddingRepository = mock(DocChunkEmbeddingRepository.class);
        embeddingProvider = mock(EmbeddingProvider.class);

        when(embeddingProvider.providerName()).thenReturn("local-hash");
        when(embeddingProvider.modelName()).thenReturn("local-hash-v1");
        when(embeddingProvider.dimension()).thenReturn(3);

        service = new DocsRagService(
            docRepository,
            docChunkRepository,
            docChunkEmbeddingRepository,
            embeddingProvider,
            new ObjectMapper(),
            1024 * 1024,
            400,
            500,
            80,
            5,
            20,
            3,
            100,
            0.15D,
            25,
            true,
            200,
            true
        );
    }

    @Test
    void searchReturnsEmbeddingModeAndRankedResults() {
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("alpha");
        request.setDocIds(List.of("doc-1"));
        request.setTopK(5);

        DocEntity doc = new DocEntity();
        doc.setId("doc-1");
        doc.setName("doc1.txt");
        doc.setStatus(DocStatus.PROCESSED);
        when(docRepository.findByIds(List.of("doc-1"))).thenReturn(List.of(doc));

        DocChunkEntity c1 = buildChunk("doc-1-c-1", "doc-1", 1, "alpha one");
        DocChunkEntity c2 = buildChunk("doc-1-c-2", "doc-1", 2, "alpha two");
        when(docChunkRepository.findByDocIds(List.of("doc-1"))).thenReturn(List.of(c1, c2));

        when(embeddingProvider.embedTexts(List.of("alpha"))).thenReturn(List.of(new double[]{1, 0, 0}));

        DocChunkEmbeddingEntity e1 = buildEmbedding("doc-1-c-1", "[0.9,0,0]");
        DocChunkEmbeddingEntity e2 = buildEmbedding("doc-1-c-2", "[0.2,0,0]");
        when(docChunkEmbeddingRepository.findByChunkIds(List.of("doc-1-c-1", "doc-1-c-2"))).thenReturn(List.of(e1, e2));

        RagSearchResponse response = service.search(request);

        assertEquals("embedding_similarity", response.getRetrievalMode());
        assertEquals(2, response.getItems().size());
        assertEquals("doc-1-c-1", response.getItems().get(0).getChunkId());
        assertEquals(1, response.getItems().get(0).getRank());
        assertEquals("cosine_similarity", response.getItems().get(0).getScoreType());
    }

    @Test
    void searchUsesFallbackWhenEmbeddingUnavailable() {
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("alpha beta");
        request.setDocIds(List.of("doc-1"));
        request.setTopK(5);

        DocEntity doc = new DocEntity();
        doc.setId("doc-1");
        doc.setName("doc1.txt");
        doc.setStatus(DocStatus.PROCESSED);
        when(docRepository.findByIds(List.of("doc-1"))).thenReturn(List.of(doc));

        DocChunkEntity c1 = buildChunk("doc-1-c-1", "doc-1", 1, "alpha beta");
        when(docChunkRepository.findByDocIds(List.of("doc-1"))).thenReturn(List.of(c1));

        when(embeddingProvider.embedTexts(List.of("alpha beta")))
            .thenThrow(new EmbeddingUnavailableException("provider down"));

        RagSearchResponse response = service.search(request);
        assertEquals("fallback_lexical", response.getRetrievalMode());
        assertEquals("embedding_unavailable:provider down", response.getFallbackReason());
        verify(docChunkEmbeddingRepository, never()).findByChunkIds(any());
    }

    @Test
    void searchRejectsInvalidTopK() {
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("test");
        request.setDocIds(List.of("doc-1"));
        request.setTopK(200);

        ApiException ex = assertThrows(ApiException.class, () -> service.search(request));
        assertEquals("RAG_BAD_REQUEST", ex.getCode());
    }

    @Test
    void backfillEmbeddingsIdempotentPath() {
        DocChunkEntity c1 = buildChunk("doc-1-c-1", "doc-1", 1, "hello");
        when(docChunkRepository.findBackfillCandidates(false, 200)).thenReturn(List.of(c1));
        when(embeddingProvider.embedTexts(List.of("hello"))).thenReturn(List.of(new double[]{0.1, 0.2, 0.3}));

        EmbeddingBackfillResponse response = service.backfillEmbeddings(false, null);
        assertEquals(1, response.getProcessed());
        assertEquals(1, response.getSuccessCount());
        verify(docChunkEmbeddingRepository).upsert(any(DocChunkEmbeddingEntity.class));
    }

    @Test
    void backfillThrowsWhenProviderUnavailable() {
        DocChunkEntity c1 = buildChunk("doc-1-c-1", "doc-1", 1, "hello");
        when(docChunkRepository.findBackfillCandidates(false, 200)).thenReturn(List.of(c1));
        when(embeddingProvider.embedTexts(List.of("hello"))).thenThrow(new EmbeddingUnavailableException("disabled"));

        ApiException ex = assertThrows(ApiException.class, () -> service.backfillEmbeddings(false, null));
        assertEquals("RAG_EMBEDDING_UNAVAILABLE", ex.getCode());
    }

    @Test
    void chunkDetailNotFoundThrows() {
        when(docChunkRepository.findByChunkId("missing")).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> service.getChunk("missing"));
        assertEquals("CHUNK_NOT_FOUND", ex.getCode());
    }

    @Test
    void chunkDetailSuccess() {
        DocChunkEntity chunk = buildChunk("doc-1-c-1", "doc-1", 1, "hello world");
        chunk.setMetadataJson("{}");
        chunk.setCreatedAt(Instant.now());

        DocEntity doc = new DocEntity();
        doc.setId("doc-1");
        doc.setName("doc1.txt");

        when(docChunkRepository.findByChunkId("doc-1-c-1")).thenReturn(chunk);
        when(docRepository.findById("doc-1")).thenReturn(doc);

        RagChunkDetailResponse response = service.getChunk("doc-1-c-1");
        assertEquals("doc1.txt", response.getDocName());
        assertEquals("hello world", response.getContent());
    }

    @Test
    void uploadDocUsesSectionAwareChunkingWithOverlap() {
        DocsRagService localService = new DocsRagService(
            docRepository,
            docChunkRepository,
            docChunkEmbeddingRepository,
            embeddingProvider,
            new ObjectMapper(),
            1024 * 1024,
            400,
            40,
            8,
            5,
            20,
            3,
            100,
            0.15D,
            25,
            true,
            200,
            true
        );

        List<DocChunkEntity> inserted = new ArrayList<>();
        doAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return 1;
        }).when(docChunkRepository).insert(any(DocChunkEntity.class));
        when(embeddingProvider.embedTexts(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(t -> new double[]{0.1D, 0.2D, 0.3D}).toList();
        });

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.md",
            "text/markdown",
            ("# Intro\nMarkdown basics are easy to learn.\n\n## Error Handling\nError handling includes try catch logging fallback defaults.")
                .getBytes()
        );

        localService.uploadDoc(file);

        assertTrue(inserted.size() >= 2);
        assertTrue(inserted.stream().allMatch(c -> c.getMetadataJson().contains("\"section\":")));
        verify(docChunkRepository, atLeast(2)).insert(any(DocChunkEntity.class));
    }

    @Test
    void searchOrderingIsDeterministicWhenScoresEqual() {
        RagSearchRequest request = new RagSearchRequest();
        request.setQuery("same score query");
        request.setDocIds(List.of("doc-1"));
        request.setTopK(2);

        DocEntity doc = new DocEntity();
        doc.setId("doc-1");
        doc.setName("doc1.txt");
        doc.setStatus(DocStatus.PROCESSED);
        when(docRepository.findByIds(List.of("doc-1"))).thenReturn(List.of(doc));

        DocChunkEntity c1 = buildChunk("doc-1-c-1", "doc-1", 1, "content a");
        DocChunkEntity c2 = buildChunk("doc-1-c-2", "doc-1", 2, "content b");
        when(docChunkRepository.findByDocIds(List.of("doc-1"))).thenReturn(List.of(c1, c2));

        when(embeddingProvider.embedTexts(List.of("same score query"))).thenReturn(List.of(new double[]{1, 0, 0}));
        DocChunkEmbeddingEntity e1 = buildEmbedding("doc-1-c-1", "[0.5,0,0]");
        DocChunkEmbeddingEntity e2 = buildEmbedding("doc-1-c-2", "[0.5,0,0]");
        when(docChunkEmbeddingRepository.findByChunkIds(List.of("doc-1-c-1", "doc-1-c-2"))).thenReturn(List.of(e1, e2));

        RagSearchResponse response = service.search(request);
        assertEquals("doc-1-c-1", response.getItems().get(0).getChunkId());
        assertEquals("doc-1-c-2", response.getItems().get(1).getChunkId());
    }

    @Test
    void uploadPdfSuccess() {
        when(embeddingProvider.embedTexts(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(t -> new double[]{0.1D, 0.2D, 0.3D}).toList();
        });

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "sample.pdf",
            "application/pdf",
            createPdfBytes("Hello PDF world")
        );

        var response = service.uploadDoc(file);
        assertEquals("INDEXED", response.getStatus());
    }

    @Test
    void uploadPdfUnsupportedHeader() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "bad.pdf",
            "application/pdf",
            "not a pdf".getBytes()
        );
        ApiException ex = assertThrows(ApiException.class, () -> service.uploadDoc(file));
        assertEquals("PDF_UNSUPPORTED", ex.getCode());
    }

    @Test
    void uploadPdfParseFailure() {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "empty.pdf",
            "application/pdf",
            ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF").getBytes(StandardCharsets.ISO_8859_1)
        );
        ApiException ex = assertThrows(ApiException.class, () -> service.uploadDoc(file));
        assertTrue(List.of("PDF_EMPTY_TEXT", "PDF_PARSE_FAILED").contains(ex.getCode()));
    }

    private byte[] createPdfBytes(String text) {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            doc.save(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create test pdf", ex);
        }
    }

    private DocChunkEntity buildChunk(String chunkId, String docId, int index, String content) {
        DocChunkEntity chunk = new DocChunkEntity();
        chunk.setChunkId(chunkId);
        chunk.setDocId(docId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        return chunk;
    }

    private DocChunkEmbeddingEntity buildEmbedding(String chunkId, String embeddingJson) {
        DocChunkEmbeddingEntity embedding = new DocChunkEmbeddingEntity();
        embedding.setChunkId(chunkId);
        embedding.setEmbeddingJson(embeddingJson);
        embedding.setStatus(EmbeddingStatus.READY);
        return embedding;
    }
}
