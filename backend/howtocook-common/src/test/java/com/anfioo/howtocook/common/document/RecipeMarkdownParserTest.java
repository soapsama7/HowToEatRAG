package com.anfioo.howtocook.common.document;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析器单测：直接用仓库真实菜谱文件（工作目录 = howtocook-common，仓库根为 ../../）。
 */
class RecipeMarkdownParserTest {

    private final RecipeMarkdownParser parser = new RecipeMarkdownParser();

    private String read(String relativePath) throws IOException {
        Path path = Path.of("..", "..", relativePath);
        return Files.readString(path);
    }

    @Test
    void parse红烧鱼() throws IOException {
        ParsedDocument doc = parser.parse(read("dishes/aquatic/红烧鱼.md"));

        assertEquals("红烧鱼", doc.getTitle());
        assertEquals(4, doc.getDifficulty());
        assertEquals(570, doc.getCalories());

        List<String> headings = doc.getSections().stream()
                .map(ParsedSection::getHeading)
                .map(h -> h == null ? "简介" : h)
                .toList();
        assertTrue(headings.contains("简介"), "应有简介块");
        assertTrue(headings.contains("必备原料和工具"), "应有必备原料和工具章节");
        assertTrue(headings.contains("计算"), "应有计算章节");
        assertTrue(headings.contains("操作"), "应有操作章节");
        assertTrue(headings.contains("附加内容"), "应有附加内容章节");

        // 清洗：全文无 HTML 注释残留、无图片语法
        for (ParsedSection section : doc.getSections()) {
            assertFalse(section.fullText().contains("<!--"), "不应残留 HTML 注释");
            assertFalse(section.fullText().contains("!["));
        }
        // 链接保留文字
        ParsedSection extra = doc.getSections().stream()
                .filter(s -> "附加内容".equals(s.getHeading())).findFirst().orElseThrow();
        assertTrue(extra.fullText().contains("Issue") || extra.fullText().contains("Pull request"),
                "链接文字应保留");
    }

    @Test
    void parse示例菜模板_注释剥离() throws IOException {
        ParsedDocument doc = parser.parse(read("dishes/template/示例菜/示例菜.md"));
        assertEquals("示例菜", doc.getTitle());
        for (ParsedSection section : doc.getSections()) {
            assertFalse(section.fullText().contains("<!--"), "模板注释应剥离干净");
            assertFalse(section.fullText().contains("标题必须是"), "注释内容不应进入正文");
        }
        // 模板未标难度/卡路里
        assertEquals(1, doc.getSections().stream()
                .filter(s -> "操作".equals(s.getHeading())).count(), "模板应有操作章节");
    }

    @Test
    void parse食品安全_tips() throws IOException {
        ParsedDocument doc = parser.parse(read("tips/learn/食品安全.md"));
        assertNotNull(doc.getTitle());
        // tips 文档清洗后同样无注释残留
        for (ParsedSection section : doc.getSections()) {
            assertFalse(section.fullText().contains("<!--"));
        }
        assertTrue(doc.getSections().size() >= 1, "至少一个章节（含简介块）");
    }
}
