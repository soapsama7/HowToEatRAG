package com.anfioo.howtocook.common.enums;

import lombok.Getter;

/**
 * 消息角色，对应 {@code message.role} 字段。
 */
@Getter
public enum MessageRole {

    /** 用户 */
    USER("用户"),
    /** 助手（Agent 最终回答） */
    ASSISTANT("助手"),
    /** 工具消息 */
    TOOL("工具"),
    /** 系统消息 */
    SYSTEM("系统");

    private final String desc;

    MessageRole(String desc) {
        this.desc = desc;
    }
}
