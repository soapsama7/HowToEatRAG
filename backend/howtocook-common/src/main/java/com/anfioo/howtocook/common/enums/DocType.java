package com.anfioo.howtocook.common.enums;

import lombok.Getter;

/**
 * 文档类型，对应 {@code document.doc_type} 字段。
 */
@Getter
public enum DocType {

    /** 菜谱（dishes 目录） */
    RECIPE("菜谱"),
    /** 烹饪技巧（tips 目录） */
    TIP("烹饪技巧"),
    /** 其他 */
    OTHER("其他");

    private final String desc;

    DocType(String desc) {
        this.desc = desc;
    }
}
