package com.anfioo.howtocook.mcp.tool;

import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.retrieval.ChunkResult;
import com.anfioo.howtocook.common.retrieval.HybridRetrievalService;
import com.anfioo.howtocook.common.storage.StorageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MCP 工具集（开发文档 §5.4 契约）：
 * ① search_chunks：混合检索知识库片段（向量 + 关键词加权融合）；
 * ② get_recipe_detail：doc_id → document.object_key → RustFS 原文（三跳定位）。
 * <p>仅暴露这两个工具，不提供任何其他端点能力；底层检索/存储能力复用 common 模块。</p>
 */
@Component
public class RecipeTools {

    /** topK 未传时的默认返回条数（与主应用 howtocook.retrieval.top-k 默认一致） */
    private static final int DEFAULT_TOP_K = 5;

    private final HybridRetrievalService retrievalService;
    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    public RecipeTools(HybridRetrievalService retrievalService,
                       DocumentMapper documentMapper,
                       StorageService storageService) {
        this.retrievalService = retrievalService;
        this.documentMapper = documentMapper;
        this.storageService = storageService;
    }

    /** 混合检索：query 向量化 + 关键词召回 → 归一化加权融合 → Top K 知识片段 */
    @Tool(name = "search_chunks", description = "在 HowToCook 菜谱知识库中检索与问题最相关的知识片段"
            + "（菜谱做法/原料用量/烹饪技巧等），混合了语义向量与关键词两路召回并加权融合。"
            + "回答做菜问题时应优先调用本工具。")
    public List<ChunkResult> searchChunks(
            @ToolParam(description = "检索问题，如：红烧鱼怎么做、可乐鸡翅需要哪些原料") String query,
            @ToolParam(required = false, description = "返回的最大条数，缺省为 5")
            Integer topK) {
        int effectiveTopK = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
        return retrievalService.retrieve(query, effectiveTopK);
    }

    /** 完整菜谱原文：按 docId 查 object_key 后读 RustFS 原始 markdown（chunk 缺步骤/用量时的二阶段兜底） */
    @Tool(name = "get_recipe_detail", description = "按文档 ID 获取完整菜谱原文全文（含全部步骤、用量、注意事项）。"
            + "当检索到的片段缺少具体步骤或用量时，用 search_chunks 结果中的 docId 调用本工具。")
    public RecipeDetail getRecipeDetail(
            @ToolParam(description = "文档 ID（来自 search_chunks 结果的 docId 字段）") long docId) {
        Document document = documentMapper.selectById(docId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + docId);
        }
        RecipeDetail detail = new RecipeDetail();
        detail.setDocId(document.getId());
        detail.setTitle(document.getTitle());
        detail.setCategory(document.getCategory());
        detail.setDifficulty(document.getDifficulty());
        detail.setCalories(document.getCalories());
        detail.setContent(new String(storageService.getObject(document.getObjectKey()), StandardCharsets.UTF_8));
        return detail;
    }
}
