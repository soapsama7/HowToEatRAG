package com.anfioo.howtocook.common.retrieval;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 混合检索融合逻辑单测（纯函数，不依赖 DB）：
 * 覆盖双路命中 / 单路命中 / 单条归一化 / 同分边界 / TopK 截断（开发文档 §5.3 验收）。
 */
class HybridRetrievalServiceFuseTest {

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
        // 向量路：A(0.9) C(0.5)；关键词路：B(0.8) C(0.4)
        // 向量归一化：A=1, C=0；关键词归一化：B=1, C=0
        // final: A=0.7, B=0.3, C=0（两路都是 min → 0）
        List<ChunkResult> fused = HybridRetrievalService.fuse(
                new ArrayList<>(List.of(hit(1, 0.9), hit(3, 0.5))),
                new ArrayList<>(List.of(hit(2, 0.8), hit(3, 0.4))),
                5, 0.7, 0.3);

        assertEquals(3, fused.size());
        assertEquals(1L, fused.get(0).getChunkId());
        assertEquals(0.7, fused.get(0).getScore(), 1e-9);
        assertEquals("VECTOR", fused.get(0).getRetrievalType());

        assertEquals(2L, fused.get(1).getChunkId());
        assertEquals(0.3, fused.get(1).getScore(), 1e-9);
        assertEquals("KEYWORD", fused.get(1).getRetrievalType());

        // C 双路命中（两路都归一化为 0）→ HYBRID，final=0
        assertEquals(3L, fused.get(2).getChunkId());
        assertEquals(0.0, fused.get(2).getScore(), 1e-9);
        assertEquals("HYBRID", fused.get(2).getRetrievalType());
    }

    @Test
    void 仅一路命中时另一路按0计() {
        // 关键词路为空：向量 A(0.6) 单条 → 归一化 1 → final = 0.7
        List<ChunkResult> fused = HybridRetrievalService.fuse(
                new ArrayList<>(List.of(hit(1, 0.6))),
                new ArrayList<>(), 5, 0.7, 0.3);

        assertEquals(1, fused.size());
        assertEquals(0.7, fused.get(0).getScore(), 1e-9);
        assertEquals("VECTOR", fused.get(0).getRetrievalType());
    }

    @Test
    void 双路均空返回空列表() {
        assertTrue(HybridRetrievalService.fuse(new ArrayList<>(), new ArrayList<>(), 5, 0.7, 0.3).isEmpty());
    }

    @Test
    void 同分边界_归一化置1避免除零() {
        // 两 chunk 同分：max==min → 全部归一化为 1
        List<ChunkResult> fused = HybridRetrievalService.fuse(
                new ArrayList<>(List.of(hit(1, 0.5), hit(2, 0.5))),
                new ArrayList<>(), 5, 0.7, 0.3);

        assertEquals(0.7, fused.get(0).getScore(), 1e-9);
        assertEquals(0.7, fused.get(1).getScore(), 1e-9);
    }

    @Test
    void topK截断_保留高分前K条() {
        List<ChunkResult> fused = HybridRetrievalService.fuse(
                new ArrayList<>(List.of(hit(1, 0.9), hit(2, 0.8), hit(3, 0.1))),
                new ArrayList<>(), 2, 0.7, 0.3);

        assertEquals(2, fused.size());
        assertEquals(1L, fused.get(0).getChunkId());
        assertEquals(2L, fused.get(1).getChunkId());
    }
}
