package com.anfioo.howtocook.common.retrieval;

import com.anfioo.howtocook.common.config.RetrievalProperties;
import com.anfioo.howtocook.common.embedding.EmbeddingClient;
import com.anfioo.howtocook.common.enums.doc.RetrievalType;
import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索服务（开发文档 §5.3）：向量 + 关键词两路召回 → Java 内 Min-Max 归一化 →
 * 加权融合（默认 0.7/0.3，仅命中单路的另一路按 0 计）→ 按 chunk_id 去重合并 → Top K。
 * <p>READY 且未删除的过滤在 SQL 层完成；融合公式与方案设计一字不差，不得更改。</p>
 */
@Slf4j
@Component
public class HybridRetrievalService {

    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingClient embeddingClient;
    private final RetrievalProperties props;

    public HybridRetrievalService(DocumentChunkMapper chunkMapper,
                                  EmbeddingClient embeddingClient,
                                  RetrievalProperties props) {
        this.chunkMapper = chunkMapper;
        this.embeddingClient = embeddingClient;
        this.props = props;
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
        return fuse(vectorHits, keywordHits, topK, props.getVectorWeight(), props.getKeywordWeight());
    }

    /**
     * 归一化融合（纯函数，供单测）：
     * ① 每路 Min-Max 归一化（该路仅 1 条时 score'=1）；② finalScore = vw*vector' + kw*keyword'，
     * 仅命中单路的另一路按 0 计；③ 按 chunkId 去重合并、finalScore 降序取 Top K，并标注 retrievalType。
     */
    static List<ChunkResult> fuse(List<ChunkResult> vectorHits, List<ChunkResult> keywordHits,
                                  int topK, double vectorWeight, double keywordWeight) {
        Map<Long, Double> vectorNorm = normalize(vectorHits);
        Map<Long, Double> keywordNorm = normalize(keywordHits);

        // 按 chunkId 去重合并（保留原始行，两路同 chunk 内容一致，任取一路的行）
        Set<Long> mergedIds = new LinkedHashSet<>();
        mergedIds.addAll(vectorNorm.keySet());
        mergedIds.addAll(keywordNorm.keySet());
        Map<Long, ChunkResult> rowsById = new HashMap<>();
        for (ChunkResult row : vectorHits) {
            rowsById.put(row.getChunkId(), row);
        }
        for (ChunkResult row : keywordHits) {
            rowsById.putIfAbsent(row.getChunkId(), row);
        }

        List<ChunkResult> fused = new ArrayList<>(mergedIds.size());
        for (Long chunkId : mergedIds) {
            boolean inVector = vectorNorm.containsKey(chunkId);
            boolean inKeyword = keywordNorm.containsKey(chunkId);
            double v = vectorNorm.getOrDefault(chunkId, 0.0);
            double k = keywordNorm.getOrDefault(chunkId, 0.0);
            ChunkResult row = rowsById.get(chunkId);
            row.setScore(vectorWeight * v + keywordWeight * k);
            // 命中类型按"是否被两路召回"判定（与归一化分数无关，归一化为 0 仍是命中）
            row.setRetrievalType(
                    inVector && inKeyword ? RetrievalType.HYBRID.name()
                            : inVector ? RetrievalType.VECTOR.name()
                            : RetrievalType.KEYWORD.name());
            fused.add(row);
        }
        fused.sort(Comparator.comparingDouble(ChunkResult::getScore).reversed());
        return fused.size() <= topK ? fused : new ArrayList<>(fused.subList(0, topK));
    }

    /** Min-Max 归一化：返回 chunkId → score'；该路仅 1 条时 score'=1；max==min（同分）时全部置 1 */
    private static Map<Long, Double> normalize(List<ChunkResult> hits) {
        Map<Long, Double> result = new HashMap<>();
        if (hits == null || hits.isEmpty()) {
            return result;
        }
        if (hits.size() == 1) {
            result.put(hits.get(0).getChunkId(), 1.0);
            return result;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ChunkResult hit : hits) {
            double s = hit.getScore();
            min = Math.min(min, s);
            max = Math.max(max, s);
        }
        for (ChunkResult hit : hits) {
            result.put(hit.getChunkId(), max == min ? 1.0 : (hit.getScore() - min) / (max - min));
        }
        return result;
    }
}
