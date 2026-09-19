package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 用户侧菜谱列表条目（仅 READY 文档）。
 */
@Data
@Builder
public class DocumentSummaryResponse {

    /** 文档 ID */
    private Long id;

    /** 菜谱标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 难度星级 1-5 */
    private Integer difficulty;

    /** 预计时长（分钟） */
    private Integer cookMinutes;

    /** 预估卡路里（大卡） */
    private Integer calories;
}
