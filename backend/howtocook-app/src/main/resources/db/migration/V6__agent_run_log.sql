-- =====================================================================
-- V6__agent_run_log.sql —— Agent 行为日志表（优化 2.8）
-- 与 audit_log（管理员操作审计）区分：本表记录 agent 每次运行的工具调用轨迹、
-- 最终回答与出错信息，按会话隔离，仅 ROOT 可查。
-- =====================================================================

CREATE TABLE agent_run_log (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,          -- 会话 ID（隔离维度）
    user_id         BIGINT,                         -- 触发对话的用户
    event           VARCHAR(30)  NOT NULL,          -- TOOL_START / TOOL_RESULT / ANSWER / ERROR
    tool_name       VARCHAR(100),                   -- TOOL_START/TOOL_RESULT 时记录
    elapsed_ms      BIGINT,                         -- TOOL_RESULT 耗时
    ok              BOOLEAN,                        -- TOOL_RESULT 成败 / ERROR=false
    summary         TEXT,                           -- 结果摘要 / 最终回答 / 错误信息（截断）
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_run_log_conv ON agent_run_log (conversation_id, created_at);
