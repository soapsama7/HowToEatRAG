package com.anfioo.howtocook.app.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：标注在需要审计的写操作接口上（当前主要面向 /api/admin/** 管理端接口）。
 * <p>由 {@link AuditLogAspect} 环绕拦截，异步落库到 audit_log 表。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作名（如 UPLOAD_DOCUMENT / DELETE_DOCUMENT），审计记录的业务标识 */
    String operation();
}
