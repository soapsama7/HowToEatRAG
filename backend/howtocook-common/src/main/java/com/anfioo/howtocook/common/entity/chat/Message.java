package com.anfioo.howtocook.common.entity.chat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息表，对应 {@code message}。
 * <p>references / trace 为 JSONB 列：实体以 JSON 字符串存取，
 * 依赖 JDBC URL 的 {@code stringtype=unspecified} 完成 varchar→jsonb 隐式转换。</p>
 */
@Data
@TableName("message")
public class Message {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 ID */
    private Long conversationId;

    /** 消息角色：USER / ASSISTANT / TOOL / SYSTEM（见 MessageRole） */
    private String role;

    /** 消息类型：USER_MESSAGE / AGENT_THINKING / TOOL_CALL / TOOL_RESULT / REFERENCE / FINAL_ANSWER（见 MessageType） */
    private String messageType;

    /** 正文（回答内容 / 工具名 / 引用说明） */
    private String content;

    /** 工具名（TOOL_CALL / TOOL_RESULT 时记录） */
    private String toolName;

    /** 引用列表 JSON：[{docId,chunkId,title,section,content,score,retrievalType,fullRecipeFetched}]（references 为 PG 保留字，映射须带引号） */
    @TableField("\"references\"")
    private String references;

    /** Agent 执行轨迹 JSON（结构化步骤，非真实思维链） */
    private String trace;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
