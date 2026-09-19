package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Data;

/**
 * SSE REFERENCE 事件的引用视图（Step 4.3）：
 * <p>在 DTO 层过滤 score / retrievalType 等后端调试字段（开发文档 §5.6 / Review 要点）。</p>
 */
@Data
@Builder
public class ReferenceView {

    /** 文档 ID */
    private Long docId;

    /** 文档标题 */
    private String title;

    /** 章节 */
    private String section;

    /** 片段内容 */
    private String content;

    /** 是否已取完整菜谱（两阶段检索标记） */
    private Boolean fullRecipeFetched;
}
