package com.anfioo.howtocook.app.schedule;

import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿 chunk 兜底清理任务：
 * purge 物理删 document 时，若恰有在途索引任务正在写 chunk（向量化是最大耗时窗口），
 * 会留下 doc_id 无对应 document 行的孤儿 chunk。这些 chunk 不会被召回
 * （召回 SQL 自带 JOIN document 过滤），但会占用空间、干扰 chunk 计数，故定时清除。
 * <p>放在回收站清理（03:00）之后执行，确保 purge 产生的孤儿已稳定。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanChunkCleanupJob {

    private final DocumentChunkMapper documentChunkMapper;

    /** 单批删除上限，避免一次 DELETE 锁表过久 */
    private static final int BATCH_SIZE = 1000;

    /** 单轮最大批数，防止异常场景下死循环 */
    private static final int MAX_BATCHES = 50;

    /** 每日 03:30 执行（可用 howtocook.chunk.orphan-cleanup-cron 覆盖） */
    @Scheduled(cron = "${howtocook.chunk.orphan-cleanup-cron:0 30 3 * * ?}")
    public void cleanOrphans() {
        int total = 0;
        int batches = 0;
        int deleted = 0;
        while (batches < MAX_BATCHES) {
            deleted = documentChunkMapper.deleteOrphans(BATCH_SIZE);
            if (deleted == 0) {
                break;
            }
            total += deleted;
            batches++;
        }
        if (deleted > 0) {
            // 达到单轮批数上限仍未删尽，下轮继续
            log.warn("孤儿 chunk 本轮未删尽（已达 {} 批上限，本轮删除 {} 条），下轮继续", MAX_BATCHES, total);
        } else if (total > 0) {
            log.info("孤儿 chunk 清理完成: 删除 {} 条（{} 批）", total, batches);
        }
    }
}
