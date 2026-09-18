package com.anfioo.howtocook.app.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.service.AuditLogService;
import com.anfioo.howtocook.common.entity.sys.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 审计日志切面：环绕拦截标注 {@link AuditOperation} 的方法，采集
 * 用户 / 方法路径 / 入参摘要（截断 500 字符 + 脱敏）/ 结果 / 耗时 / IP，异步入库。
 * <p>业务异常照常向上抛出，仅影响审计记录的 result 字段（FAIL）。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogService auditLogService;

    @Around(value = "@annotation(auditOperation)", argNames = "joinPoint,auditOperation")
    public Object around(ProceedingJoinPoint joinPoint, AuditOperation auditOperation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            record(auditOperation.operation(), joinPoint, "SUCCESS", System.currentTimeMillis() - start);
            return result;
        } catch (Throwable e) {
            record(auditOperation.operation(), joinPoint, "FAIL", System.currentTimeMillis() - start);
            throw e;
        }
    }

    private void record(String operation, ProceedingJoinPoint joinPoint, String result, long costMs) {
        try {
            HttpServletRequest request = currentRequest();

            AuditLog auditLog = new AuditLog();
            auditLog.setOperation(operation);
            auditLog.setMethod(request == null ? null
                    : request.getMethod() + " " + request.getRequestURI());
            auditLog.setParams(auditLogService.maskSensitive(
                    auditLogService.summarizeParams(joinPoint.getArgs())));
            auditLog.setResult(result);
            auditLog.setIp(request == null ? null : resolveClientIp(request));
            auditLog.setCostMs(costMs);
            fillUser(auditLog);

            auditLogService.saveAsync(auditLog);
        } catch (Exception e) {
            // 审计失败绝不影响主业务
            log.warn("审计日志采集失败: operation={}, error={}", operation, e.getMessage());
        }
    }

    /** 填充操作用户（未登录场景 userId/username 为空）；username 优先取登录会话 */
    private void fillUser(AuditLog auditLog) {
        try {
            long userId = StpUtil.getLoginIdAsLong();
            auditLog.setUserId(userId);
            Object username = StpUtil.getSession().get("username");
            auditLog.setUsername(username == null ? null : username.toString());
        } catch (Exception ignored) {
            // 未登录调用：审计记录保留空用户
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /** 客户端 IP：优先 X-Forwarded-For 首段，否则 remoteAddr */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
