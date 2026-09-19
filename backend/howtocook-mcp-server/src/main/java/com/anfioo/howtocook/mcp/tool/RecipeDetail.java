package com.anfioo.howtocook.mcp.tool;

import lombok.Data;

/**
 * get_recipe_detail 工具出参（开发文档 §5.4 契约，独立 DTO 文件）。
 */
@Data
public class RecipeDetail {

    /** 文档 ID */
    private Long docId;

    /** 菜谱标题 */
    private String title;

    /** 分类（目录名映射的中文分类） */
    private String category;

    /** 难度星级 1-5（从 ★ 解析） */
    private Integer difficulty;

    /** 预估卡路里（大卡） */
    private Integer calories;

    /** 菜谱原文全文（RustFS 原始 markdown） */
    private String content;
}
