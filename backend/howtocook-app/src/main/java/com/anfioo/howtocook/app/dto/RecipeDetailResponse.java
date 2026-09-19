package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户侧菜谱详情（RustFS 原文全文；不暴露 objectKey/uploaderId 等管理字段）。
 */
@Data
@Builder
public class RecipeDetailResponse {

    /** 文档 ID */
    private Long id;

    /** 菜谱标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 难度星级 1-5 */
    private Integer difficulty;

    /** 预估卡路里（大卡） */
    private Integer calories;

    /** 菜谱原文全文（RustFS 原始 markdown） */
    private String content;
}
