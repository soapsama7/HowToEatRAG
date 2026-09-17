package com.anfioo.howtocook.common.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户偏好表，对应 {@code user_preference}（每用户一行，user_id 唯一）。
 * <p>Agent 请求时读取并注入 Prompt（Step 1.4 的 loadAsPromptText）。</p>
 */
@Data
@TableName("user_preference")
public class UserPreference {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID，唯一（每用户一条偏好） */
    private Long userId;

    /** 辣度：0 不辣 1 微辣 2 中辣 3 重辣 */
    private Integer spiceLevel;

    /** 饮食类型：NONE / VEGETARIAN / VEGAN / HALAL / LOW_FAT ... */
    private String dietType;

    /** 过敏原，逗号分隔，如 "虾,花生" */
    private String allergens;

    /** 忌口食材，逗号分隔 */
    private String dislikedIngredients;

    /** 喜好食材，逗号分隔 */
    private String favoriteIngredients;

    /** 健康目标自由文本 */
    private String healthGoal;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
