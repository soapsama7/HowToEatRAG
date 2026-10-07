package com.anfioo.howtocook.common.retrieval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Min-Max 归一化加权融合（旧版，留作 A/B 对照）：
 * 每路独立 Min-Max 归一化后 {@code final = vectorWeight * v' + keywordWeight * k'}，
 * 仅命中单路的另一路按 0 计。
 */
public class WeightedMinMaxFusion implements FusionStrategy {

    private final double vectorWeight;
    private final double keywordWeight;

    public WeightedMinMaxFusion(double vectorWeight, double keywordWeight) {
        this.vectorWeight = vectorWeight;
        this.keywordWeight = keywordWeight;
    }

    @Override
    public List<ChunkResult> fuse(List<ChunkResult> vectorHits, List<ChunkResult> keywordHits, int topK) {
        Map<Long, Double> vectorNorm = normalize(vectorHits);
        Map<Long, Double> keywordNorm = normalize(keywordHits);

        Set<Long> ids = new HashSet<>(vectorNorm.keySet());
        ids.addAll(keywordNorm.keySet());
        Map<Long, Double> scores = new HashMap<>(ids.size());
        for (Long id : ids) {
            double v = vectorNorm.getOrDefault(id, 0.0);
            double k = keywordNorm.getOrDefault(id, 0.0);
            scores.put(id, vectorWeight * v + keywordWeight * k);
        }
        return FusionStrategy.merge(vectorHits, keywordHits, scores, topK);
    }

    /** Min-Max 归一化：该路仅 1 条时置 1；max==min（同分）时全部置 1 */
    private Map<Long, Double> normalize(List<ChunkResult> hits) {
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
            min = Math.min(min, hit.getScore());
            max = Math.max(max, hit.getScore());
        }
        for (ChunkResult hit : hits) {
            result.put(hit.getChunkId(), max == min ? 1.0 : (hit.getScore() - min) / (max - min));
        }
        return result;
    }
}
