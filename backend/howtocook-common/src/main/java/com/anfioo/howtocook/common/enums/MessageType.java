package com.anfioo.howtocook.common.enums;

import lombok.Getter;

/**
 * 消息类型（供前端渲染），对应 {@code message.message_type} 字段。
 * <p>两阶段检索会产生多次 TOOL_CALL / TOOL_RESULT。</p>
 */
@Getter
public enum MessageType {

    /** 用户消息 */
    USER_MESSAGE("用户消息"),
    /** Agent 执行过程（结构化步骤，非真实思维链） */
    AGENT_THINKING("执行过程"),
    /** 工具调用 */
    TOOL_CALL("工具调用"),
    /** 工具返回 */
    TOOL_RESULT("工具返回"),
    /** 引用资料 */
    REFERENCE("引用资料"),
    /** 最终回答 */
    FINAL_ANSWER("最终回答");

    private final String desc;

    MessageType(String desc) {
        this.desc = desc;
    }
}
