package com.anfioo.howtocook.app.mq;

import com.anfioo.howtocook.app.service.DocumentIndexService;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.entity.sys.IndexTask;
import com.anfioo.howtocook.common.enums.doc.DocStatus;
import com.anfioo.howtocook.common.enums.sys.IndexTaskStatus;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.mapper.sys.IndexTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 文档索引消息消费者：task_no Redis 锁幂等 → 状态机 PENDING→PROCESSING→SUCCESS/FAILED。
 * <p>重试策略：失败且 retryCount &lt; 3 时回 PENDING 并抛异常触发重投；达到上限置 FAILED 终结。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = DocumentIndexProducer.TOPIC_DOC_INDEX,
        consumerGroup = "howtocook-index-consumer")
public class DocumentIndexConsumer implements RocketMQListener<DocumentIndexMessage> {

    private static final int MAX_RETRY = 3;
    private static final String LOCK_KEY_PREFIX = "index:task:lock:";

    private final IndexTaskMapper indexTaskMapper;
    private final DocumentMapper documentMapper;
    private final DocumentIndexService documentIndexService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void onMessage(DocumentIndexMessage message) {
        String lockKey = LOCK_KEY_PREFIX + message.getTaskNo();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(120));
        if (!Boolean.TRUE.equals(locked)) {
            log.info("重复消费（幂等锁生效），跳过: taskNo={}", message.getTaskNo());
            return;
        }
        try {
            doConsume(message);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void doConsume(DocumentIndexMessage message) {
        IndexTask task = indexTaskMapper.selectOne(new LambdaQueryWrapper<IndexTask>()
                .eq(IndexTask::getTaskNo, message.getTaskNo()));
        if (task == null) {
            log.warn("索引任务不存在，跳过: taskNo={}", message.getTaskNo());
            return;
        }
        if (IndexTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            log.info("任务已完成，跳过: taskNo={}", task.getTaskNo());
            return;
        }

        task.setStatus(IndexTaskStatus.PROCESSING.name());
        indexTaskMapper.updateById(task);
        log.info("索引任务开始: taskNo={}, docId={}, retryCount={}", task.getTaskNo(), task.getDocId(), task.getRetryCount());

        try {
            documentIndexService.index(task.getTaskNo(), task.getDocId());
            task.setStatus(IndexTaskStatus.SUCCESS.name());
            indexTaskMapper.updateById(task);
            log.info("索引任务成功: taskNo={}", task.getTaskNo());
        } catch (Exception e) {
            // 文档已删除：任务直接终结（删除接口约定：在途任务由消费端判断后跳过，不重试）
            Document document = documentMapper.selectById(task.getDocId());
            if (document == null || document.getDeleted() != null && document.getDeleted() == 1) {
                task.setStatus(IndexTaskStatus.FAILED.name());
                task.setErrorMsg("文档已删除，索引任务取消");
                indexTaskMapper.updateById(task);
                log.info("文档已删除，索引任务取消: taskNo={}, docId={}", task.getTaskNo(), task.getDocId());
                return;
            }
            int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
            if (retryCount >= MAX_RETRY) {
                // 达到重试上限：任务与文档双双 FAILED，终结消息（不再重投）
                task.setStatus(IndexTaskStatus.FAILED.name());
                task.setErrorMsg(truncate(e.getMessage()));
                indexTaskMapper.updateById(task);
                markDocumentFailed(task.getDocId(), e.getMessage());
                log.error("索引任务达到重试上限，置 FAILED: taskNo={}, error={}", task.getTaskNo(), e.getMessage());
            } else {
                task.setRetryCount(retryCount + 1);
                task.setStatus(IndexTaskStatus.PENDING.name());
                indexTaskMapper.updateById(task);
                log.warn("索引任务失败，待重投: taskNo={}, retryCount={}, error={}",
                        task.getTaskNo(), retryCount + 1, e.getMessage());
                throw new IllegalStateException("索引失败待重试: " + task.getTaskNo(), e);
            }
        }
    }

    /** 文档存在时置 FAILED + error_msg（文档不存在等场景静默跳过） */
    private void markDocumentFailed(long docId, String errorMsg) {
        Document document = documentMapper.selectById(docId);
        if (document != null) {
            document.setStatus(DocStatus.FAILED.name());
            document.setErrorMsg(truncate(errorMsg));
            documentMapper.updateById(document);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 900 ? message : message.substring(0, 900);
    }
}
