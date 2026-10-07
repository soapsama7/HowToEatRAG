package com.anfioo.howtocook.common.entity.sys;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 行为日志表，对应 {@code agent_run_log}（优化 2.8）。
 * <p>记录 agent 每次运行的工具调用轨迹、最终回答与出错信息；按会话隔离，仅 ROOT 可查。
 * 与 {@code audit_log}（管理员操作审计）区分。</p>
 */
@Data
@TableName("agent_run_log")
public class AgentRunLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 ID */
    private Long conversationId;

    /** 触发对话的用户 ID */
    private Long userId;

    /** 事件类型：TOOL_START / TOOL_RESULT / ANSWER / ERROR */
    private String event;

    /** 工具名（TOOL_START / TOOL_RESULT 时记录） */
    private String toolName;

    /** 耗时毫秒（TOOL_RESULT） */
    private Long elapsedMs;

    /** 成败（TOOL_RESULT / ERROR） */
    private Boolean ok;

    /** 结果摘要 / 最终回答 / 错误信息（截断） */
    private String summary;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
