package com.anfioo.howtocook.app.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 历史消息响应 DTO（开发文档 §6：含 trace 与 references，管理调试用）。
 * <p>references / trace 在实体层为 JSONB 字符串，出参解析为 JSON 结构。</p>
 */
@Data
@Builder
public class MessageResponse {

    /** 消息 ID */
    private Long id;

    /** 角色：USER / ASSISTANT / TOOL / SYSTEM */
    private String role;

    /** 消息类型：USER_MESSAGE / AGENT_THINKING / TOOL_CALL / TOOL_RESULT / REFERENCE / FINAL_ANSWER */
    private String messageType;

    /** 正文 */
    private String content;

    /** 工具名（TOOL_CALL / TOOL_RESULT 时有值） */
    private String toolName;

    /** 引用列表（JSON 数组） */
    private JsonNode references;

    /** Agent 执行轨迹（JSON 数组） */
    private JsonNode trace;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
