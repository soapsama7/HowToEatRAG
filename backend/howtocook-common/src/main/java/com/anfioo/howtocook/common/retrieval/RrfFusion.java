package com.anfioo.howtocook.common.retrieval;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion，倒数排名融合）——默认融合策略（优化 2.7）。
 * <p>{@code score(d) = Σ 1/(k + rank_d)}，k 默认 60，只看排名不看分数，
 * 天然免疫余弦相似度与 ts_rank 之间的量纲差异与异常分数。业界事实标准
 * （Elasticsearch / Weaviate / Azure AI Search 均采用）。</p>
 */
public class RrfFusion implements FusionStrategy {

    /** RRF 平滑常数 k */
    private static final double K = 60.0;

    @Override
    public List<ChunkResult> fuse(List<ChunkResult> vectorHits, List<ChunkResult> keywordHits, int topK) {
        Map<Long, Double> scores = new HashMap<>();
        accumulate(vectorHits, scores);
        accumulate(keywordHits, scores);
        return FusionStrategy.merge(vectorHits, keywordHits, scores, topK);
    }

    /** 对该路召回按原始分降序排名，累加 1/(k + rank)（rank 从 1 开始） */
    private void accumulate(List<ChunkResult> hits, Map<Long, Double> scores) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        List<ChunkResult> ranked = hits.stream()
                .sorted(Comparator.comparingDouble(ChunkResult::getScore).reversed())
                .toList();
        for (int i = 0; i < ranked.size(); i++) {
            long id = ranked.get(i).getChunkId();
            scores.merge(id, 1.0 / (K + i + 1), Double::sum);
        }
    }
}
