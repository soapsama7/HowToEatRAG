package com.anfioo.howtocook.common.document;

import lombok.Getter;

import java.util.List;

/**
 * 解析出的章节：简介块（heading 为 null）或一个 H2 章节。
 */
@Getter
public class ParsedSection {

    /** 章节名（H2 文本）；简介块为 null */
    private final String heading;

    /** 章节内的内容块（顺序保留） */
    private final List<ParsedBlock> blocks;

    public ParsedSection(String heading, List<ParsedBlock> blocks) {
        this.heading = heading;
        this.blocks = blocks;
    }

    /** 章节全文（含 H3 标题行的拼接，按块间空行分隔） */
    public String fullText() {
        StringBuilder sb = new StringBuilder();
        for (ParsedBlock block : blocks) {
            if (block.getH3() != null) {
                sb.append("### ").append(block.getH3()).append("\n");
            }
            sb.append(block.getText()).append("\n\n");
        }
        return sb.toString().strip();
    }
}
