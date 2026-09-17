package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求体。
 */
@Data
public class RegisterRequest {

    /** 用户名：3-20 位字母/数字/下划线，注册后不可改 */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名须为 3-20 位字母、数字或下划线")
    private String username;

    /** 明文密码：8-32 位（服务端 BCrypt 加密后入库） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度须为 8-32 位")
    private String password;

    /** 昵称（可选，缺省取用户名） */
    @Size(max = 50, message = "昵称最长 50 字符")
    private String nickname;
}
