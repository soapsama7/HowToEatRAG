package com.anfioo.howtocook.common.entity.doc;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档提交申请表，对应 {@code doc_submission}（优化 B2）。
 * <p>duplicate_hint 为 JSONB 列：实体以 JSON 字符串存取，依赖 JDBC URL 的
 * {@code stringtype=unspecified} 完成 varchar→jsonb 隐式转换（同 message.references 约定）。</p>
 */
@Data
@TableName("doc_submission")
public class DocSubmission {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 提交人用户 ID */
    private Long userId;

    /** 菜谱/技巧标题 */
    private String title;

    /** 文档类型：RECIPE / TIP / OTHER */
    private String docType;

    /** 分类 */
    private String category;

    /** markdown 原文 */
    private String content;

    /** 状态：PENDING / APPROVED / REJECTED */
    private String status;

    /** 拒绝原因（REJECTED 时填写） */
    private String rejectReason;

    /** 相似文档提示 JSON：[{docId,title,similarity}] */
    private String duplicateHint;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
