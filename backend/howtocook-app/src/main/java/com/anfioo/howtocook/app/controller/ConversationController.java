package com.anfioo.howtocook.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.agent.AgentService;
import com.anfioo.howtocook.app.dto.ChatRequest;
import com.anfioo.howtocook.app.dto.ConversationResponse;
import com.anfioo.howtocook.app.dto.MessageResponse;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.common.entity.chat.Conversation;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

/**
 * 会话接口（/api/conversations，要求登录）。
 * <p>SSE 对话端点委托 {@link AgentService#askSse}：限流闸门 / SseEmitter / 心跳 / Flux 桥接
 * 均已下沉到 Service，Controller 只做参数校验与委托（优化 2.3）。</p>
 */
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final AgentService agentService;

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

    /** SSE 对话（§5.6 事件契约；限流 / 心跳 / 桥接已下沉到 AgentService.askSse） */
    @PostMapping(value = "/api/conversations/{id}/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable long id, @Valid @RequestBody ChatRequest request) {
        return agentService.askSse(id, StpUtil.getLoginIdAsLong(), request.getQuestion());
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
