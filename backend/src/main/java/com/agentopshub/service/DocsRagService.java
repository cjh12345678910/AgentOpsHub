package com.agentopshub.service;

import com.agentopshub.domain.DocChunkEmbeddingEntity;
import com.agentopshub.domain.DocChunkEntity;
import com.agentopshub.domain.DocEntity;
import com.agentopshub.domain.DocStatus;
import com.agentopshub.domain.EmbeddingStatus;
import com.agentopshub.dto.DocSummaryResponse;
import com.agentopshub.dto.DocUploadResponse;
import com.agentopshub.dto.EmbeddingBackfillResponse;
import com.agentopshub.dto.RechunkResponse;
import com.agentopshub.dto.RagChunkDetailResponse;
import com.agentopshub.dto.RagSearchItemResponse;
import com.agentopshub.dto.RagSearchRequest;
import com.agentopshub.dto.RagSearchResponse;
import com.agentopshub.exception.ApiException;
import com.agentopshub.repository.DocChunkEmbeddingRepository;
import com.agentopshub.repository.DocChunkRepository;
import com.agentopshub.repository.DocRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocsRagService {
    private static final Logger log = LoggerFactory.getLogger(DocsRagService.class);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9\\u4e00-\\u9fa5]+");
    private static final String RETRIEVAL_MODE_EMBEDDING = "embedding_similarity";
    private static final String RETRIEVAL_MODE_FALLBACK = "fallback_lexical";
    private static final String SCORE_TYPE_COSINE = "cosine_similarity";
    private static final String SCORE_TYPE_LEXICAL = "lexical_overlap";

    private final DocRepository docRepository;
    private final DocChunkRepository docChunkRepository;
    private final DocChunkEmbeddingRepository docChunkEmbeddingRepository;
    private final EmbeddingProvider embeddingProvider;
    private final ObjectMapper objectMapper;
    private final long maxUploadBytes;
    private final int maxChunksPerDoc;
    private final int chunkSize;
    private final int chunkOverlapChars;
    private final int topKDefault;
    private final int topKMax;
    private final int recallCandidateMultiplier;
    private final int recallCandidateCap;
    private final double rerankLexicalWeight;
    private final int rerankTimeoutMs;
    private final boolean allowLexicalFallback;
    private final int backfillBatchSize;
    private final boolean pdfUploadEnabled;

    public DocsRagService(DocRepository docRepository,
                          DocChunkRepository docChunkRepository,
                          DocChunkEmbeddingRepository docChunkEmbeddingRepository,
                          EmbeddingProvider embeddingProvider,
                          ObjectMapper objectMapper,
                          @Value("${app.docs.max-upload-bytes:2097152}") long maxUploadBytes,
                          @Value("${app.docs.max-chunks-per-doc:400}") int maxChunksPerDoc,
                          @Value("${app.docs.chunk-size:500}") int chunkSize,
                          @Value("${app.docs.chunk-overlap-chars:80}") int chunkOverlapChars,
                          @Value("${app.rag.topk-default:5}") int topKDefault,
                          @Value("${app.rag.topk-max:20}") int topKMax,
                          @Value("${app.rag.search.recall-candidate-multiplier:3}") int recallCandidateMultiplier,
                          @Value("${app.rag.search.recall-candidate-cap:100}") int recallCandidateCap,
                          @Value("${app.rag.search.rerank-lexical-weight:0.15}") double rerankLexicalWeight,
                          @Value("${app.rag.search.rerank-timeout-ms:25}") int rerankTimeoutMs,
                          @Value("${app.rag.search.allow-lexical-fallback:true}") boolean allowLexicalFallback,
                          @Value("${app.rag.embedding.backfill-batch-size:200}") int backfillBatchSize,
                          @Value("${app.docs.pdf-enabled:true}") boolean pdfUploadEnabled) {
        this.docRepository = docRepository;
        this.docChunkRepository = docChunkRepository;
        this.docChunkEmbeddingRepository = docChunkEmbeddingRepository;
        this.embeddingProvider = embeddingProvider;
        this.objectMapper = objectMapper;
        this.maxUploadBytes = maxUploadBytes;
        this.maxChunksPerDoc = maxChunksPerDoc;
        this.chunkSize = chunkSize;
        this.chunkOverlapChars = Math.max(0, chunkOverlapChars);
        this.topKDefault = topKDefault;
        this.topKMax = topKMax;
        this.recallCandidateMultiplier = Math.max(1, recallCandidateMultiplier);
        this.recallCandidateCap = Math.max(10, recallCandidateCap);
        this.rerankLexicalWeight = Math.min(1D, Math.max(0D, rerankLexicalWeight));
        this.rerankTimeoutMs = Math.max(1, rerankTimeoutMs);
        this.allowLexicalFallback = allowLexicalFallback;
        this.backfillBatchSize = backfillBatchSize;
        this.pdfUploadEnabled = pdfUploadEnabled;
    }

    @Transactional
    public DocUploadResponse uploadDoc(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("DOC_EMPTY_CONTENT", "Uploaded file is empty", Map.of(), HttpStatus.BAD_REQUEST.value());
        }

        String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        boolean isPdf = normalizedName.endsWith(".pdf");
        if (isPdf && !pdfUploadEnabled) {
            throw new ApiException(
                "DOC_FORMAT_NOT_SUPPORTED",
                "PDF upload is disabled by configuration",
                Map.of("name", fileName),
                HttpStatus.BAD_REQUEST.value()
            );
        }
        if (!(normalizedName.endsWith(".txt") || normalizedName.endsWith(".md") || isPdf)) {
            throw new ApiException(
                "DOC_FORMAT_NOT_SUPPORTED",
                "Only .txt, .md and .pdf files are supported",
                Map.of("name", fileName),
                HttpStatus.BAD_REQUEST.value()
            );
        }

        long fileSize = file.getSize();
        if (fileSize > maxUploadBytes) {
            throw new ApiException(
                "DOC_TOO_LARGE",
                "Uploaded file exceeds size limit",
                Map.of("maxUploadBytes", maxUploadBytes, "actual", fileSize),
                HttpStatus.BAD_REQUEST.value()
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception ex) {
            throw new ApiException("DOC_READ_ERROR", "Failed to read uploaded file", Map.of("name", fileName), HttpStatus.BAD_REQUEST.value());
        }

        String content;
        if (isPdf) {
            content = extractPdfText(bytes, fileName).trim();
        } else {
            content = new String(bytes, StandardCharsets.UTF_8).trim();
        }
        if (content.isBlank()) {
            String errorCode = isPdf ? "PDF_EMPTY_TEXT" : "DOC_EMPTY_CONTENT";
            throw new ApiException(errorCode, "Parsed file content is empty", Map.of("name", fileName), HttpStatus.BAD_REQUEST.value());
        }

        Instant now = Instant.now();
        String docId = "doc-" + UUID.randomUUID();
        DocEntity doc = new DocEntity();
        doc.setId(docId);
        doc.setName(fileName);
        doc.setContentType(file.getContentType() == null ? "text/plain" : file.getContentType());
        doc.setStatus(DocStatus.UPLOADED);
        doc.setFileSize(fileSize);
        doc.setContentHash(sha256Hex(bytes));
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        docRepository.insert(doc);

        if (isPdf) {
            docRepository.updateStatus(docId, DocStatus.PARSING, null, Instant.now());
        }
        List<ChunkPiece> chunks;
        try {
            chunks = splitIntoChunks(content);
        } catch (RuntimeException ex) {
            if (isPdf) {
                docRepository.updateStatus(docId, DocStatus.FAILED, "PDF_PARSE_FAILED:" + ex.getMessage(), Instant.now());
                throw new ApiException(
                    "PDF_PARSE_FAILED",
                    "Failed to parse PDF content",
                    Map.of("docId", docId, "name", fileName),
                    HttpStatus.BAD_REQUEST.value()
                );
            }
            throw ex;
        }
        if (chunks.isEmpty()) {
            String code = isPdf ? "PDF_EMPTY_TEXT" : "DOC_EMPTY_CONTENT";
            docRepository.updateStatus(docId, DocStatus.FAILED, "No chunks generated", Instant.now());
            throw new ApiException(code, "No chunks generated from content", Map.of("docId", docId), HttpStatus.BAD_REQUEST.value());
        }
        if (chunks.size() > maxChunksPerDoc) {
            docRepository.updateStatus(docId, DocStatus.FAILED, "Chunk count exceeded max limit", Instant.now());
            throw new ApiException(
                "DOC_TOO_MANY_CHUNKS",
                "Chunk count exceeds max limit",
                Map.of("docId", docId, "maxChunksPerDoc", maxChunksPerDoc, "actual", chunks.size()),
                HttpStatus.BAD_REQUEST.value()
            );
        }

        List<DocChunkEntity> chunkEntities = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkPiece piece = chunks.get(i);
            DocChunkEntity chunk = new DocChunkEntity();
            chunk.setChunkId(docId + "-c-" + (i + 1));
            chunk.setDocId(docId);
            chunk.setChunkIndex(i + 1);
            chunk.setContent(piece.content());
            chunk.setMetadataJson("{\"source\":\"" + escapeJson(fileName) + "\",\"length\":" + piece.content().length() + ",\"section\":\"" + escapeJson(piece.section()) + "\"}");
            chunk.setCreatedAt(now);
            chunk.setUpdatedAt(now);
            docChunkRepository.insert(chunk);
            chunkEntities.add(chunk);
        }

        persistEmbeddingsForChunks(chunkEntities);
        DocStatus finalStatus = isPdf ? DocStatus.INDEXED : DocStatus.PROCESSED;
        Instant updatedAt = Instant.now();
        docRepository.updateStatus(docId, finalStatus, null, updatedAt);
        return new DocUploadResponse(docId, finalStatus.name(), fileName, fileSize, chunks.size(), now, updatedAt);
    }

    @Transactional(readOnly = true)
    public List<DocSummaryResponse> listDocs() {
        return docRepository.findAllOrderByCreatedAtDesc().stream()
            .map(doc -> new DocSummaryResponse(
                doc.getId(),
                doc.getName(),
                doc.getContentType(),
                doc.getStatus().name(),
                doc.getFileSize(),
                doc.getErrorMessage(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public RagSearchResponse search(RagSearchRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery().trim();
        if (query.isBlank()) {
            throw new ApiException("RAG_BAD_REQUEST", "query is required", Map.of("field", "query"), HttpStatus.BAD_REQUEST.value());
        }
        List<String> docIds = normalizeDocIds(request.getDocIds());
        if (docIds == null || docIds.isEmpty()) {
            throw new ApiException("RAG_BAD_REQUEST", "docIds is required", Map.of("field", "docIds"), HttpStatus.BAD_REQUEST.value());
        }
        int topK = request.getTopK() == null ? topKDefault : request.getTopK();
        if (topK <= 0 || topK > topKMax) {
            throw new ApiException(
                "RAG_BAD_REQUEST",
                "topK is out of range",
                Map.of("field", "topK", "min", 1, "max", topKMax, "actual", topK),
                HttpStatus.BAD_REQUEST.value()
            );
        }

        List<DocEntity> docs = requireReadyDocs(docIds);
        List<DocChunkEntity> chunks = docChunkRepository.findByDocIds(docIds);
        if (chunks.isEmpty()) {
            return buildSearchResponse(query, topK, RETRIEVAL_MODE_EMBEDDING, SCORE_TYPE_COSINE, null, false, 0, false, embeddingProvider.providerName(), List.of(), List.of());
        }
        Map<String, String> docNameMap = buildDocNameMap(docs);

        try {
            return searchByEmbedding(query, topK, chunks, docNameMap);
        } catch (EmbeddingUnavailableException embeddingError) {
            if (!allowLexicalFallback) {
                throw new ApiException(
                    "RAG_EMBEDDING_UNAVAILABLE",
                    "Embedding retrieval is unavailable",
                    Map.of("reason", embeddingError.getMessage(), "errorCode", embeddingError.getCode()),
                    HttpStatus.SERVICE_UNAVAILABLE.value()
                );
            }
            log.warn("Embedding unavailable, fallback to lexical retrieval. reason={}", embeddingError.getMessage());
            return searchByLexical(query, topK, chunks, docNameMap, embeddingError.getCode() + ":" + embeddingError.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public RagChunkDetailResponse getChunk(String chunkId) {
        DocChunkEntity chunk = docChunkRepository.findByChunkId(chunkId);
        if (chunk == null) {
            throw new ApiException("CHUNK_NOT_FOUND", "Chunk not found", Map.of("chunkId", chunkId), HttpStatus.NOT_FOUND.value());
        }

        DocEntity doc = docRepository.findById(chunk.getDocId());
        return new RagChunkDetailResponse(
            chunk.getChunkId(),
            chunk.getDocId(),
            doc == null ? "unknown" : doc.getName(),
            chunk.getChunkIndex(),
            chunk.getContent(),
            chunk.getMetadataJson(),
            chunk.getCreatedAt()
        );
    }

    @Transactional
    public EmbeddingBackfillResponse backfillEmbeddings(boolean rebuild, Integer limit) {
        int batchLimit = (limit == null || limit <= 0) ? backfillBatchSize : Math.min(limit, backfillBatchSize);
        List<DocChunkEntity> candidates = docChunkRepository.findBackfillCandidates(rebuild, batchLimit);
        if (candidates.isEmpty()) {
            return new EmbeddingBackfillResponse(batchLimit, 0, 0, 0, embeddingProvider.providerName(), embeddingProvider.modelName());
        }

        Instant started = Instant.now();
        List<String> texts = candidates.stream().map(DocChunkEntity::getContent).toList();
        List<double[]> vectors;
        try {
            vectors = embeddingProvider.embedTexts(texts);
        } catch (EmbeddingUnavailableException ex) {
            throw new ApiException(
                "RAG_EMBEDDING_UNAVAILABLE",
                "Embedding provider is unavailable",
                Map.of("reason", ex.getMessage()),
                HttpStatus.SERVICE_UNAVAILABLE.value()
            );
        }

        int successCount = 0;
        int failureCount = 0;
        for (int i = 0; i < candidates.size(); i++) {
            DocChunkEntity chunk = candidates.get(i);
            try {
                double[] vector = vectors.get(i);
                upsertReadyEmbedding(chunk.getChunkId(), vector, Instant.now());
                successCount++;
            } catch (Exception ex) {
                upsertFailedEmbedding(chunk.getChunkId(), "backfill_error:" + ex.getMessage(), Instant.now());
                failureCount++;
            }
        }

        long latencyMs = Duration.between(started, Instant.now()).toMillis();
        log.info("Embedding backfill completed. rebuild={}, requested={}, processed={}, success={}, failure={}, latencyMs={}",
            rebuild, batchLimit, candidates.size(), successCount, failureCount, latencyMs);
        return new EmbeddingBackfillResponse(batchLimit, candidates.size(), successCount, failureCount, embeddingProvider.providerName(), embeddingProvider.modelName());
    }

    @Transactional
    public RechunkResponse rechunkDocs(boolean rebuildEmbeddings, Integer limit) {
        int safeLimit = (limit == null || limit <= 0) ? 50 : Math.min(limit, 200);
        List<DocEntity> docs = docRepository.findProcessedByUpdatedAtAsc(safeLimit);
        int processedDocs = 0;
        int rebuiltChunks = 0;
        int failedDocs = 0;

        for (DocEntity doc : docs) {
            try {
                List<DocChunkEntity> existing = docChunkRepository.findByDocIdOrdered(doc.getId());
                if (existing.isEmpty()) {
                    continue;
                }
                String rebuiltContent = existing.stream()
                    .map(DocChunkEntity::getContent)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
                List<ChunkPiece> rechunked = splitIntoChunks(rebuiltContent);
                if (rechunked.isEmpty()) {
                    failedDocs++;
                    docRepository.updateStatus(doc.getId(), DocStatus.FAILED, "rechunk generated empty result", Instant.now());
                    continue;
                }

                docChunkRepository.deleteByDocId(doc.getId());
                List<DocChunkEntity> entities = new ArrayList<>();
                Instant now = Instant.now();
                for (int i = 0; i < rechunked.size(); i++) {
                    ChunkPiece piece = rechunked.get(i);
                    DocChunkEntity entity = new DocChunkEntity();
                    entity.setChunkId(doc.getId() + "-c-" + (i + 1));
                    entity.setDocId(doc.getId());
                    entity.setChunkIndex(i + 1);
                    entity.setContent(piece.content());
                    entity.setMetadataJson("{\"source\":\"" + escapeJson(doc.getName()) + "\",\"length\":" + piece.content().length() + ",\"section\":\"" + escapeJson(piece.section()) + "\"}");
                    entity.setCreatedAt(now);
                    entity.setUpdatedAt(now);
                    docChunkRepository.insert(entity);
                    entities.add(entity);
                }

                if (rebuildEmbeddings) {
                    persistEmbeddingsForChunks(entities);
                }
                docRepository.updateStatus(doc.getId(), DocStatus.PROCESSED, null, Instant.now());
                processedDocs++;
                rebuiltChunks += entities.size();
            } catch (Exception ex) {
                failedDocs++;
                docRepository.updateStatus(doc.getId(), DocStatus.FAILED, "rechunk_error:" + ex.getMessage(), Instant.now());
            }
        }
        return new RechunkResponse(safeLimit, processedDocs, rebuiltChunks, failedDocs);
    }

    private List<DocEntity> requireReadyDocs(List<String> docIds) {
        List<DocEntity> docs = docRepository.findByIds(docIds);
        if (docs.size() != docIds.size()) {
            Set<String> existing = new HashSet<>();
            for (DocEntity doc : docs) {
                existing.add(doc.getId());
            }
            List<String> missing = docIds.stream().filter(id -> !existing.contains(id)).toList();
            throw new ApiException("DOC_NOT_FOUND", "Some docIds do not exist", Map.of("missingDocIds", missing), HttpStatus.NOT_FOUND.value());
        }

        for (DocEntity doc : docs) {
            if (doc.getStatus() != DocStatus.PROCESSED && doc.getStatus() != DocStatus.INDEXED) {
                throw new ApiException(
                    "DOC_NOT_READY",
                    "Document is not ready for retrieval",
                    Map.of("docId", doc.getId(), "status", doc.getStatus().name()),
                    HttpStatus.CONFLICT.value()
                );
            }
        }
        return docs;
    }

    private Map<String, String> buildDocNameMap(List<DocEntity> docs) {
        Map<String, String> docNameMap = new HashMap<>();
        for (DocEntity doc : docs) {
            docNameMap.put(doc.getId(), doc.getName());
        }
        return docNameMap;
    }

    private String extractPdfText(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length < 5) {
            throw new ApiException(
                "PDF_UNSUPPORTED",
                "Invalid PDF header",
                Map.of("name", fileName),
                HttpStatus.BAD_REQUEST.value()
            );
        }
        String header = new String(bytes, 0, 5, StandardCharsets.ISO_8859_1);
        if (!header.startsWith("%PDF-")) {
            throw new ApiException(
                "PDF_UNSUPPORTED",
                "Invalid PDF header",
                Map.of("name", fileName),
                HttpStatus.BAD_REQUEST.value()
            );
        }
        try {
            String parsed;
            try (PDDocument document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                parsed = stripper.getText(document);
            }
            parsed = parsed == null ? "" : parsed.replace("\u0000", "").trim();
            if (parsed.isBlank()) {
                throw new ApiException(
                    "PDF_EMPTY_TEXT",
                    "Parsed PDF content is empty",
                    Map.of("name", fileName),
                    HttpStatus.BAD_REQUEST.value()
                );
            }
            return parsed;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(
                "PDF_PARSE_FAILED",
                "PDF parser could not extract text",
                Map.of("name", fileName),
                HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    private RagSearchResponse searchByEmbedding(String query,
                                                int topK,
                                                List<DocChunkEntity> chunks,
                                                Map<String, String> docNameMap) {
        Instant started = Instant.now();
        double[] queryVector = embeddingProvider.embedTexts(List.of(query)).get(0);
        Set<String> queryTerms = tokenize(query);

        List<String> chunkIds = chunks.stream().map(DocChunkEntity::getChunkId).toList();
        Map<String, double[]> embeddingMap = new HashMap<>();
        for (DocChunkEmbeddingEntity entity : docChunkEmbeddingRepository.findByChunkIds(chunkIds)) {
            if (entity.getStatus() != EmbeddingStatus.READY) {
                continue;
            }
            embeddingMap.put(entity.getChunkId(), parseEmbeddingJson(entity.getEmbeddingJson()));
        }
        boolean partialCoverage = embeddingMap.size() < chunks.size();

        List<ScoredChunk> scored = new ArrayList<>();
        for (DocChunkEntity chunk : chunks) {
            double[] chunkVector = embeddingMap.get(chunk.getChunkId());
            if (chunkVector == null) {
                continue;
            }
            double semanticScore = cosineSimilarity(queryVector, chunkVector);
            double lexicalScore = computeLexicalScore(queryTerms, chunk.getContent());
            scored.add(new ScoredChunk(chunk, semanticScore, lexicalScore, semanticScore));
        }

        int candidateCount = Math.min(recallCandidateCap, Math.max(topK, topK * recallCandidateMultiplier));
        List<ScoredChunk> recalled = scored.stream()
            .sorted(Comparator
                .comparing(ScoredChunk::semanticScore).reversed()
                .thenComparing(sc -> sc.chunk().getChunkIndex())
                .thenComparing(sc -> sc.chunk().getChunkId()))
            .limit(candidateCount)
            .toList();

        long rerankDeadlineNs = System.nanoTime() + (long) rerankTimeoutMs * 1_000_000L;
        List<ScoredChunk> rerankCandidates = new ArrayList<>();
        boolean rerankTimedOut = false;
        for (ScoredChunk candidate : recalled) {
            if (System.nanoTime() > rerankDeadlineNs) {
                rerankTimedOut = true;
                break;
            }
            rerankCandidates.add(candidate.withFinalScore(
                (1D - rerankLexicalWeight) * candidate.semanticScore() + rerankLexicalWeight * candidate.lexicalScore()
            ));
        }
        List<ScoredChunk> reranked;
        if (rerankTimedOut) {
            reranked = recalled.stream().limit(topK).toList();
        } else {
            reranked = rerankCandidates.stream()
                .sorted(Comparator
                    .comparing(ScoredChunk::finalScore).reversed()
                    .thenComparing(sc -> sc.chunk().getChunkIndex())
                    .thenComparing(sc -> sc.chunk().getChunkId()))
                .limit(topK)
                .toList();
        }

        List<RagSearchItemResponse> items = toItems(reranked, docNameMap, SCORE_TYPE_COSINE, queryTerms);
        List<Double> distribution = recalled.stream().map(ScoredChunk::semanticScore).limit(10).toList();
        long latencyMs = Duration.between(started, Instant.now()).toMillis();
        log.info("RAG embedding search complete. items={}, chunks={}, partialCoverage={}, latencyMs={}",
            items.size(), chunks.size(), partialCoverage, latencyMs);
        return buildSearchResponse(
            query,
            topK,
            RETRIEVAL_MODE_EMBEDDING,
            SCORE_TYPE_COSINE,
            null,
            partialCoverage,
            recalled.size(),
            !rerankTimedOut,
            embeddingProvider.providerName(),
            distribution,
            items
        );
    }

    private RagSearchResponse searchByLexical(String query,
                                              int topK,
                                              List<DocChunkEntity> chunks,
                                              Map<String, String> docNameMap,
                                              String fallbackReason) {
        Set<String> queryTerms = tokenize(query);
        List<ScoredChunk> scored = new ArrayList<>();
        for (DocChunkEntity chunk : chunks) {
            double score = computeLexicalScore(queryTerms, chunk.getContent());
            if (score <= 0D) {
                continue;
            }
            scored.add(new ScoredChunk(chunk, score, score, score));
        }
        List<ScoredChunk> ranked = scored.stream()
            .sorted(Comparator
                .comparing(ScoredChunk::finalScore).reversed()
                .thenComparing(sc -> sc.chunk().getChunkIndex())
                .thenComparing(sc -> sc.chunk().getChunkId()))
            .limit(topK)
            .toList();
        List<RagSearchItemResponse> items = toItems(ranked, docNameMap, SCORE_TYPE_LEXICAL, queryTerms);
        List<Double> distribution = ranked.stream().map(ScoredChunk::finalScore).limit(10).toList();
        return buildSearchResponse(
            query,
            topK,
            RETRIEVAL_MODE_FALLBACK,
            SCORE_TYPE_LEXICAL,
            fallbackReason,
            false,
            ranked.size(),
            false,
            embeddingProvider.providerName(),
            distribution,
            items
        );
    }

    private List<RagSearchItemResponse> toItems(List<ScoredChunk> ranked,
                                                Map<String, String> docNameMap,
                                                String scoreType,
                                                Set<String> queryTerms) {
        List<RagSearchItemResponse> items = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            ScoredChunk scoredChunk = ranked.get(i);
            DocChunkEntity chunk = scoredChunk.chunk();
            items.add(new RagSearchItemResponse(
                chunk.getChunkId(),
                chunk.getDocId(),
                docNameMap.getOrDefault(chunk.getDocId(), "unknown"),
                chunk.getChunkIndex(),
                scoredChunk.finalScore(),
                scoreType,
                i + 1,
                buildSnippet(chunk.getContent(), queryTerms)
            ));
        }
        return items;
    }

    private RagSearchResponse buildSearchResponse(String query,
                                                  int topK,
                                                  String retrievalMode,
                                                  String scoreType,
                                                  String fallbackReason,
                                                  boolean partialCoverage,
                                                  int candidateCount,
                                                  boolean rerankApplied,
                                                  String embeddingProviderName,
                                                  List<Double> scoreDistribution,
                                                  List<RagSearchItemResponse> items) {
        return new RagSearchResponse(
            query,
            topK,
            retrievalMode,
            scoreType,
            fallbackReason,
            partialCoverage,
            candidateCount,
            rerankApplied,
            embeddingProviderName,
            scoreDistribution,
            items
        );
    }

    private void persistEmbeddingsForChunks(List<DocChunkEntity> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        Instant started = Instant.now();
        try {
            List<String> texts = chunks.stream().map(DocChunkEntity::getContent).toList();
            List<double[]> vectors = embeddingProvider.embedTexts(texts);
            for (int i = 0; i < chunks.size(); i++) {
                upsertReadyEmbedding(chunks.get(i).getChunkId(), vectors.get(i), Instant.now());
            }
            log.info("Embedding ingest completed. chunkCount={}, provider={}, model={}, latencyMs={}",
                chunks.size(), embeddingProvider.providerName(), embeddingProvider.modelName(), Duration.between(started, Instant.now()).toMillis());
        } catch (EmbeddingUnavailableException ex) {
            for (DocChunkEntity chunk : chunks) {
                upsertFailedEmbedding(chunk.getChunkId(), ex.getMessage(), Instant.now());
            }
            log.warn("Embedding ingest failed due to unavailable provider. chunkCount={}, reason={}", chunks.size(), ex.getMessage());
        } catch (Exception ex) {
            for (DocChunkEntity chunk : chunks) {
                upsertFailedEmbedding(chunk.getChunkId(), ex.getMessage(), Instant.now());
            }
            log.warn("Embedding ingest failed. chunkCount={}, reason={}", chunks.size(), ex.getMessage());
        }
    }

    private void upsertReadyEmbedding(String chunkId, double[] vector, Instant now) {
        DocChunkEmbeddingEntity entity = new DocChunkEmbeddingEntity();
        entity.setChunkId(chunkId);
        entity.setProvider(embeddingProvider.providerName());
        entity.setModel(embeddingProvider.modelName());
        entity.setDimension(embeddingProvider.dimension());
        entity.setEmbeddingJson(toEmbeddingJson(vector));
        entity.setStatus(EmbeddingStatus.READY);
        entity.setErrorMessage(null);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        docChunkEmbeddingRepository.upsert(entity);
    }

    private void upsertFailedEmbedding(String chunkId, String errorMessage, Instant now) {
        DocChunkEmbeddingEntity entity = new DocChunkEmbeddingEntity();
        entity.setChunkId(chunkId);
        entity.setProvider(embeddingProvider.providerName());
        entity.setModel(embeddingProvider.modelName());
        entity.setDimension(embeddingProvider.dimension());
        entity.setEmbeddingJson("[]");
        entity.setStatus(EmbeddingStatus.FAILED);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        docChunkEmbeddingRepository.upsert(entity);
    }

    private List<ChunkPiece> splitIntoChunks(String content) {
        List<ChunkPiece> chunks = new ArrayList<>();
        List<SectionBlock> blocks = extractSectionBlocks(content);
        if (blocks.isEmpty()) {
            return chunks;
        }

        for (SectionBlock block : blocks) {
            String paragraph = block.content().trim();
            if (paragraph.isBlank()) {
                continue;
            }
            if (paragraph.length() <= chunkSize) {
                appendWithOverlap(chunks, paragraph, block.section());
                continue;
            }
            for (int start = 0; start < paragraph.length(); ) {
                int end = Math.min(start + chunkSize, paragraph.length());
                String piece = paragraph.substring(start, end);
                appendWithOverlap(chunks, piece, block.section());
                if (end >= paragraph.length()) {
                    break;
                }
                start = Math.max(start + 1, end - chunkOverlapChars);
            }
        }
        return chunks;
    }

    private void appendWithOverlap(List<ChunkPiece> chunks, String piece, String section) {
        if (piece == null || piece.isBlank()) {
            return;
        }
        if (chunks.isEmpty()) {
            chunks.add(new ChunkPiece(piece, section));
            return;
        }
        ChunkPiece previous = chunks.get(chunks.size() - 1);
        int overlap = Math.min(chunkOverlapChars, previous.content().length());
        if (overlap <= 0) {
            chunks.add(new ChunkPiece(piece, section));
            return;
        }
        String prefix = previous.content().substring(previous.content().length() - overlap);
        String merged = (prefix + "\n" + piece).trim();
        if (merged.length() > chunkSize) {
            merged = merged.substring(merged.length() - chunkSize);
        }
        chunks.add(new ChunkPiece(merged, section));
    }

    private List<SectionBlock> extractSectionBlocks(String content) {
        List<SectionBlock> blocks = new ArrayList<>();
        String currentSection = "root";
        String[] lines = content.split("\\R", -1);
        StringBuilder paragraph = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                if (paragraph.length() > 0) {
                    blocks.add(new SectionBlock(currentSection, paragraph.toString().trim()));
                    paragraph = new StringBuilder();
                }
                currentSection = trimmed.replaceAll("^#+\\s*", "");
                continue;
            }
            if (trimmed.isEmpty()) {
                if (paragraph.length() > 0) {
                    blocks.add(new SectionBlock(currentSection, paragraph.toString().trim()));
                    paragraph = new StringBuilder();
                }
                continue;
            }
            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }
        if (paragraph.length() > 0) {
            blocks.add(new SectionBlock(currentSection, paragraph.toString().trim()));
        }
        return blocks;
    }

    private List<String> normalizeDocIds(List<String> docIds) {
        if (docIds == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String docId : docIds) {
            if (docId == null) {
                continue;
            }
            String trimmed = docId.trim();
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
        }
        return new ArrayList<>(normalized);
    }

    private Set<String> tokenize(String text) {
        String[] parts = TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT));
        Set<String> terms = new HashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            terms.add(part);
        }
        return terms;
    }

    private double computeLexicalScore(Set<String> queryTerms, String content) {
        if (queryTerms.isEmpty() || content == null || content.isBlank()) {
            return 0D;
        }
        Set<String> chunkTerms = tokenize(content);
        if (chunkTerms.isEmpty()) {
            return 0D;
        }
        int hitCount = 0;
        for (String term : queryTerms) {
            if (chunkTerms.contains(term)) {
                hitCount++;
            }
        }
        return hitCount == 0 ? 0D : (double) hitCount / (double) queryTerms.size();
    }

    private String buildSnippet(String content, Set<String> queryTerms) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String lower = content.toLowerCase(Locale.ROOT);
        int firstPos = -1;
        for (String term : queryTerms) {
            int idx = lower.indexOf(term.toLowerCase(Locale.ROOT));
            if (idx >= 0 && (firstPos == -1 || idx < firstPos)) {
                firstPos = idx;
            }
        }
        if (firstPos < 0) {
            return content.substring(0, Math.min(140, content.length()));
        }

        // 让 snippet 以命中点为中心，便于前端快速定位相关上下文。
        int start = Math.max(0, firstPos - 40);
        int end = Math.min(content.length(), firstPos + 100);
        return content.substring(start, end);
    }

    private double[] parseEmbeddingJson(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, double[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid embedding json", e);
        }
    }

    private String toEmbeddingJson(double[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize embedding vector", e);
        }
    }

    private double cosineSimilarity(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0D;
        }
        int n = Math.min(left.length, right.length);
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < n; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm <= 0D || rightNorm <= 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(payload);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String escapeJson(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record SectionBlock(String section, String content) {}
    private record ChunkPiece(String content, String section) {}
    private record ScoredChunk(DocChunkEntity chunk, Double semanticScore, Double lexicalScore, Double finalScore) {
        private ScoredChunk withFinalScore(Double value) {
            return new ScoredChunk(chunk, semanticScore, lexicalScore, value);
        }
    }
}
