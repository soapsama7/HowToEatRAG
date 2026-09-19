package com.anfioo.howtocook.app.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 一次 Agent 运行的上下文（Step 4.2）：收集工具调用轨迹（trace）与引用（references），
 * 并限制最大工具调用轮数。每个对话请求创建一个实例，随工具包装器传递。
 */
@Data
public class AgentRunContext {

    /** 工具调用次数（含失败），达到上限后包装器拒绝执行并提示模型收尾 */
    private final AtomicInteger toolCallCount = new AtomicInteger(0);

    /** 工具调用轨迹：[{step, tool, event, elapsedMs, ok, summary}]（不含模型思维链） */
    private final List<Map<String, Object>> trace = new ArrayList<>();

    /** 引用列表：[{docId, chunkId, title, section, content, score, retrievalType, fullRecipeFetched}]，按 chunkId 去重 */
    private final List<Map<String, Object>> references = new ArrayList<>();

    /** 已收集引用的 chunkId → 列表下标（去重、更新 fullRecipeFetched 用） */
    private final Map<Long, Integer> referenceIndexByChunk = new LinkedHashMap<>();

    /** trace 步骤序号 */
    private final AtomicInteger step = new AtomicInteger(0);
}
