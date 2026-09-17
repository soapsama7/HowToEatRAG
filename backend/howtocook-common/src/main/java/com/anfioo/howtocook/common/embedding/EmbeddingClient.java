package com.anfioo.howtocook.common.embedding;

import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化客户端封装（开发文档 §2 common/embedding）：
 * 批量（每批 ≤ 16 条）+ 失败整批重试 1 次 + 全局批次限速（防免费额度被打爆）。
 */
@Slf4j
@Component
public class EmbeddingClient {

    /** 每批最大条数 */
    public static final int BATCH_SIZE = 16;

    private final EmbeddingModel embeddingModel;
    private final long batchIntervalMs;

    /** 上一批次发起时间（全局限速用） */
    private long lastBatchTime = 0L;

    public EmbeddingClient(EmbeddingModel embeddingModel,
                           @Value("${howtocook.index.embedding-batch-interval-ms:1000}") long batchIntervalMs) {
        this.embeddingModel = embeddingModel;
        this.batchIntervalMs = batchIntervalMs;
    }

    /**
     * 批量向量化：返回与入参顺序一致的向量列表。
     * <p>失败语义：整批重试 1 次后仍失败 → 抛 BusinessException(SERVICE_UNAVAILABLE)，
     * 由索引服务标记文档 FAILED。</p>
     */
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()));
            vectors.addAll(embedBatchWithRetry(batch));
            boolean hasMore = start + BATCH_SIZE < texts.size();
            if (hasMore) {
                throttle();
            }
        }
        return vectors;
    }

    private List<float[]> embedBatchWithRetry(List<String> batch) {
        try {
            return embeddingModel.embed(batch);
        } catch (Exception first) {
            log.warn("embedding 批次失败，整批重试一次: {}", first.getMessage());
            try {
                return embeddingModel.embed(batch);
            } catch (Exception second) {
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "向量化服务调用失败: " + second.getMessage());
            }
        }
    }

    /** 全局批次限速：保证相邻两批发起间隔 ≥ batchIntervalMs（多消费线程并发时同样生效） */
    private synchronized void throttle() {
        if (batchIntervalMs <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long wait = lastBatchTime + batchIntervalMs - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "向量化被中断");
            }
        }
        lastBatchTime = System.currentTimeMillis();
    }
}
