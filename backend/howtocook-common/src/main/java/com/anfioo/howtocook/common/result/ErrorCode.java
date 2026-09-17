package com.anfioo.howtocook.common.result;

import lombok.Getter;

/**
 * 全局业务错误码。
 * <p>code 为业务码（与 HTTP 状态码分段对应，便于排查）；httpStatus 决定 HTTP 响应状态。</p>
 */
@Getter
public enum ErrorCode {

    /** 成功 */
    SUCCESS(0, "成功", 200),

    /** 参数校验失败、参数格式错误 */
    BAD_REQUEST(40000, "请求参数错误", 400),
    /** 未登录 / Token 无效或过期 */
    UNAUTHORIZED(40100, "未登录或登录已过期", 401),
    /** 已登录但无权限（角色不足、非资源归属者） */
    FORBIDDEN(40300, "无权访问该资源", 403),
    /** 资源不存在 */
    NOT_FOUND(40400, "资源不存在", 404),
    /** 请求过于频繁（对话限流、并发上限） */
    RATE_LIMITED(42900, "请求过于频繁，请稍后再试", 429),

    /** 服务器内部错误（兜底） */
    INTERNAL_ERROR(50000, "服务器内部错误", 500),
    /** 依赖服务不可用（模型服务、对象存储、检索服务等） */
    SERVICE_UNAVAILABLE(50300, "依赖服务暂不可用，请稍后再试", 503);

    /** 业务错误码，0 为成功 */
    private final int code;

    /** 默认提示信息 */
    private final String message;

    /** 对应 HTTP 状态码 */
    private final int httpStatus;

    ErrorCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
