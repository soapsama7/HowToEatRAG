package com.anfioo.howtocook.common.enums.sys;

import lombok.Getter;

/**
 * 索引任务状态，对应 {@code index_task.status} 字段。
 * <p>状态流转：PENDING → PROCESSING → SUCCESS / FAILED（FAILED 可重试回 PENDING）</p>
 */
@Getter
public enum IndexTaskStatus {

    /** 待处理 */
    PENDING("待处理"),
    /** 处理中 */
    PROCESSING("处理中"),
    /** 成功 */
    SUCCESS("成功"),
    /** 失败 */
    FAILED("失败");

    private final String desc;

    IndexTaskStatus(String desc) {
        this.desc = desc;
    }
}
