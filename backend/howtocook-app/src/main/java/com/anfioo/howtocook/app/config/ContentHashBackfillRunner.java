package com.anfioo.howtocook.app.config;

import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.storage.StorageService;
import com.anfioo.howtocook.common.util.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * content_hash 存量回填任务（Review 修订 R3）：
 * V4 迁移新增的 content_hash 列对存量文档为 NULL，启动时从 RustFS 拉取原文计算 SHA-256 回填。
 * 单文档失败仅告警跳过（留 NULL），下次启动自动重试；全部回填完成后每次启动零开销（空查询直接返回）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentHashBackfillRunner implements ApplicationRunner {

    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    @Override
    public void run(ApplicationArguments args) {
        // @TableLogic 自动过滤已删文档，只回填未删除文档；回收站文档恢复后如仍缺 hash，下次启动会补上
        List<Document> missing = documentMapper.selectList(
                new LambdaQueryWrapper<Document>()
                        .isNull(Document::getContentHash));
        if (missing.isEmpty()) {
            return;
        }
        log.info("content_hash 存量回填开始: 待回填 {} 个文档", missing.size());
        int succeeded = 0;
        int failed = 0;
        for (Document document : missing) {
            try {
                byte[] bytes = storageService.getObject(document.getObjectKey());
                documentMapper.update(null, new LambdaUpdateWrapper<Document>()
                        .eq(Document::getId, document.getId())
                        .set(Document::getContentHash, DigestUtil.sha256Hex(bytes)));
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("content_hash 回填失败（下次启动重试）: id={}, objectKey={}, error={}",
                        document.getId(), document.getObjectKey(), e.getMessage());
            }
        }
        log.info("content_hash 存量回填完成: 成功 {}，失败 {}", succeeded, failed);
    }
}
