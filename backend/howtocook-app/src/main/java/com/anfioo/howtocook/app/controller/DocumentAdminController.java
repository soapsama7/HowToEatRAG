package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.aspect.AuditOperation;
import com.anfioo.howtocook.app.dto.DocumentDetailResponse;
import com.anfioo.howtocook.app.dto.DocumentUpdateRequest;
import com.anfioo.howtocook.app.dto.DocumentUploadResponse;
import com.anfioo.howtocook.app.service.DocumentAdminService;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端文档接口（/api/admin/**，要求 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
public class DocumentAdminController {

    private final DocumentAdminService documentAdminService;

    /** 上传 markdown（仅 .md、≤2MB），立即返回 docId + taskNo */
    @AuditOperation(operation = "UPLOAD_DOCUMENT")
    @PostMapping
    public Result<DocumentUploadResponse> upload(@RequestParam("file") MultipartFile file,
                                                 @RequestParam(value = "docType", required = false) String docType,
                                                 @RequestParam(value = "category", required = false) String category) {
        return Result.ok(documentAdminService.upload(file, docType, category));
    }

    /** 全量列表（分页 + 状态筛选） */
    @GetMapping
    public Result<Page<Document>> list(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "20") long pageSize,
                                       @RequestParam(required = false) String status) {
        return Result.ok(documentAdminService.list(pageNum, pageSize, status));
    }

    /** 详情（RustFS 原文返回） */
    @GetMapping("/{id}")
    public Result<DocumentDetailResponse> detail(@PathVariable long id) {
        return Result.ok(documentAdminService.detail(id));
    }

    /** 删除（移入回收站：逻辑删，chunk 与 RustFS 文件保留，可恢复） */
    @AuditOperation(operation = "DELETE_DOCUMENT")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        documentAdminService.delete(id);
        return Result.ok();
    }

    /** 回收站分页列表（按删除时间倒序） */
    @GetMapping("/recycle-bin")
    public Result<Page<Document>> recycleBin(@RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(documentAdminService.recycleBin(pageNum, pageSize));
    }

    /** 恢复回收站文档（READY 文档恢复后检索立即可见） */
    @AuditOperation(operation = "RESTORE_DOCUMENT")
    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable long id) {
        documentAdminService.restore(id);
        return Result.ok();
    }

    /** 彻底清除（物理删 document/chunks/未完结任务，事务提交后删 RustFS 对象，不可恢复） */
    @AuditOperation(operation = "PURGE_DOCUMENT")
    @PostMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable long id) {
        documentAdminService.purge(id);
        return Result.ok();
    }

    /** 重索引：version+1，重新走索引链路，用于需要更新文档的chunk块以及向量数据 */
    @AuditOperation(operation = "REINDEX_DOCUMENT")
    @PostMapping("/{id}/reindex")
    public Result<String> reindex(@PathVariable long id) {
        return Result.ok(documentAdminService.reindex(id));
    }

    /** 修改文档内容（覆盖写 RustFS 原文）并重索引 */
    @AuditOperation(operation = "UPDATE_DOCUMENT")
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable long id, @Valid @RequestBody DocumentUpdateRequest request) {
        return Result.ok(documentAdminService.updateContent(id, request.getContent(), request.getDocType(), request.getCategory()));
    }
}
