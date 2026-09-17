package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 管理端文档详情（含 RustFS 原文）。
 */
@Getter
@Builder
public class DocumentDetailResponse {

    private final Long id;
    private final String title;
    private final String docType;
    private final String category;
    private final String objectKey;
    private final Long fileSize;
    private final Integer difficulty;
    private final Integer cookMinutes;
    private final Integer calories;
    private final Integer version;
    private final String status;
    private final String errorMsg;
    private final Integer chunkCount;
    private final Long uploaderId;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    /** 原文全文（RustFS 读取，UTF-8） */
    private final String content;
}
