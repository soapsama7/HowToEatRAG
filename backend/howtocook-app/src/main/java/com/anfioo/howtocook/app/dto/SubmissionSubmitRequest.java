package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户提交文档申请请求（POST /api/submissions）。
 */
@Getter
@Setter
public class SubmissionSubmitRequest {

    /** 菜谱/技巧标题 */
    @NotBlank(message = "标题不能为空")
    private String title;

    /** 文档类型：RECIPE / TIP / OTHER */
    private String docType;

    /** 分类 */
    private String category;

    /** markdown 原文 */
    @NotBlank(message = "内容不能为空")
    private String content;
}
