package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.aspect.AuditOperation;
import com.anfioo.howtocook.app.dto.DocumentDetailResponse;
import com.anfioo.howtocook.app.dto.DocumentUploadResponse;
import com.anfioo.howtocook.app.service.DocumentAdminService;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    /** 删除（逻辑删 + 物理删 chunk） */
    @AuditOperation(operation = "DELETE_DOCUMENT")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable long id) {
        documentAdminService.delete(id);
        return Result.ok();
    }

    /** 重索引：version+1，重新走索引链路 */
    @AuditOperation(operation = "REINDEX_DOCUMENT")
    @PostMapping("/{id}/reindex")
    public Result<String> reindex(@PathVariable long id) {
        return Result.ok(documentAdminService.reindex(id));
    }

    /** 批量导入（本地开发）：body 传本机目录，遍历 dishes/**（排除 template）与 tips/** */
    @AuditOperation(operation = "BATCH_IMPORT_DOCUMENTS")
    @PostMapping("/batch-import")
    public Result<com.anfioo.howtocook.app.dto.BatchImportResponse> batchImport(
            @RequestBody com.anfioo.howtocook.app.dto.BatchImportRequest request) {
        return Result.ok(documentAdminService.batchImport(request.getDirPath()));
    }
}
