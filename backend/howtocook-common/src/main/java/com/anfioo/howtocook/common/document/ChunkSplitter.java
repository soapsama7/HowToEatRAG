package com.anfioo.howtocook.common.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构感知分块器（开发文档 §5.2）。
 * <p>规则：简介块 + 每个 H2 章节各成一块；单块超过 {@value #MAX_CHUNK_CHARS} 字符时按 H3 / 块边界
 * 二次切分，无结构的超长文本按滑窗切分；相邻 chunk 之间保留 {@value #OVERLAP_CHARS} 字符重叠；
 * 每块 content 前缀章节名（提升向量与关键词双路的章节命中率）。</p>
 */
@Component
public class ChunkSplitter {

    /** 单块最大字符数 */
    public static final int MAX_CHUNK_CHARS = 800;
    /** 相邻块重叠字符数 */
    public static final int OVERLAP_CHARS = 50;

    private static final String INTRO_SECTION = "简介";

    /**
     * 分块：返回全局递增 chunkIndex 的草稿列表。
     */
    public List<ChunkDraft> split(ParsedDocument document) {
        List<ChunkDraft> drafts = new ArrayList<>();
        int index = 0;
        for (ParsedSection section : document.getSections()) {
            String sectionName = section.getHeading() == null ? INTRO_SECTION : section.getHeading();
            for (String body : splitSection(section)) {
                String content = (section.getHeading() == null ? "" : section.getHeading() + "\n") + body;
                drafts.add(new ChunkDraft(index++, sectionName, content.strip()));
            }
        }
        return drafts;
    }

    /** 单章节 → 一个或多个 chunk 正文（不含章节名前缀） */
    private List<String> splitSection(ParsedSection section) {
        String fullText = section.fullText();
        if (fullText.length() <= MAX_CHUNK_CHARS) {
            return List.of(fullText);
        }

        // 超 800：优先按 H3 边界 + 块边界累积切分
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentH3 = null;
        for (ParsedBlock block : section.getBlocks()) {
            String text = (block.getH3() != null && !block.getH3().equals(currentH3)
                    ? "### " + block.getH3() + "\n" : "") + block.getText();
            currentH3 = block.getH3();
            // 单块自身超长（如无结构长段落）：滑窗切分
            if (text.length() > MAX_CHUNK_CHARS) {
                flush(pieces, current);
                pieces.addAll(slidingWindow(text));
                continue;
            }
            if (current.length() + text.length() + 1 > MAX_CHUNK_CHARS && current.length() > 0) {
                flush(pieces, current);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(text);
        }
        flush(pieces, current);

        // 相邻 chunk 间加 overlap
        return withOverlap(pieces);
    }

    private void flush(List<String> pieces, StringBuilder current) {
        if (current.length() > 0) {
            pieces.add(current.toString().strip());
            current.setLength(0);
        }
    }

    /** 滑窗切分：800 字符窗口、50 字符步进重叠 */
    private List<String> slidingWindow(String text) {
        List<String> windows = new ArrayList<>();
        int step = MAX_CHUNK_CHARS - OVERLAP_CHARS;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + MAX_CHUNK_CHARS, text.length());
            windows.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
        }
        return windows;
    }

    /** 相邻 chunk 之间：后块前缀前块末尾 50 字符 */
    private List<String> withOverlap(List<String> pieces) {
        if (pieces.size() <= 1) {
            return pieces;
        }
        List<String> result = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i);
            if (i > 0) {
                String prev = pieces.get(i - 1);
                String tail = prev.substring(Math.max(0, prev.length() - OVERLAP_CHARS));
                result.add(tail + "\n" + piece);
            } else {
                result.add(piece);
            }
        }
        return result;
    }
}
