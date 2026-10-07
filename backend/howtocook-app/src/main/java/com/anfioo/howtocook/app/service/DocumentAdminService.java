package com.anfioo.howtocook.app.service;

import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.app.dto.DocumentDetailResponse;
import com.anfioo.howtocook.app.dto.DocumentUploadResponse;
import com.anfioo.howtocook.app.mq.DocumentIndexProducer;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.entity.doc.DocumentChunk;
import com.anfioo.howtocook.common.entity.sys.IndexTask;
import com.anfioo.howtocook.common.enums.doc.DocStatus;
import com.anfioo.howtocook.common.enums.doc.DocType;
import com.anfioo.howtocook.common.enums.sys.IndexTaskStatus;
import com.anfioo.howtocook.common.enums.sys.IndexTaskType;
import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.mapper.sys.IndexTaskMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.anfioo.howtocook.common.storage.StorageService;
import com.anfioo.howtocook.common.util.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 管理端文档服务：上传 / 列表 / 详情 / 回收站（删除/恢复/彻底清除）。
 * <p>事务边界约定（开发文档 Review 要点）：RustFS 与 DB 无分布式事务，
 * DB 失败产生的 RustFS 孤儿对象仅记录日志，不做补偿（purge 的对象删除放事务提交后，同约定）。</p>
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
    private final DocumentIndexProducer documentIndexProducer;

    /**
     * 上传 markdown：校验 → SHA-256 查重 → 存 RustFS → 建 document(PENDING) + index_task(PENDING) → 返回 taskNo。
     * <p>查重范围：所有未物理删除条目（含回收站，R3 返工）——否则「删除→重传→恢复」会产生重复文档。
     * 彻底清除（purge）后该内容才允许重新上传。</p>
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

        // 内容哈希查重（R3 返工）：手写 SQL 绕过 @TableLogic，回收站文档同样拦截
        String contentHash = DigestUtil.sha256Hex(bytes);
        Document duplicate = documentMapper.selectByContentHash(contentHash);
        if (duplicate != null) {
            String hint = duplicate.getDeleted() != null && duplicate.getDeleted() == 1
                    ? "（该文档当前在回收站中，可恢复使用或彻底清除后再上传）"
                    : "，请勿重复上传";
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "文件内容已存在：docId=" + duplicate.getId() + "（" + duplicate.getTitle() + "）" + hint);
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
        document.setContentHash(contentHash);
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
        task.setStatus(IndexTaskStatus.PENDING.name());
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
     * 删除（R2 改造）：移入回收站——仅逻辑删 document（记录 deleted_at），chunk 与 RustFS 文件保留，
     * 保留期内可恢复。检索不受影响（召回 SQL 自带 d.deleted = 0 过滤）；
     * 在途索引任务由消费端按「文档已删除」取消。
     */
    public void delete(long id) {
        Document document = requireDocument(id);
        int updated = documentMapper.moveToRecycleBin(id);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "删除失败，请重试");
        }
        log.info("文档已移入回收站: id={}, objectKey={}", id, document.getObjectKey());
    }

    /** 回收站分页列表（按删除时间倒序） */
    public Page<Document> recycleBin(long pageNum, long pageSize) {
        return documentMapper.selectRecycleBin(new Page<>(pageNum, pageSize));
    }

    /**
     * 恢复：反逻辑删（deleted=0 + 清空 deleted_at）。
     * chunk 保留未删，READY 文档恢复后检索立即可见；PENDING/FAILED 文档可手动 reindex。
     */
    public void restore(long id) {
        int updated = documentMapper.restoreById(id);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回收站中不存在该文档");
        }
        log.info("文档已从回收站恢复: id={}", id);
    }

    /**
     * 彻底清除（回收站 → 不可恢复）：同事务物理删 chunks + document + 未完结索引任务；
     * RustFS 对象删除放事务提交后执行，失败仅告警不做补偿（孤儿对象，同上传侧约定）。
     */
    @Transactional
    public void purge(long id) {
        Document document = documentMapper.selectDeletedById(id);
        if (document == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "回收站中不存在该文档");
        }
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getDocId, id));
        // 未完结（PENDING/PROCESSING）任务一并清理，避免任务列表残留死任务；
        // 已发出的在途消息由消费端「任务不存在/文档已删除」分支静默终结
        indexTaskMapper.delete(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getDocId, id)
                .in(IndexTask::getStatus, IndexTaskStatus.PENDING.name(), IndexTaskStatus.PROCESSING.name()));
        int deleted = documentMapper.purgeById(id);
        if (deleted == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "清除失败，请重试");
        }

        // 对象删除放在 DB 事务提交之后：事务回滚时对象仍在，恢复一致
        String objectKey = document.getObjectKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteObject(objectKey);
                    log.info("回收站清除完成，RustFS 对象已删除: docId={}, objectKey={}", id, objectKey);
                } catch (Exception e) {
                    log.error("RustFS 对象删除失败（孤儿对象，需人工清理）: docId={}, objectKey={}", id, objectKey, e);
                }
            }
        });
        log.info("文档已从回收站彻底清除: id={}, objectKey={}", id, objectKey);
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
        return createReindexTask(id);
    }

    /**
     * 修改文档内容并重索引（用户新增需求）：
     * 校验内容与查重 → 覆盖写 RustFS 原文（objectKey 不变，重索引即读新内容）→ 更新元数据
     * → version+1 → 建 REINDEX 任务投递消息。
     * <p>标题/难度/卡路里等由重索引时解析器按新内容回填（与上传侧行为一致）。</p>
     */
    @Transactional
    public String updateContent(long id, String content, String docType, String category) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文档内容不能为空");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容超过 2MB 上限");
        }
        if (docType != null && !docType.isBlank()
                && !DocType.RECIPE.name().equals(docType)
                && !DocType.TIP.name().equals(docType) && !DocType.OTHER.name().equals(docType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "docType 仅允许 RECIPE/TIP/OTHER");
        }

        Document document = requireDocument(id);
        String newHash = DigestUtil.sha256Hex(bytes);
        Document duplicate = documentMapper.selectByContentHash(newHash);
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "内容与其他文档重复：docId=" + duplicate.getId() + "（" + duplicate.getTitle() + "）");
        }

        // 覆盖写 RustFS（沿用上传侧约定：DB 失败产生的内容不一致由下次重索引/更新自愈，不做补偿）
        storageService.putObject(new ByteArrayInputStream(bytes), document.getObjectKey());

        document.setContentHash(newHash);
        document.setFileSize((long) bytes.length);
        if (docType != null && !docType.isBlank()) {
            document.setDocType(docType);
        }
        if (category != null && !category.isBlank()) {
            document.setCategory(category);
        }
        document.setStatus(DocStatus.PENDING.name());
        document.setErrorMsg(null);
        // @Version 拦截器自动 version+1
        int updated = documentMapper.updateById(document);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "更新并发冲突，请重试");
        }
        log.info("文档内容已更新，等待重索引: id={}, objectKey={}", id, document.getObjectKey());
        return createReindexTask(id);
    }

    /** 建 REINDEX 任务并投递索引消息（reindex / updateContent 共用） */
    private String createReindexTask(long docId) {
        String taskNo = UUID.randomUUID().toString();
        IndexTask task = new IndexTask();
        task.setTaskNo(taskNo);
        task.setDocId(docId);
        task.setTaskType(IndexTaskType.REINDEX.name());
        task.setStatus(IndexTaskStatus.PENDING.name());
        indexTaskMapper.insert(task);
        documentIndexProducer.send(taskNo, docId);
        return taskNo;
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
