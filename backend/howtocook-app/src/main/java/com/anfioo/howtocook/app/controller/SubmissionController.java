package com.anfioo.howtocook.app.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.aspect.AuditOperation;
import com.anfioo.howtocook.app.dto.SubmissionListResponse;
import com.anfioo.howtocook.app.dto.SubmissionRejectRequest;
import com.anfioo.howtocook.app.dto.SubmissionSubmitRequest;
import com.anfioo.howtocook.app.service.DocSubmissionService;
import com.anfioo.howtocook.common.entity.doc.DocSubmission;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档提交申请接口（优化 B2）。
 * <p>提交：任意登录用户；审核（列表/同意/拒绝）：/api/admin/** 下，由 SaTokenConfig 要求 ADMIN/ROOT。</p>
 */
@RestController
@RequiredArgsConstructor
public class SubmissionController {

    private final DocSubmissionService submissionService;

    /** 用户提交文档申请 */
    @PostMapping("/api/submissions")
    public Result<DocSubmission> submit(@Valid @RequestBody SubmissionSubmitRequest request) {
        return Result.ok(submissionService.submit(StpUtil.getLoginIdAsLong(), request));
    }

    /** 审核列表（分页 + 状态筛选） */
    @GetMapping("/api/admin/submissions")
    public Result<Page<SubmissionListResponse>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "20") long pageSize,
                                                     @RequestParam(required = false) String status) {
        return Result.ok(submissionService.list(pageNum, pageSize, status));
    }

    /** 同意（转正式文档并触发索引） */
    @AuditOperation(operation = "APPROVE_SUBMISSION", category = "DOC")
    @PostMapping("/api/admin/submissions/{id}/approve")
    public Result<Void> approve(@PathVariable long id) {
        submissionService.approve(id);
        return Result.ok();
    }

    /** 拒绝（填拒绝原因） */
    @AuditOperation(operation = "REJECT_SUBMISSION", category = "DOC")
    @PostMapping("/api/admin/submissions/{id}/reject")
    public Result<Void> reject(@PathVariable long id, @Valid @RequestBody SubmissionRejectRequest request) {
        submissionService.reject(id, request.getReason());
        return Result.ok();
    }
}
