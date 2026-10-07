package com.anfioo.howtocook.common.retrieval;

import com.anfioo.howtocook.common.config.RetrievalProperties;
import com.anfioo.howtocook.common.embedding.EmbeddingClient;
import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 混合检索服务（开发文档 §5.3）：向量 + 关键词两路召回 → 融合策略 → Top K。
 * <p>READY 且未删除的过滤在 SQL 层完成；融合策略可插拔（优化 2.7）：
 * 默认 {@link RrfFusion}（RRF 排名融合），配置 {@code howtocook.retrieval.fusion-strategy=weighted}
 * 可切回旧版 {@link WeightedMinMaxFusion}。</p>
 */
@Slf4j
@Component
public class HybridRetrievalService {

    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingClient embeddingClient;
    private final RetrievalProperties props;
    private final FusionStrategy fusionStrategy;

    public HybridRetrievalService(DocumentChunkMapper chunkMapper,
                                  EmbeddingClient embeddingClient,
                                  RetrievalProperties props) {
        this.chunkMapper = chunkMapper;
        this.embeddingClient = embeddingClient;
        this.props = props;
        this.fusionStrategy = resolveStrategy(props);
    }

    private static FusionStrategy resolveStrategy(RetrievalProperties props) {
        if ("weighted".equalsIgnoreCase(props.getFusionStrategy())) {
            return new WeightedMinMaxFusion(props.getVectorWeight(), props.getKeywordWeight());
        }
        return new RrfFusion();
    }

    /** 按默认 Top K 检索 */
    public List<ChunkResult> retrieve(String query) {
        return retrieve(query, props.getTopK());
    }

    /** 检索入口：查询向量化 → 两路召回 → 融合取 Top K */
    public List<ChunkResult> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryVector = embeddingClient.embedAll(List.of(query)).get(0);
        List<ChunkResult> vectorHits = chunkMapper.vectorRecall(new PGvector(queryVector), props.getRecallSize());
        List<ChunkResult> keywordHits = chunkMapper.keywordRecall(query, props.getRecallSize());
        log.debug("检索召回: query={}, vector={}, keyword={}", query, vectorHits.size(), keywordHits.size());
        return fusionStrategy.fuse(vectorHits, keywordHits, topK);
    }
}
