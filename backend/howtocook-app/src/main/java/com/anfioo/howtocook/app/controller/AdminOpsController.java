package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.aspect.AuditOperation;
import com.anfioo.howtocook.app.service.AdminOpsService;
import com.anfioo.howtocook.common.entity.sys.AuditLog;
import com.anfioo.howtocook.common.entity.sys.IndexTask;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端任务与审计接口（/api/admin/**，要求 ADMIN 角色，Step 5.1）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminOpsController {

    private final AdminOpsService adminOpsService;

    /** 索引任务列表（分页 + 状态筛选） */
    @GetMapping("/index-tasks")
    public Result<Page<IndexTask>> indexTasks(@RequestParam(defaultValue = "1") long pageNum,
                                              @RequestParam(defaultValue = "20") long pageSize,
                                              @RequestParam(required = false) String status) {
        return Result.ok(adminOpsService.listIndexTasks(pageNum, pageSize, status));
    }

    /** 失败任务重试（置 PENDING 并重发索引消息） */
    @AuditOperation(operation = "RETRY_INDEX_TASK")
    @PostMapping("/index-tasks/{taskNo}/retry")
    public Result<IndexTask> retryIndexTask(@PathVariable String taskNo) {
        return Result.ok(adminOpsService.retryIndexTask(taskNo));
    }

    /** 审计日志分页查询 */
    @GetMapping("/audit-logs")
    public Result<Page<AuditLog>> auditLogs(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "20") long pageSize,
                                            @RequestParam(required = false) String operation) {
        return Result.ok(adminOpsService.listAuditLogs(pageNum, pageSize, operation));
    }
}
