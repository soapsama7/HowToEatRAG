package com.anfioo.howtocook.common.enums;

import lombok.Getter;

/**
 * 文档（菜谱）索引状态，对应 {@code document.status} 字段。
 * <p>状态流转：PENDING → INDEXING → READY / FAILED</p>
 */
@Getter
public enum DocStatus {

    /** 已上传待索引 */
    PENDING("已上传待索引"),
    /** 索引中 */
    INDEXING("索引中"),
    /** 索引完成，可检索（Agent 检索仅命中该状态） */
    READY("可检索"),
    /** 索引失败 */
    FAILED("索引失败");

    private final String desc;

    DocStatus(String desc) {
        this.desc = desc;
    }
}
