package com.anfioo.howtocook.common.document;

import lombok.Getter;

/**
 * 分块草稿：写入 document_chunk 前的中间模型。
 */
@Getter
public class ChunkDraft {

    /** 文档内全局递增序号（从 0 开始） */
    private final int chunkIndex;

    /** 所属章节名（简介块为"简介"） */
    private final String section;

    /** chunk 纯文本（含章节名前缀） */
    private final String content;

    /** token 数（按字符数估算） */
    private final int tokenCount;

    public ChunkDraft(int chunkIndex, String section, String content) {
        this.chunkIndex = chunkIndex;
        this.section = section;
        this.content = content;
        this.tokenCount = content.length();
    }
}
