package com.anfioo.howtocook.common.result;

import lombok.Getter;

/**
 * 统一响应体：所有 REST 接口的返回值外壳。
 * <p>成功时 code = 0；失败时 code 为 {@link ErrorCode} 中定义的业务错误码，HTTP 状态码由 {@link ErrorCode#getHttpStatus()} 决定。</p>
 *
 * @param <T> 业务数据类型
 */
@Getter
public class Result<T> {

    /** 业务错误码，0 表示成功 */
    private final int code;

    /** 提示信息 */
    private final String message;

    /** 业务数据，失败时为 null */
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 是否为成功响应 */
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
