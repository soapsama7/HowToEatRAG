package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.common.entity.sys.AuditLog;
import com.anfioo.howtocook.common.mapper.sys.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 审计日志落库服务：独立单线程池异步入库，主请求零阻塞。
 * <p>命名线程池 + 有界队列 + 丢弃时告警日志；{@code @PreDestroy} 关闭钩子确保停机前排空队列。</p>
 */
@Slf4j
@Service
public class AuditLogService {

    /** 入参摘要最大长度（超出截断），与 DDL audit_log.params 注释一致 */
    private static final int PARAMS_MAX_LENGTH = 500;

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /** 审计落库线程池：单线程即可满足异步写；拒绝时丢弃并告警（审计允许有损，不影响主业务） */
    private final ThreadPoolExecutor auditExecutor = new ThreadPoolExecutor(
            1, 1, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "audit-log-writer");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                    log.warn("审计日志队列已满，丢弃一条审计记录（queueSize={}）", e.getQueue().size());
                }
            });

    public AuditLogService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    /** 异步入库一条审计记录 */
    public void saveAsync(AuditLog auditLog) {
        auditExecutor.execute(() -> {
            try {
                auditLogMapper.insert(auditLog);
            } catch (Exception e) {
                log.warn("审计日志入库失败: operation={}, error={}", auditLog.getOperation(), e.getMessage());
            }
        });
    }

    /**
     * 入参摘要：JSON 序列化后截断至 500 字符；password 字段值脱敏；不可序列化参数以占位符代替。
     */
    public String summarizeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] safeArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            safeArgs[i] = toSafeArg(args[i]);
        }
        try {
            String json = objectMapper.writeValueAsString(safeArgs);
            return json.length() <= PARAMS_MAX_LENGTH ? json : json.substring(0, PARAMS_MAX_LENGTH) + "...(截断)";
        } catch (Exception e) {
            return "<unserializable>";
        }
    }

    private Object toSafeArg(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof Number || arg instanceof Boolean || arg instanceof String
                || arg instanceof Enum || arg instanceof java.time.temporal.Temporal) {
            return arg;
        }
        if (arg instanceof org.springframework.web.multipart.MultipartFile file) {
            return "<file:" + file.getOriginalFilename() + ":" + file.getSize() + "B>";
        }
        if (arg instanceof jakarta.servlet.ServletRequest
                || arg instanceof jakarta.servlet.ServletResponse
                || arg instanceof org.springframework.validation.BindingResult) {
            return "<" + arg.getClass().getSimpleName() + ">";
        }
        return arg;
    }

    /** 入参 JSON 脱敏：将 key 含 password 的字段值替换为 *** */
    public String maskSensitive(String json) {
        if (json == null) {
            return null;
        }
        return json.replaceAll("(\"[^\"]*password[^\"]*\"\\s*:\\s*)\"[^\"]*\"", "$1\"***\"");
    }

    /** 关闭钩子：停机时排空队列，不留尾巴 */
    @PreDestroy
    public void shutdown() {
        auditExecutor.shutdown();
        try {
            if (!auditExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("审计日志队列 5 秒内未排空，放弃 {} 条待写记录", auditExecutor.getQueue().size());
                auditExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auditExecutor.shutdownNow();
        }
    }
}
