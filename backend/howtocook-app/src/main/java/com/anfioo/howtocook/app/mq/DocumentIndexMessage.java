package com.anfioo.howtocook.app.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档索引消息体（doc-index-topic）。字段精简（Review 要点），详情由消费端按 docId 查库。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentIndexMessage {

    /** 索引任务编号（幂等锁 key） */
    private String taskNo;

    /** 文档 ID */
    private Long docId;
}
