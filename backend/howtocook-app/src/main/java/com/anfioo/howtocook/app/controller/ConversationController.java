package com.anfioo.howtocook.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.dto.AgentEvent;
import com.anfioo.howtocook.app.dto.ChatRequest;
import com.anfioo.howtocook.app.dto.ConversationResponse;
import com.anfioo.howtocook.app.dto.MessageResponse;
import com.anfioo.howtocook.app.agent.AgentService;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.common.entity.chat.Conversation;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * 会话接口（/api/conversations，要求登录）。
 * <p>SSE 对话端点按 §5.6 契约输出事件流：AGENT_START / TOOL_START / TOOL_RESULT /
 * REFERENCE / ANSWER_DELTA / DONE / ERROR，含心跳与断连中止。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final AgentService agentService;

    /** SSE 心跳线程（每 15s 一条注释帧，防止代理/网关空闲断连） */
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /** 新建会话（body 可空，title 可选） */
    @PostMapping("/api/conversations")
    public Result<ConversationResponse> create(@RequestParam(required = false) String title) {
        Conversation conversation = conversationService.create(StpUtil.getLoginIdAsLong(), title);
        return Result.ok(toResponse(conversation));
    }

    /** 我的会话分页（更新时间倒序） */
    @GetMapping("/api/conversations")
    public Result<Page<ConversationResponse>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "20") long pageSize) {
        Page<Conversation> page = conversationService.listMine(StpUtil.getLoginIdAsLong(), pageNum, pageSize);
        Page<ConversationResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toResponse).toList());
        return Result.ok(result);
    }

    /** 历史消息（含 trace 与 references） */
    @GetMapping("/api/conversations/{id}/messages")
    public Result<List<MessageResponse>> messages(@PathVariable long id) {
        return Result.ok(conversationService.messages(id, StpUtil.getLoginIdAsLong()));
    }

    /** 删除会话（逻辑删，仅归属者） */
    @DeleteMapping("/api/conversations/{id}")
    public Result<Void> delete(@PathVariable long id) {
        conversationService.delete(id, StpUtil.getLoginIdAsLong());
        return Result.ok();
    }

    /** SSE 对话（§5.6 事件契约） */
    @PostMapping(value = "/api/conversations/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable long id, @Valid @RequestBody ChatRequest request) {
        long userId = StpUtil.getLoginIdAsLong();
        SseEmitter emitter = new SseEmitter(300_000L);
        AtomicBoolean disconnected = new AtomicBoolean(false);

        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onError(t -> disconnected.set(true));
        emitter.onCompletion(() -> { });

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

        Flux<AgentEvent> events = agentService.askStream(id, userId, request.getQuestion());
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

        // 客户端断开：停止心跳 + 中止模型生成（Flux 取消传播到 ChatClient）
        emitter.onCompletion(() -> {
            heartbeat.cancel(false);
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        return emitter;
    }

    private ConversationResponse toResponse(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
