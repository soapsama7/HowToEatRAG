package com.anfioo.howtocook.common.document;

import lombok.Getter;

import java.util.List;

/**
 * 章节内容块：section 内的一个顶层元素（段落 / 列表 / H3 小节等）的清洗后文本。
 * <p>h3 为该块所属的 H3 小节标题（无则为 null），供分块器在超长章节内按 H3 二次切分。</p>
 */
@Getter
public class ParsedBlock {

    /** 所属 H3 小节标题（可空） */
    private final String h3;

    /** 清洗后纯文本 */
    private final String text;

    public ParsedBlock(String h3, String text) {
        this.h3 = h3;
        this.text = text;
    }

    /** section 的块列表快捷构造 */
    public static List<ParsedBlock> of(String h3, String text) {
        return List.of(new ParsedBlock(h3, text));
    }
}
