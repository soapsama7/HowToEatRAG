package com.anfioo.howtocook.app.schedule;

import com.anfioo.howtocook.app.service.DocumentAdminService;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回收站定时清理任务（Review 修订 R2）：
 * 每日按 cron 扫描「逻辑删除超过保留天数」的文档，逐个走 purge（物理删 chunks/document/未完结任务，
 * 事务提交后删 RustFS 对象）。单个文档清除失败仅告警跳过，不影响本轮其余文档（每文档独立事务）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleBinCleanupJob {

    private final DocumentMapper documentMapper;
    private final DocumentAdminService documentAdminService;

    /** 回收站保留天数（超过即清除） */
    @Value("${howtocook.recycle-bin.retention-days:30}")
    private long retentionDays;

    /** 每日 03:00 执行（可用 howtocook.recycle-bin.purge-cron 覆盖） */
    @Scheduled(cron = "${howtocook.recycle-bin.purge-cron:0 0 3 * * ?}")
    public void purgeExpired() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        List<Document> expired = documentMapper.selectExpired(threshold);
        if (expired.isEmpty()) {
            return;
        }
        log.info("回收站定时清理开始: 过期文档 {} 个（删除时间早于 {}，保留期 {} 天）",
                expired.size(), threshold, retentionDays);
        int succeeded = 0;
        for (Document document : expired) {
            try {
                documentAdminService.purge(document.getId());
                succeeded++;
            } catch (Exception e) {
                log.warn("回收站过期文档清除失败，跳过: id={}, error={}", document.getId(), e.getMessage());
            }
        }
        log.info("回收站定时清理完成: 成功 {}/{}", succeeded, expired.size());
    }
}
