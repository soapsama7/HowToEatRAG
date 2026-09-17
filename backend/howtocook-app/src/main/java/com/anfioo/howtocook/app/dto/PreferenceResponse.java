package com.anfioo.howtocook.app.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 用户偏好响应体（GET /api/preferences）。未设置时返回默认值而非报错（Step 1.4 验收要求）。
 */
@Getter
@Builder
public class PreferenceResponse {

    /** 辣度：0 不辣 1 微辣 2 中辣 3 重辣（默认 0） */
    private final Integer spiceLevel;

    /** 饮食类型（默认 NONE） */
    private final String dietType;

    /** 过敏原，逗号分隔（默认空） */
    private final String allergens;

    /** 忌口食材（默认空） */
    private final String dislikedIngredients;

    /** 喜好食材（默认空） */
    private final String favoriteIngredients;

    /** 健康目标（默认空） */
    private final String healthGoal;
}
