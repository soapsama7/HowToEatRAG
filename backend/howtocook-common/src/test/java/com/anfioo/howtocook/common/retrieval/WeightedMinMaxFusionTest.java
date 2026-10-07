package com.anfioo.howtocook.common.retrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Min-Max 归一化加权融合单测（纯函数，不依赖 DB；优化 2.7 保留作 A/B 对照）：
 * 覆盖双路命中 / 单路命中 / 单条归一化 / 同分边界 / TopK 截断。
 */
class WeightedMinMaxFusionTest {

    private static final FusionStrategy STRATEGY = new WeightedMinMaxFusion(0.7, 0.3);

    private static ChunkResult hit(long chunkId, double score) {
        ChunkResult r = new ChunkResult();
        r.setChunkId(chunkId);
        r.setDocId(100L + chunkId);
        r.setTitle("菜谱-" + chunkId);
        r.setSection("操作");
        r.setContent("内容-" + chunkId);
        r.setScore(score);
        return r;
    }

    @Test
    void 双路命中标HYBRID_单路命中标单路类型_按融合分降序() {
        List<ChunkResult> fused = STRATEGY.fuse(
                new ArrayList<>(List.of(hit(1, 0.9), hit(3, 0.5))),
                new ArrayList<>(List.of(hit(2, 0.8), hit(3, 0.4))),
                5);

        assertEquals(3, fused.size());
        assertEquals(1L, fused.get(0).getChunkId());
        assertEquals(0.7, fused.get(0).getScore(), 1e-9);
        assertEquals("VECTOR", fused.get(0).getRetrievalType());

        assertEquals(2L, fused.get(1).getChunkId());
        assertEquals(0.3, fused.get(1).getScore(), 1e-9);
        assertEquals("KEYWORD", fused.get(1).getRetrievalType());

        assertEquals(3L, fused.get(2).getChunkId());
        assertEquals(0.0, fused.get(2).getScore(), 1e-9);
        assertEquals("HYBRID", fused.get(2).getRetrievalType());
    }

    @Test
    void 仅一路命中时另一路按0计() {
        List<ChunkResult> fused = STRATEGY.fuse(
                new ArrayList<>(List.of(hit(1, 0.6))),
                new ArrayList<>(), 5);

        assertEquals(1, fused.size());
        assertEquals(0.7, fused.get(0).getScore(), 1e-9);
        assertEquals("VECTOR", fused.get(0).getRetrievalType());
    }

    @Test
    void 双路均空返回空列表() {
        assertTrue(STRATEGY.fuse(new ArrayList<>(), new ArrayList<>(), 5).isEmpty());
    }

    @Test
    void 同分边界_归一化置1避免除零() {
        List<ChunkResult> fused = STRATEGY.fuse(
                new ArrayList<>(List.of(hit(1, 0.5), hit(2, 0.5))),
                new ArrayList<>(), 5);

        assertEquals(0.7, fused.get(0).getScore(), 1e-9);
        assertEquals(0.7, fused.get(1).getScore(), 1e-9);
    }

    @Test
    void topK截断_保留高分前K条() {
        List<ChunkResult> fused = STRATEGY.fuse(
                new ArrayList<>(List.of(hit(1, 0.9), hit(2, 0.8), hit(3, 0.1))),
                new ArrayList<>(), 2);

        assertEquals(2, fused.size());
        assertEquals(1L, fused.get(0).getChunkId());
        assertEquals(2L, fused.get(1).getChunkId());
    }
}
