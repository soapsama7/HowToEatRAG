package com.anfioo.howtocook.app.service;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.dto.BatchImportRequest;
import com.anfioo.howtocook.app.dto.BatchImportResponse;
import com.anfioo.howtocook.app.dto.DocumentDetailResponse;
import com.anfioo.howtocook.app.dto.DocumentUploadResponse;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.entity.doc.DocumentChunk;
import com.anfioo.howtocook.common.entity.sys.IndexTask;
import com.anfioo.howtocook.common.enums.doc.DocStatus;
import com.anfioo.howtocook.common.enums.doc.DocType;
import com.anfioo.howtocook.common.enums.sys.IndexTaskType;
import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.mapper.sys.IndexTaskMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.anfioo.howtocook.common.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 管理端文档服务：上传 / 列表 / 详情 / 删除。
 * <p>事务边界约定（开发文档 Review 要点）：RustFS 成功但 DB 失败时允许孤儿对象，仅记录日志不做补偿。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAdminService {

    /** 上传体积上限：2MB */
    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final IndexTaskMapper indexTaskMapper;
    private final StorageService storageService;
    private final com.anfioo.howtocook.app.mq.DocumentIndexProducer documentIndexProducer;

    /**
     * 上传 markdown：校验 → 存 RustFS → 建 document(PENDING) + index_task(PENDING) → 返回 taskNo。
     * <p>消息发送位由 Step 2.3 接上。</p>
     */
    @Transactional
    public DocumentUploadResponse upload(MultipartFile file, String docType, String category) {
        validateUpload(file);
        if (docType != null && !DocType.RECIPE.name().equals(docType)
                && !DocType.TIP.name().equals(docType) && !DocType.OTHER.name().equals(docType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "docType 仅允许 RECIPE/TIP/OTHER");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取上传文件失败");
        }

        // objectKey 服务端生成，文件名不参与路径（防路径穿越）
        String objectKey = StorageService.generateObjectKey();
        storageService.putObject(new ByteArrayInputStream(bytes), objectKey);

        Document document = new Document();
        document.setTitle(file.getOriginalFilename().replaceAll("(?i)\\.md$", ""));
        document.setDocType(docType == null ? DocType.OTHER.name() : docType);
        document.setCategory(category);
        document.setObjectKey(objectKey);
        document.setFileSize((long) bytes.length);
        document.setStatus(DocStatus.PENDING.name());
        document.setUploaderId(StpUtil.getLoginIdAsLong());
        try {
            documentMapper.insert(document);
        } catch (Exception e) {
            // DB 失败：RustFS 孤儿对象允许存在，记录日志不做补偿
            log.error("document 入库失败，RustFS 孤儿对象: objectKey={}", objectKey, e);
            throw e;
        }

        String taskNo = UUID.randomUUID().toString();
        IndexTask task = new IndexTask();
        task.setTaskNo(taskNo);
        task.setDocId(document.getId());
        task.setTaskType(IndexTaskType.INDEX.name());
        task.setStatus(com.anfioo.howtocook.common.enums.sys.IndexTaskStatus.PENDING.name());
        indexTaskMapper.insert(task);

        // Step 2.3：投递索引消息 {taskNo, docId}
        documentIndexProducer.send(taskNo, document.getId());
        return DocumentUploadResponse.builder()
                .docId(document.getId())
                .taskNo(taskNo)
                .build();
    }

    /** 分页列表（状态筛选，逻辑删除自动过滤） */
    public Page<Document> list(long pageNum, long pageSize, String status) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .orderByDesc(Document::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Document::getStatus, status);
        }
        return documentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    /** 详情：元数据 + RustFS 原文全文 */
    public DocumentDetailResponse detail(long id) {
        Document document = requireDocument(id);
        byte[] bytes = storageService.getObject(document.getObjectKey());
        return DocumentDetailResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .docType(document.getDocType())
                .category(document.getCategory())
                .objectKey(document.getObjectKey())
                .fileSize(document.getFileSize())
                .difficulty(document.getDifficulty())
                .cookMinutes(document.getCookMinutes())
                .calories(document.getCalories())
                .version(document.getVersion())
                .status(document.getStatus())
                .errorMsg(document.getErrorMsg())
                .chunkCount(document.getChunkCount())
                .uploaderId(document.getUploaderId())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .content(new String(bytes, StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 删除：逻辑删 document + 物理删该文档全部 chunk（同事务）。
     */
    @Transactional
    public void delete(long id) {
        Document document = requireDocument(id);
        documentMapper.deleteById(id);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocId, id));
        // 索引任务若在途，由索引服务按文档状态判断后跳过（文档已删除，索引结果不可见）
        log.info("文档已删除: id={}, objectKey={}", id, document.getObjectKey());
    }

    /**
     * 重索引（Step 2.5）：version+1（乐观锁）→ 建 REINDEX 任务 → 投递消息。
     * 新 chunk 以新 version 入库，成功后由索引服务删除旧版本 chunk。
     */
    @Transactional
    public String reindex(long id) {
        Document document = requireDocument(id);
        // @Version 拦截器自动 version+1（WHERE version=N → SET version=N+1），不可手动 set
        int updated = documentMapper.updateById(document);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "重索引并发冲突，请重试");
        }
        String taskNo = UUID.randomUUID().toString();
        IndexTask task = new IndexTask();
        task.setTaskNo(taskNo);
        task.setDocId(id);
        task.setTaskType(IndexTaskType.REINDEX.name());
        task.setStatus(com.anfioo.howtocook.common.enums.sys.IndexTaskStatus.PENDING.name());
        indexTaskMapper.insert(task);
        documentIndexProducer.send(taskNo, id);
        return taskNo;
    }

    /**
     * 批量导入（Step 2.5，本地开发用）：遍历 dirPath 下 dishes/**（排除 template）与 tips/**，
     * 逐个走「上传 + 建任务 + 投递」链路，索引异步完成。返回导入汇总。
     */
    public BatchImportResponse batchImport(String dirPath) {
        java.nio.file.Path root = java.nio.file.Path.of(dirPath);
        if (!java.nio.file.Files.isDirectory(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录不存在: " + dirPath);
        }
        java.nio.file.Path dishes = root.resolve("dishes");
        java.nio.file.Path tips = root.resolve("tips");
        if (!java.nio.file.Files.isDirectory(dishes) && !java.nio.file.Files.isDirectory(tips)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目录下未找到 dishes/ 与 tips/ 子目录");
        }

        List<String> failures = new ArrayList<>();
        int total = 0;
        int succeeded = 0;

        // dishes/<分类目录>/*.md（template 排除）
        if (java.nio.file.Files.isDirectory(dishes)) {
            try (var dirs = java.nio.file.Files.list(dishes)) {
                for (java.nio.file.Path categoryDir : dirs.filter(java.nio.file.Files::isDirectory)
                        .filter(d -> com.anfioo.howtocook.common.document.CategoryNames.importable(d.getFileName().toString()))
                        .toList()) {
                    String category = com.anfioo.howtocook.common.document.CategoryNames.NAMES
                            .get(categoryDir.getFileName().toString());
                    try (var files = java.nio.file.Files.list(categoryDir)) {
                        for (java.nio.file.Path file : files
                                .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".md"))
                                .toList()) {
                            total++;
                            if (importOne(file, DocType.RECIPE.name(), category)) {
                                succeeded++;
                            } else {
                                failures.add(file.getFileName() + ": 入库失败（见日志）");
                            }
                        }
                    } catch (Exception e) {
                        log.warn("遍历分类目录失败: {}", categoryDir, e);
                    }
                }
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "遍历 dishes 目录失败: " + e.getMessage());
            }
        }

        // tips/**/*.md（递归）
        if (java.nio.file.Files.isDirectory(tips)) {
            try (var files = java.nio.file.Files.walk(tips)) {
                for (java.nio.file.Path file : files
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".md"))
                        .toList()) {
                    total++;
                    if (importOne(file, DocType.TIP.name(), com.anfioo.howtocook.common.document.CategoryNames.NAMES.get("tips"))) {
                        succeeded++;
                    } else {
                        failures.add(file.getFileName() + ": 入库失败（见日志）");
                    }
                }
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "遍历 tips 目录失败: " + e.getMessage());
            }
        }

        return com.anfioo.howtocook.app.dto.BatchImportResponse.builder()
                .total(total)
                .succeeded(succeeded)
                .failed(failures.size())
                .failures(failures.size() > 50 ? failures.subList(0, 50) : failures)
                .build();
    }

    /** 单文件导入：成功 true / 失败 false（失败仅告警，不中断批量） */
    private boolean importOne(java.nio.file.Path file, String docType, String category) {
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file);
            if (bytes.length > MAX_FILE_SIZE) {
                log.warn("批量导入跳过超限文件: {} ({}B)", file.getFileName(), bytes.length);
                return false;
            }
            String objectKey = StorageService.generateObjectKey();
            storageService.putObject(new ByteArrayInputStream(bytes), objectKey);

            Document document = new Document();
            String filename = file.getFileName().toString().replaceAll("(?i)\\.md$", "");
            document.setTitle(filename);
            document.setDocType(docType);
            document.setCategory(category);
            document.setObjectKey(objectKey);
            document.setFileSize((long) bytes.length);
            document.setStatus(DocStatus.PENDING.name());
            document.setUploaderId(StpUtil.getLoginIdAsLong());
            try {
                documentMapper.insert(document);
            } catch (Exception e) {
                log.error("批量导入 document 入库失败，RustFS 孤儿对象: {}", objectKey, e);
                throw e;
            }

            String taskNo = UUID.randomUUID().toString();
            IndexTask task = new IndexTask();
            task.setTaskNo(taskNo);
            task.setDocId(document.getId());
            task.setTaskType(IndexTaskType.INDEX.name());
            task.setStatus(com.anfioo.howtocook.common.enums.sys.IndexTaskStatus.PENDING.name());
            indexTaskMapper.insert(task);
            documentIndexProducer.send(taskNo, document.getId());
            return true;
        } catch (Exception e) {
            log.warn("批量导入单文件失败: {}, error={}", file.getFileName(), e.getMessage());
            return false;
        }
    }

    /** 校验上传文件：仅 .md、≤2MB、非空 */
    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".md")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅允许上传 .md 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件超过 2MB 上限");
        }
    }

    private Document requireDocument(long id) {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "文档不存在");
        }
        return document;
    }
}
