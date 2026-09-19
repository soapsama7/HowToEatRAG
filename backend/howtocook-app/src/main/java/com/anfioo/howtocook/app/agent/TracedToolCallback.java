package com.anfioo.howtocook.app.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 工具调用包装器（Step 4.2）：委托执行 MCP 工具回调，同时
 * ① 记录 trace（TOOL_CALL / TOOL_RESULT，含耗时与结果摘要，不含思维链）；
 * ② 从工具结果提取引用（search_chunks → chunk 列表；get_recipe_detail → 标记 fullRecipeFetched）；
 * ③ 强制约束：最大工具调用轮数（超出拒执行并提示模型收尾）、单工具超时 30s、失败重试 1 次。
 */
@Slf4j
public class TracedToolCallback implements ToolCallback {

    /** 单次工具调用超时（秒） */
    private static final long TOOL_TIMEOUT_SECONDS = 30;

    /** 失败重试次数 */
    private static final int MAX_RETRY = 1;

    /** 结果摘要最大长度（trace 内） */
    private static final int SUMMARY_MAX = 300;

    private final ToolCallback delegate;
    private final AgentRunContext context;
    private final ObjectMapper objectMapper;
    private final ExecutorService timeoutExecutor;

    public TracedToolCallback(ToolCallback delegate, AgentRunContext context, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.context = context;
        this.objectMapper = objectMapper;
        this.timeoutExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "agent-tool-runner");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String toolName = delegate.getToolDefinition().name();
        int round = context.getToolCallCount().incrementAndGet();
        int step = context.getStep().incrementAndGet();

        // TOOL_START 实时事件（SSE）
        Map<String, Object> startEvent = new LinkedHashMap<>();
        startEvent.put("step", step);
        startEvent.put("tool", toolName);
        startEvent.put("event", "TOOL_START");
        context.record(startEvent);

        if (round > 5) {
            String forced = "（工具调用轮数已达上限，请基于以上信息直接回答用户）";
            recordTrace(step, toolName, "TOOL_RESULT", 0, false, forced);
            return forced;
        }

        long start = System.currentTimeMillis();
        try {
            String output = executeWithTimeoutAndRetry(toolInput, toolContext);
            long elapsed = System.currentTimeMillis() - start;
            recordTrace(step, toolName, "TOOL_RESULT", elapsed, true, summarize(output));
            collectReferences(toolName, output);
            return output;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("工具调用失败: tool={}, error={}", toolName, e.getMessage());
            recordTrace(step, toolName, "TOOL_RESULT", elapsed, false, "工具执行失败: " + e.getMessage());
            return "工具执行失败：" + e.getMessage();
        }
    }

    /** 超时控制（30s）+ 失败重试 1 次 */
    private String executeWithTimeoutAndRetry(String toolInput, ToolContext toolContext) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            Future<String> future = timeoutExecutor.submit(() -> delegate.call(toolInput, toolContext));
            try {
                return future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                future.cancel(true);
                last = e;
                log.warn("工具执行异常（第 {} 次）: {}", attempt + 1, e.getMessage());
            }
        }
        throw last;
    }

    /** 提取引用：search_chunks 结果为 chunk JSON 数组；get_recipe_detail 标记对应文档已取全文 */
    private void collectReferences(String toolName, String output) {
        try {
            JsonNode payload = unwrapToolPayload(objectMapper.readTree(output));
            if ("search_chunks".equals(toolName) && payload.isArray()) {
                for (JsonNode node : payload) {
                    long chunkId = node.path("chunkId").asLong();
                    Map<String, Object> ref = new LinkedHashMap<>();
                    ref.put("docId", node.path("docId").asLong());
                    ref.put("chunkId", chunkId);
                    ref.put("title", node.path("title").asText(null));
                    ref.put("section", node.path("section").asText(null));
                    ref.put("content", node.path("content").asText(null));
                    ref.put("score", node.path("score").asDouble());
                    ref.put("retrievalType", node.path("retrievalType").asText(null));
                    ref.put("fullRecipeFetched", false);
                    Integer existing = context.getReferenceIndexByChunk().get(chunkId);
                    if (existing == null) {
                        context.getReferences().add(ref);
                        context.getReferenceIndexByChunk().put(chunkId, context.getReferences().size() - 1);
                    } else {
                        // 同 chunk 多次命中：保留较高分
                        double old = (Double) context.getReferences().get(existing).get("score");
                        if (node.path("score").asDouble() > old) {
                            context.getReferences().get(existing).put("score", node.path("score").asDouble());
                        }
                    }
                }
            } else if ("get_recipe_detail".equals(toolName) && payload.isObject() && payload.has("docId")) {
                long docId = payload.path("docId").asLong();
                boolean marked = false;
                for (Map<String, Object> ref : context.getReferences()) {
                    if (Long.valueOf(docId).equals(ref.get("docId"))) {
                        ref.put("fullRecipeFetched", true);
                        marked = true;
                    }
                }
                if (!marked) {
                    Map<String, Object> ref = new LinkedHashMap<>();
                    ref.put("docId", docId);
                    ref.put("chunkId", null);
                    ref.put("title", payload.path("title").asText(null));
                    ref.put("section", null);
                    ref.put("content", null);
                    ref.put("score", null);
                    ref.put("retrievalType", null);
                    ref.put("fullRecipeFetched", true);
                    context.getReferences().add(ref);
                }
            }
        } catch (Exception e) {
            log.debug("引用提取跳过（非 JSON 或无引用）: {}", e.getMessage());
        }
    }

    /**
     * 解包工具载荷：MCP 回调返回形如 {@code [{"text":"<工具真实 JSON>"}]} 的 content 包装，
     * 需取首个 text 字段再解析为真实载荷。
     */
    private JsonNode unwrapToolPayload(JsonNode root) throws Exception {
        if (root.isArray() && !root.isEmpty() && root.get(0).has("text")) {
            return objectMapper.readTree(root.get(0).path("text").asText());
        }
        if (root.isObject() && root.has("text") && root.get("text").isTextual()) {
            return objectMapper.readTree(root.path("text").asText());
        }
        return root;
    }

    private void recordTrace(int step, String tool, String event, long elapsedMs, boolean ok, String summary) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("step", step);
        entry.put("tool", tool);
        entry.put("event", event);
        entry.put("elapsedMs", elapsedMs);
        entry.put("ok", ok);
        entry.put("summary", summary);
        context.record(entry);
    }

    private String summarize(String output) {
        if (output == null) {
            return null;
        }
        return output.length() <= SUMMARY_MAX ? output : output.substring(0, SUMMARY_MAX) + "...(截断)";
    }
}
