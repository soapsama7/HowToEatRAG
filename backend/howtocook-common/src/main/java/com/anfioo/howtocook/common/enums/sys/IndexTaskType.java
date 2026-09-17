package com.anfioo.howtocook.common.enums.sys;

import lombok.Getter;

/**
 * 索引任务类型，对应 {@code index_task.task_type} 字段。
 */
@Getter
public enum IndexTaskType {

    /** 首次索引（上传触发） */
    INDEX("首次索引"),
    /** 重索引（version + 1） */
    REINDEX("重索引"),
    /** 删除后清理 chunk */
    DELETE_CLEAN("删除清理");

    private final String desc;

    IndexTaskType(String desc) {
        this.desc = desc;
    }
}
