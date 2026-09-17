package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户偏好保存请求体（PUT /api/preferences，幂等 upsert）。
 * <p>dietType 合法性（枚举值）在 Service 层校验；全字段可选，未传字段按空处理。</p>
 */
@Data
public class PreferenceRequest {

    /** 辣度：0 不辣 1 微辣 2 中辣 3 重辣 */
    @Min(value = 0, message = "辣度取值 0-3")
    @Max(value = 3, message = "辣度取值 0-3")
    private Integer spiceLevel;

    /** 饮食类型：NONE / VEGETARIAN / VEGAN / HALAL / LOW_FAT（见 DietType） */
    @Size(max = 20, message = "dietType 长度非法")
    private String dietType;

    /** 过敏原，逗号分隔，如 "虾,花生" */
    @Size(max = 2000, message = "过敏原内容过长")
    private String allergens;

    /** 忌口食材，逗号分隔 */
    @Size(max = 2000, message = "忌口食材内容过长")
    private String dislikedIngredients;

    /** 喜好食材，逗号分隔 */
    @Size(max = 2000, message = "喜好食材内容过长")
    private String favoriteIngredients;

    /** 健康目标自由文本 */
    @Size(max = 200, message = "健康目标最长 200 字符")
    private String healthGoal;
}
