package com.anfioo.howtocook.common.retrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RRF 排名融合单测（纯函数，不依赖 DB；优化 2.7 默认策略）：
 * 验证只看排名、与量纲无关，以及命中类型判定 / TopK 截断。
 */
class RrfFusionTest {

    private static final FusionStrategy STRATEGY = new RrfFusion();

    private static ChunkResult hit(long chunkId, double score) {
        ChunkResult r = new ChunkResult();
        r.setChunkId(chunkId);
        r.setDocId(100L + chunkId);
        r.setTitle("菜谱-" + chunkId);
        r.setContent("内容-" + chunkId);
        r.setScore(score);
        return r;
    }

    @Test
    void 按排名融合_双路命中排前_命中类型正确() {
        // 向量：A(0.9) rank1, B(0.8) rank2, C(0.1) rank3
        // 关键词：B(0.7) rank1, D(0.6) rank2
        // RRF: B = 1/62 + 1/61 ≈ 0.0325(最高) > A = 1/61 > D = 1/62 > C = 1/63
        List<ChunkResult> fused = STRATEGY.fuse(
                new ArrayList<>(List.of(hit(1, 0.9), hit(2, 0.8), hit(3, 0.1))),
                new ArrayList<>(List.of(hit(2, 0.7), hit(4, 0.6))),
                10);

        assertEquals(4, fused.size());

        // B 双路命中排第一，HYBRID
        assertEquals(2L, fused.get(0).getChunkId());
        assertEquals(1.0 / 62 + 1.0 / 61, fused.get(0).getScore(), 1e-9);
        assertEquals("HYBRID", fused.get(0).getRetrievalType());

        // A 仅向量
        assertEquals(1L, fused.get(1).getChunkId());
        assertEquals(1.0 / 61, fused.get(1).getScore(), 1e-9);
        assertEquals("VECTOR", fused.get(1).getRetrievalType());

        // D 仅关键词
        assertEquals(4L, fused.get(2).getChunkId());
        assertEquals("KEYWORD", fused.get(2).getRetrievalType());

        // C 仅向量，排名最末
        assertEquals(3L, fused.get(3).getChunkId());
        assertEquals("VECTOR", fused.get(3).getRetrievalType());
    }

    @Test
    void 双路均空返回空列表() {
        assertTrue(STRATEGY.fuse(new ArrayList<>(), new ArrayList<>(), 5).isEmpty());
    }

    @Test
    void topK截断() {
        List<ChunkResult> fused = STRATEGY.fuse(
                new ArrayList<>(List.of(hit(1, 0.9), hit(2, 0.8), hit(3, 0.1))),
                new ArrayList<>(), 2);

        assertEquals(2, fused.size());
        assertEquals(1L, fused.get(0).getChunkId());
        assertEquals(2L, fused.get(1).getChunkId());
    }
}
