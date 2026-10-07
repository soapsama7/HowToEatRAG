package com.anfioo.howtocook.common.retrieval;

import com.anfioo.howtocook.common.enums.doc.RetrievalType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 混合检索融合策略（优化 2.7）：两路召回 → 按策略计算融合分 → 合并去重 → TopK。
 * <p>实现：{@link RrfFusion}（默认，RRF 排名融合，量纲无关）、
 * {@link WeightedMinMaxFusion}（旧版 Min-Max 归一化加权，留作对照）。</p>
 */
public interface FusionStrategy {

    /** 融合入口：返回按融合分降序、去重后的 TopK 结果 */
    List<ChunkResult> fuse(List<ChunkResult> vectorHits, List<ChunkResult> keywordHits, int topK);

    /**
     * 共享合并逻辑（纯函数）：按 chunkId 去重、写入融合分与命中类型（HYBRID/VECTOR/KEYWORD）、
     * 按融合分降序取 TopK。命中类型按「是否被两路召回」判定，与具体融合分数无关。
     */
    static List<ChunkResult> merge(List<ChunkResult> vectorHits, List<ChunkResult> keywordHits,
                                   Map<Long, Double> scores, int topK) {
        Set<Long> inVector = new HashSet<>();
        Map<Long, ChunkResult> rowsById = new HashMap<>();
        for (ChunkResult row : vectorHits) {
            inVector.add(row.getChunkId());
            rowsById.put(row.getChunkId(), row);
        }
        Set<Long> inKeyword = new HashSet<>();
        for (ChunkResult row : keywordHits) {
            inKeyword.add(row.getChunkId());
            rowsById.putIfAbsent(row.getChunkId(), row);
        }

        List<ChunkResult> fused = new ArrayList<>(scores.size());
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            Long id = e.getKey();
            ChunkResult row = rowsById.get(id);
            row.setScore(e.getValue());
            boolean inV = inVector.contains(id);
            boolean inK = inKeyword.contains(id);
            row.setRetrievalType(inV && inK ? RetrievalType.HYBRID.name()
                    : inV ? RetrievalType.VECTOR.name()
                    : RetrievalType.KEYWORD.name());
            fused.add(row);
        }
        fused.sort(Comparator.comparingDouble(ChunkResult::getScore).reversed());
        return fused.size() <= topK ? fused : new ArrayList<>(fused.subList(0, topK));
    }
}
