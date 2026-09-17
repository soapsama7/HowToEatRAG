package com.anfioo.howtocook.common.document;

import lombok.Getter;

import java.util.List;

/**
 * 解析结果：元数据 + 章节结构（写入 document 表与分块的中间模型）。
 */
@Getter
public class ParsedDocument {

    /** 菜谱名：H1 去掉"的做法"后缀 */
    private final String title;

    /** 预估烹饪难度（★ 数量），可为 null（文档未标注） */
    private final Integer difficulty;

    /** 预估卡路里（大卡），可为 null */
    private final Integer calories;

    /** 章节列表：第一个元素为简介块（heading=null），其后为各 H2 章节 */
    private final List<ParsedSection> sections;

    public ParsedDocument(String title, Integer difficulty, Integer calories, List<ParsedSection> sections) {
        this.title = title;
        this.difficulty = difficulty;
        this.calories = calories;
        this.sections = sections;
    }
}
