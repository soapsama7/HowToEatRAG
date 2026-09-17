package com.anfioo.howtocook.common.enums;

import lombok.Getter;

/**
 * 检索命中类型，对应引用数据 {@code retrievalType} 字段（后端调试用，不下发前端）。
 */
@Getter
public enum RetrievalType {

    /** 仅向量召回命中 */
    VECTOR("向量召回"),
    /** 仅关键词召回命中 */
    KEYWORD("关键词召回"),
    /** 两路均命中（归一化加权融合） */
    HYBRID("混合召回");

    private final String desc;

    RetrievalType(String desc) {
        this.desc = desc;
    }
}
