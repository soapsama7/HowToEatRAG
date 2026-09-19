package com.anfioo.howtocook.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SSE 事件（开发文档 §5.6 契约）：
 * AGENT_START / TOOL_START / TOOL_RESULT / REFERENCE / ANSWER_DELTA / DONE / ERROR。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AgentEvent {

    /** 事件名（即 SSE event 字段） */
    private String type;

    /** 事件数据（即 SSE data 字段，JSON 序列化） */
    private Map<String, Object> data;

    public static AgentEvent of(String type, Map<String, Object> data) {
        return new AgentEvent(type, data);
    }
}
