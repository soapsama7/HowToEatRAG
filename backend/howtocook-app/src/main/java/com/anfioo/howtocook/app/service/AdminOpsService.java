package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.app.mq.DocumentIndexProducer;
import com.anfioo.howtocook.common.entity.sys.AuditLog;
import com.anfioo.howtocook.common.entity.sys.IndexTask;
import com.anfioo.howtocook.common.enums.sys.IndexTaskStatus;
import com.anfioo.howtocook.common.mapper.sys.AuditLogMapper;
import com.anfioo.howtocook.common.mapper.sys.IndexTaskMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 管理端运维服务（Step 5.1）：索引任务查询 / 失败重试 + 审计日志分页查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOpsService {

    private final IndexTaskMapper indexTaskMapper;
    private final AuditLogMapper auditLogMapper;
    private final DocumentIndexProducer documentIndexProducer;

    /** 索引任务分页（可按状态筛选，更新时间倒序） */
    public Page<IndexTask> listIndexTasks(long pageNum, long pageSize, String status) {
        Page<IndexTask> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<IndexTask> wrapper = new LambdaQueryWrapper<IndexTask>()
                .orderByDesc(IndexTask::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(IndexTask::getStatus, status);
        }
        return indexTaskMapper.selectPage(page, wrapper);
    }

    /** 失败任务重试：置 PENDING + 清空错误信息 + retry_count+1 + 重发索引消息 */
    public IndexTask retryIndexTask(String taskNo) {
        IndexTask task = indexTaskMapper.selectOne(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getTaskNo, taskNo));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在: " + taskNo);
        }
        if (!IndexTaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "仅 FAILED 任务可重试，当前状态: " + task.getStatus());
        }
        task.setStatus(IndexTaskStatus.PENDING.name());
        task.setErrorMsg(null);
        task.setRetryCount(task.getRetryCount() == null ? 1 : task.getRetryCount() + 1);
        indexTaskMapper.updateById(task);
        documentIndexProducer.send(task.getTaskNo(), task.getDocId());
        log.info("失败任务已重试并重发消息: taskNo={}, docId={}, retryCount={}",
                task.getTaskNo(), task.getDocId(), task.getRetryCount());
        return task;
    }

    /** 审计日志分页（时间倒序；operation 可选筛选） */
    public Page<AuditLog> listAuditLogs(long pageNum, long pageSize, String operation) {
        Page<AuditLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getId);
        if (operation != null && !operation.isBlank()) {
            wrapper.eq(AuditLog::getOperation, operation);
        }
        return auditLogMapper.selectPage(page, wrapper);
    }
}
