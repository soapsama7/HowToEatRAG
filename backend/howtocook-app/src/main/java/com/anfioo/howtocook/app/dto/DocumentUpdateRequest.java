package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改文档内容请求体（PUT /api/admin/documents/{id}）。
 * <p>content 为新的 Markdown 原文全文；docType / category 可选，不传保持原值。</p>
 */
@Data
public class DocumentUpdateRequest {

    /** 新的 Markdown 原文全文 */
    @NotBlank(message = "文档内容不能为空")
    private String content;

    /** 文档类型：RECIPE / TIP / OTHER（可选） */
    private String docType;

    /** 分类（可选） */
    @Size(max = 30, message = "分类最长 30 字符")
    private String category;
}
