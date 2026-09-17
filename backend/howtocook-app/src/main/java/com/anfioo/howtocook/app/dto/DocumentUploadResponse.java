package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 文档上传响应体：文档 ID + 索引任务号（消息由 Step 2.3 异步发送）。
 */
@Getter
@Builder
public class DocumentUploadResponse {

    /** 文档 ID */
    private final Long docId;

    /** 索引任务编号 */
    private final String taskNo;
}
