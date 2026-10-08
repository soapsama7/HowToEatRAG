package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档提交申请列表/详情视图（含提交人用户名与相似度提示，供 admin/root 审核）。
 */
@Getter
@Builder
public class SubmissionListResponse {

    /** 申请 ID */
    private final Long id;

    /** 提交人用户 ID */
    private final Long userId;

    /** 提交人用户名 */
    private final String username;

    /** 标题 */
    private final String title;

    /** 文档类型 */
    private final String docType;

    /** 分类 */
    private final String category;

    /** 状态：PENDING / APPROVED / REJECTED */
    private final String status;

    /** 拒绝原因 */
    private final String rejectReason;

    /** 相似文档提示：[{docId,title,similarity}] */
    private final List<Map<String, Object>> duplicateHint;

    /** markdown 原文（审核时查看） */
    private final String content;

    /** 创建时间 */
    private final LocalDateTime createdAt;
}
