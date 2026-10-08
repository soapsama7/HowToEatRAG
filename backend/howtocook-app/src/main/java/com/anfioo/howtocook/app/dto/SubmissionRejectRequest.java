package com.anfioo.howtocook.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 拒绝提交申请请求（POST /api/admin/submissions/{id}/reject）。
 */
@Getter
@Setter
public class SubmissionRejectRequest {

    /** 拒绝原因 */
    @NotBlank(message = "请填写拒绝原因")
    private String reason;
}
