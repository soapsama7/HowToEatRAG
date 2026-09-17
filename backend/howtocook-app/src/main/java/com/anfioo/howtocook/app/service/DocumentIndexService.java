package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.common.document.ChunkDraft;
import com.anfioo.howtocook.common.document.ChunkSplitter;
import com.anfioo.howtocook.common.document.ParsedDocument;
import com.anfioo.howtocook.common.document.RecipeMarkdownParser;
import com.anfioo.howtocook.common.embedding.EmbeddingClient;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.entity.doc.DocumentChunk;
import com.anfioo.howtocook.common.enums.doc.DocStatus;
import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.anfioo.howtocook.common.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档索引服务（Step 2.5 闭环）：读 RustFS 原文 → 解析 → 分块 → 批量向量化 → chunk 入库（version 同步）
 * → document 置 READY；异常时置 FAILED 并向上抛出（消费方决定重试/终结）。
 * <p>状态更新绕过实体乐观锁（LambdaUpdateWrapper 直更），保证 document.version 只随重索引推进，
 * 与 chunk.version 的比对语义一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIndexService {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final StorageService storageService;
    private final RecipeMarkdownParser recipeMarkdownParser;
    private final ChunkSplitter chunkSplitter;
    private final EmbeddingClient embeddingClient;

    /** 执行索引闭环 */
    public void index(String taskNo, long docId) {
        Document document = documentMapper.selectById(docId);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在: " + docId);
        }
        try {
            doIndex(document);
            markStatus(document.getId(), DocStatus.READY.name(), null, countChunks(docId));
            log.info("索引完成: taskNo={}, docId={}, chunks={}", taskNo, docId, countChunks(docId));
        } catch (Exception e) {
            markStatus(document.getId(), DocStatus.FAILED.name(), truncate(e.getMessage()), 0);
            throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
        }
    }

    private void doIndex(Document document) {
        // 1) 读原文 → 解析 → 分块
        byte[] bytes = storageService.getObject(document.getObjectKey());
        ParsedDocument parsed = recipeMarkdownParser.parse(new String(bytes, StandardCharsets.UTF_8));
        List<ChunkDraft> drafts = chunkSplitter.split(parsed);
        if (drafts.isEmpty()) {
            throw new IllegalStateException("解析后未产生任何分块，请检查文档内容");
        }

        // 2) 批量向量化
        List<float[]> vectors = embeddingClient.embedAll(
                drafts.stream().map(ChunkDraft::getContent).toList());

        // 3) 元数据回填 + chunk 入库（version 与 document 当前版本一致）
        for (int i = 0; i < drafts.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocId(document.getId());
            chunk.setVersion(document.getVersion());
            chunk.setChunkIndex(drafts.get(i).getChunkIndex());
            chunk.setSection(drafts.get(i).getSection());
            chunk.setContent(drafts.get(i).getContent());
            chunk.setTokenCount(drafts.get(i).getTokenCount());
            chunk.setEmbedding(new PGvector(vectors.get(i)));
            documentChunkMapper.insert(chunk);
        }

        // 4) 清理历史版本 chunk（重索引场景：新版本写入成功后删旧版本）
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocId, document.getId())
                .lt(DocumentChunk::getVersion, document.getVersion()));
        // old version rows are gone — but keep wrapper for future-proofing
        // 元数据回填与 READY 状态一并直更
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, document.getId())
                .set(Document::getTitle, parsed.getTitle() != null ? parsed.getTitle() : document.getTitle())
                .set(Document::getDifficulty, parsed.getDifficulty())
                .set(Document::getCalories, parsed.getCalories()));
    }

    /** 直更状态（绕过 @Version，防止 READY 更新误增版本导致 chunk 版本错位） */
    private void markStatus(long docId, String status, String errorMsg, int chunkCount) {
        documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                .eq(Document::getId, docId)
                .set(Document::getStatus, status)
                .set(Document::getErrorMsg, errorMsg)
                .set(Document::getChunkCount, chunkCount));
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 900 ? message : message.substring(0, 900);
    }

    /** 当前文档有效 chunk 数 */
    private int countChunks(long docId) {
        return documentChunkMapper.selectCount(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocId, docId)).intValue();
    }
}
