package com.anfioo.howtocook.common.retrieval;

import lombok.Data;

/**
 * 检索命中结果（开发文档 §5.3 契约，独立 DTO 文件）。
 * <p>两路召回 SQL 以列别名 {@code chunk_id/doc_id/...} 直接映射本类；
 * score 在融合前为该路原始分，融合后为最终加权分。</p>
 */
@Data
public class ChunkResult {

    /** 所属文档 ID */
    private Long docId;

    /** chunk ID */
    private Long chunkId;

    /** 文档标题（菜谱名） */
    private String title;

    /** 所属 H2 章节名 */
    private String section;

    /** chunk 纯文本 */
    private String content;

    /** 相关性分数（融合后为 finalScore） */
    private Double score;

    /** 命中类型：VECTOR / KEYWORD / HYBRID（见 RetrievalType，融合阶段写入；接口 DTO 层不下发前端） */
    private String retrievalType;
}
