package com.anfioo.howtocook.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.dto.ConversationResponse;
import com.anfioo.howtocook.app.dto.MessageResponse;
import com.anfioo.howtocook.app.service.ConversationService;
import com.anfioo.howtocook.common.entity.chat.Conversation;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话接口（/api/conversations，要求登录）。
 * <p>SSE 对话端点 POST /{id}/chat 随 Step 4.3 交付（§5.6 事件契约）。</p>
 */
@RestController
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

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

    private ConversationResponse toResponse(Conversation conversation) {
        return ConversationResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
