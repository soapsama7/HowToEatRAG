package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.common.entity.sys.AgentRunLog;
import com.anfioo.howtocook.common.mapper.sys.AgentRunLogMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 行为日志落库服务（优化 2.8）：独立单线程池异步入库，不阻塞对话主链路。
 * <p>与 AuditLogService（管理员操作审计）物理分离；命名线程池 + 有界队列 + 丢弃告警 +
 * {@code @PreDestroy} 关闭钩子排空。</p>
 */
@Slf4j
@Service
public class AgentRunLogService {

    /** summary 最大长度（超出截断） */
    private static final int SUMMARY_MAX = 500;

    private final AgentRunLogMapper agentRunLogMapper;

    private final ThreadPoolExecutor runLogExecutor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(2000),
            r -> {
                Thread t = new Thread(r, "agent-run-log-writer");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                    log.warn("agent 行为日志队列已满，丢弃一条（queueSize={}）", e.getQueue().size());
                }
            });

    public AgentRunLogService(AgentRunLogMapper agentRunLogMapper) {
        this.agentRunLogMapper = agentRunLogMapper;
    }

    /**
     * 异步记录一条 agent 行为日志。
     *
     * @param event      事件类型（TOOL_START / TOOL_RESULT / ANSWER / ERROR）
     * @param toolName   工具名（工具事件时传）
     * @param elapsedMs  耗时（TOOL_RESULT）
     * @param ok         成败（TOOL_RESULT / ERROR）
     * @param summary    摘要 / 最终回答 / 错误信息（超长截断）
     */
    public void record(long conversationId, long userId, String event,
                       String toolName, Long elapsedMs, Boolean ok, String summary) {
        AgentRunLog runLog = new AgentRunLog();
        runLog.setConversationId(conversationId);
        runLog.setUserId(userId);
        runLog.setEvent(event);
        runLog.setToolName(toolName);
        runLog.setElapsedMs(elapsedMs);
        runLog.setOk(ok);
        runLog.setSummary(truncate(summary));
        runLogExecutor.execute(() -> {
            try {
                agentRunLogMapper.insert(runLog);
            } catch (Exception e) {
                log.warn("agent 行为日志入库失败: event={}, conversationId={}, error={}",
                        event, conversationId, e.getMessage());
            }
        });
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= SUMMARY_MAX ? s : s.substring(0, SUMMARY_MAX) + "...(截断)";
    }

    /** 关闭钩子：停机时排空队列 */
    @PreDestroy
    public void shutdown() {
        runLogExecutor.shutdown();
        try {
            if (!runLogExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("agent 行为日志队列 5 秒内未排空，放弃 {} 条", runLogExecutor.getQueue().size());
                runLogExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runLogExecutor.shutdownNow();
        }
    }
}
