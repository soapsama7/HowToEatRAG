package com.anfioo.howtocook.app.agent;

import com.anfioo.howtocook.app.dto.AgentEvent;
import com.anfioo.howtocook.app.dto.ReferenceView;
import com.anfioo.howtocook.app.service.AgentRunLogService;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.app.service.McpClientSupport;
import com.anfioo.howtocook.app.service.PreferenceService;
import com.anfioo.howtocook.app.service.RateLimitService;
import com.anfioo.howtocook.common.enums.chat.MessageType;
import com.anfioo.howtocook.common.enums.chat.MessageRole;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 服务（开发文档 §5.5 / Step 4.2/4.3）：Agentic RAG 决策链路。
 * <ul>
 *   <li>System Prompt：外部化模板（prompts/system.md）+ 用户偏好注入；</li>
 *   <li>工具：MCP search_chunks / get_recipe_detail（经 TracedToolCallback 包装，收集 trace/引用并强制轮数/超时约束）；</li>
 *   <li>记忆：AgentHistoryAdvisor 在 before 阶段读取最近 memory-window 条历史注入上下文（仅 USER_MESSAGE / FINAL_ANSWER）；</li>
 *   <li>落库：USER 消息与 ASSISTANT(FINAL_ANSWER) 消息由本服务持久化，trace/references 随 ASSISTANT 消息保存。</li>
 * </ul>
 */
@Slf4j
@Service
public class AgentService {

    /** 携带的最近消息条数（howtocook.agent.memory-window） */
    @Value("${howtocook.agent.memory-window:10}")
    private int memoryWindow;

    /** System Prompt 模板（外部化到 prompts/system.md，启动时加载一次） */
    @Value("classpath:prompts/system.md")
    private Resource systemPromptResource;

    private String systemPromptTemplate;

    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ConversationService conversationService;
    private final PreferenceService preferenceService;
    private final McpClientSupport mcpClientSupport;
    private final RateLimitService rateLimitService;
    private final AgentRunLogService agentRunLogService;
    private final ObjectMapper objectMapper;

    /** SSE 心跳线程（每 15s 一条注释帧，防止代理/网关空闲断连） */
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    public AgentService(ChatClient.Builder chatClientBuilder,
                        ToolCallbackProvider toolCallbackProvider,
                        ConversationService conversationService,
                        PreferenceService preferenceService,
                        McpClientSupport mcpClientSupport,
                        RateLimitService rateLimitService,
                        AgentRunLogService agentRunLogService,
                        ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.toolCallbackProvider = toolCallbackProvider;
        this.conversationService = conversationService;
        this.preferenceService = preferenceService;
        this.mcpClientSupport = mcpClientSupport;
        this.rateLimitService = rateLimitService;
        this.agentRunLogService = agentRunLogService;
        this.objectMapper = objectMapper;
    }

