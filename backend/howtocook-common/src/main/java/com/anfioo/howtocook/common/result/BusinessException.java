package com.anfioo.howtocook.common.result;

import lombok.Getter;

/**
 * 业务异常：Service 层校验不通过时抛出，由 GlobalExceptionHandler 统一转换为 {@link Result}。
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
