package com.anfioo.howtocook.app.agent;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 一次运行的聚合结果（Step 4.2；SSE 阶段复用其 trace/references）。
 */
@Data
public class AgentRunResult {

    /** 最终回答全文 */
    private String finalAnswer;

    /** 工具调用轨迹（不含模型思维链） */
    private List<Map<String, Object>> trace;

    /** 引用列表（后端留存含 score/retrievalType，SSE DTO 层过滤） */
    private List<Map<String, Object>> references;
}