    /** 启动时加载一次 System Prompt 模板（外部化，含 %s 偏好占位） */
    @PostConstruct
    public void loadSystemPrompt() {
        try {
            systemPromptTemplate = new String(systemPromptResource.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 System Prompt 模板失败: prompts/system.md", e);
        }
    }

    /** 应用关闭时停掉心跳线程池 */
    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    /**
     * 流式问答（Step 4.3）：按 §5.6 契约产出事件流。
     * <p>客户端断开时（Flux 被取消）底层模型调用一并中止；最终回答取"最后一次工具调用之后的
     * 内容段"（模型在工具调用前可能输出的引导语计入流但不作为最终答案落库）。</p>
     */
    public Flux<AgentEvent> askStream(long conversationId, long userId, String question) {
        // 准备阶段（eager，先于 Flux 订阅）：归属校验 / MCP 补连 / 工具包装 / Prompt / USER 消息落库
        conversationService.getOwned(conversationId, userId);
        mcpClientSupport.ensureInitialized();
        AgentRunContext context = new AgentRunContext();
        ToolCallback[] tools = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(cb -> (ToolCallback) new TracedToolCallback(cb, context, objectMapper))
                .toArray(ToolCallback[]::new);
        String systemPrompt = String.format(systemPromptTemplate,
                preferenceService.loadAsPromptText(userId));
        conversationService.appendMessage(conversationId, MessageRole.USER.name(),
                MessageType.USER_MESSAGE.name(), question, null, null, null);
        conversationService.updateTitleIfDefault(conversationId, question);
        // 历史注入交给 AgentHistoryAdvisor（before 阶段），不在本方法手动 buildHistory
        AgentHistoryAdvisor historyAdvisor = new AgentHistoryAdvisor(conversationService, memoryWindow, conversationId);

        StringBuilder currentSegment = new StringBuilder();
        AtomicReference<String> finalAnswer = new AtomicReference<>("");

        return Flux.create(sink -> {
            // 工具实时事件：TOOL_START / TOOL_RESULT
            context.setTraceListener(entry -> {
                String event = String.valueOf(entry.get("event"));
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("toolName", entry.get("tool"));
                data.put("step", entry.get("step"));
                if ("TOOL_RESULT".equals(event)) {
                    data.put("summary", entry.get("summary"));
                    data.put("elapsedMs", entry.get("elapsedMs"));
                }
                sink.next(AgentEvent.of(event, data));
                // 记录到 agent_run_log（优化 2.8）：工具事件实时落库
                Long elapsedMs = entry.get("elapsedMs") == null ? null
                        : ((Number) entry.get("elapsedMs")).longValue();
                agentRunLogService.record(conversationId, userId, event,
                        String.valueOf(entry.get("tool")), elapsedMs,
                        (Boolean) entry.get("ok"),
                        entry.get("summary") == null ? null : String.valueOf(entry.get("summary")));
                // 工具返回后重置内容段：最终答案 = 最后一次工具调用之后的内容
                if ("TOOL_RESULT".equals(event)) {
                    finalAnswer.set(currentSegment.toString());
                    currentSegment.setLength(0);
                }
            });

            sink.next(AgentEvent.of("AGENT_START",
                    Map.of("conversationId", conversationId)));

            Disposable disposable = chatClient.prompt()
                    .system(systemPrompt)
                    .user(question)
                    .advisors(historyAdvisor)
                    .toolCallbacks(tools)
                    .stream()
                    .chatResponse()
                    .subscribe(response -> {
                        if (response == null || response.getResult() == null
                                || response.getResult().getOutput() == null) {
                            return;
                        }
                        String delta = response.getResult().getOutput().getText();
                        if (delta != null && !delta.isEmpty()) {
                            currentSegment.append(delta);
                            sink.next(AgentEvent.of("ANSWER_DELTA", Map.of("delta", delta)));
                        }
                    }, error -> {
                        log.error("Agent 流式调用失败: conversationId={}, error={}",
                                conversationId, error.getMessage(), error);
                        // 出错也落库（优化 2.8：补观测盲区，特别记录出错部分）
                        agentRunLogService.record(conversationId, userId, "ERROR", null, null,
                                false, error.getMessage());
                        sink.next(AgentEvent.of("ERROR", Map.of(
                                "code", ErrorCode.SERVICE_UNAVAILABLE.getCode(),
                                "message", "对话服务暂时不可用，请稍后再试")));
                        sink.complete();
                    }, () -> {
                        finalAnswer.set(currentSegment.toString());
                        // 记录最终回答到 agent_run_log（优化 2.8）
                        agentRunLogService.record(conversationId, userId, "ANSWER", null, null,
                                true, finalAnswer.get());
                        // 最终回答 + trace/references 落库
                        Long messageId = conversationService.appendMessage(conversationId,
                                MessageRole.ASSISTANT.name(), MessageType.FINAL_ANSWER.name(),
                                finalAnswer.get(), null,
                                toJson(context.getReferences()),
                                toJson(context.getTrace()));
                        // REFERENCE：DTO 层过滤 score/retrievalType
                        List<ReferenceView> refs = context.getReferences().stream()
                                .map(r -> ReferenceView.builder()
                                        .docId((Long) r.get("docId"))
                                        .title((String) r.get("title"))
                                        .section((String) r.get("section"))
                                        .content((String) r.get("content"))
                                        .fullRecipeFetched((Boolean) r.get("fullRecipeFetched"))
                                        .build())
                                .toList();
                        sink.next(AgentEvent.of("REFERENCE", Map.of("references", refs)));
                        sink.next(AgentEvent.of("DONE", Map.of("messageId", messageId)));
                        sink.complete();
                    });
            // 客户端断开 → 中止模型生成
            sink.onCancel(disposable::dispose);
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * SSE 对话入口（Step 4.3 / 优化 2.3）：限流闸门 + SseEmitter + 心跳 + Flux 桥接下沉到此，
     * Controller 只做参数校验与委托。事件契约（AGENT_START/TOOL_START/TOOL_RESULT/ANSWER_DELTA/REFERENCE/DONE/ERROR）
     * 由 {@link #askStream} 产出，本方法仅负责传输层编排。
     */
    public SseEmitter askSse(long conversationId, long userId, String question) {
        rateLimitService.acquireChatSlot(userId); // 频次 + 并发闸门（超限 429）
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean disconnected = new AtomicBoolean(false);

        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onError(t -> disconnected.set(true));

        // 心跳：注释帧不计入事件流
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (disconnected.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                disconnected.set(true);
            }
        }, 15, 15, TimeUnit.SECONDS);

        Flux<AgentEvent> events = askStream(conversationId, userId, question);
        Disposable subscription = events.subscribe(event -> {
            if (disconnected.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event.getType())
                        .data(event.getData(), MediaType.APPLICATION_JSON));
                if ("DONE".equals(event.getType()) || "ERROR".equals(event.getType())) {
                    heartbeat.cancel(false);
                    emitter.complete();
                }
            } catch (Exception e) {
                disconnected.set(true);
            }
        }, error -> {
            heartbeat.cancel(false);
            try {
                emitter.send(SseEmitter.event().name("ERROR").data(Map.of(
                        "code", 50000, "message", "服务器内部错误"),
                        MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception ignored) {
                log.debug("SSE 发送失败（客户端已断开）");
            }
        }, () -> {
            heartbeat.cancel(false);
            emitter.complete();
        });

        // 会话终结（正常结束/客户端断开/超时）：停止心跳、中止模型生成、释放限流槽位
        emitter.onCompletion(() -> {
            heartbeat.cancel(false);
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
            rateLimitService.releaseSlot(userId);
        });
        return emitter;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
