package com.anfioo.howtocook.common.enums.doc;

import lombok.Getter;

/**
 * 文档提交申请状态，对应 {@code doc_submission.status} 字段。
 * <p>状态流转：PENDING → APPROVED / REJECTED</p>
 */
@Getter
public enum SubmissionStatus {

    /** 待审核 */
    PENDING("待审核"),
    /** 已通过（已转正式文档） */
    APPROVED("已通过"),
    /** 已拒绝 */
    REJECTED("已拒绝");

    private final String desc;

    SubmissionStatus(String desc) {
        this.desc = desc;
    }
}
