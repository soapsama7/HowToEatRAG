package com.anfioo.howtocook.common.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块器单测：结构感知 + 超长二次切分 + overlap。
 */
class ChunkSplitterTest {

    private final RecipeMarkdownParser parser = new RecipeMarkdownParser();
    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    void 红烧鱼_结构分块() throws IOException {
        ParsedDocument doc = parser.parse(Files.readString(Path.of("..", "..", "dishes/aquatic/红烧鱼.md")));
        List<ChunkDraft> chunks = splitter.split(doc);

        assertTrue(chunks.size() >= 4, "红烧鱼应有简介+原料+计算+操作等多块，实际 " + chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        // chunk_index 全局递增
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).getChunkIndex());
        }
        // section 名与章节对齐
        assertEquals("简介", chunks.get(0).getSection());
        assertTrue(chunks.stream().anyMatch(c -> "必备原料和工具".equals(c.getSection())));
        // 内容前缀章节名
        assertTrue(chunks.stream().filter(c -> "计算".equals(c.getSection()))
                .allMatch(c -> c.getContent().startsWith("计算")));
        // token_count 与 content 长度一致
        assertTrue(chunks.stream().allMatch(c -> c.getTokenCount() == c.getContent().length()));
    }

    @Test
    void 超长章节_二次切分与overlap() {
        // 构造一个 >800 字符的单章节（无 H3），验证滑窗 + overlap
        String longText = "这是一段很长的操作说明。".repeat(120);
        ParsedDocument doc = parser.parse("# 测试的做法\n\n预估烹饪难度：★\n\n" + longText);
        List<ChunkDraft> chunks = splitter.split(doc);

        assertTrue(chunks.size() >= 2, "超长文本应被切成多块");
        // 每块 ≤ 800 + 章节名前缀 + overlap 余量
        for (ChunkDraft chunk : chunks) {
            assertTrue(chunk.getContent().length() <= ChunkSplitter.MAX_CHUNK_CHARS
                            + ChunkSplitter.OVERLAP_CHARS + 20,
                    "块长超限: " + chunk.getContent().length());
        }
        // 相邻块有 overlap：第二块开头应包含第一块的结尾片段
        String first = chunks.get(0).getContent();
        String firstTail = first.substring(Math.max(0, first.length() - ChunkSplitter.OVERLAP_CHARS));
        assertTrue(chunks.get(1).getContent().contains(firstTail.strip()),
                "第二块应包含第一块的尾部 overlap");
    }

    @Test
    void 食品安全_tips分块() throws IOException {
        ParsedDocument doc = parser.parse(Files.readString(Path.of("..", "..", "tips/learn/食品安全.md")));
        List<ChunkDraft> chunks = splitter.split(doc);
        assertTrue(chunks.size() >= 1, "tips 文档至少产生一个 chunk");
        assertTrue(chunks.stream().allMatch(c -> !c.getContent().isEmpty()), "不应有空块");
    }
}
