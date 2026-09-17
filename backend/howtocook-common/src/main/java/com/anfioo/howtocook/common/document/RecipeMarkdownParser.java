package com.anfioo.howtocook.common.document;

import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜谱 Markdown 解析器（commonmark-java AST，不靠正则硬切标题）。
 * <p>职责（开发文档 §5.1）：
 * ① 元数据提取：title（H1 去"的做法"后缀）、difficulty（★ 数）、calories（数字）；
 * ② 文本清洗：剥离 HTML 注释、图片语法，链接保留文字；
 * ③ 结构提取：简介块（H1→首个 H2）+ 各 H2 章节，块内保留 H3 边界供分块器二次切分。</p>
 */
@org.springframework.stereotype.Component
public class RecipeMarkdownParser {

    private static final String TITLE_SUFFIX = "的做法";

    private final Parser parser = Parser.builder().build();

    public ParsedDocument parse(String markdown) {
        Node document = parser.parse(markdown);

        String title = null;
        Integer difficulty = null;
        Integer calories = null;

        List<ParsedSection> sections = new ArrayList<>();
        String currentH2 = null;          // null = 简介块
        String currentH3 = null;
        List<ParsedBlock> currentBlocks = new ArrayList<>();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                String headingText = render(heading).strip();
                if (heading.getLevel() == 1) {
                    title = headingText.endsWith(TITLE_SUFFIX)
                            ? headingText.substring(0, headingText.length() - TITLE_SUFFIX.length())
                            : headingText;
                    continue;
                }
                if (heading.getLevel() == 2) {
                    // H2：收上一章节，开新章节
                    if (!currentBlocks.isEmpty()) {
                        sections.add(new ParsedSection(currentH2, currentBlocks));
                    }
                    currentH2 = headingText;
                    currentH3 = null;
                    currentBlocks = new ArrayList<>();
                    continue;
                }
                // H3 及以下：作为块边界（H3 记录为小节标题）
                currentH3 = headingText;
                currentBlocks.add(new ParsedBlock(currentH3, headingText));
                continue;
            }
            if (node instanceof ThematicBreak) {
                continue;
            }

            String text = render(node).strip();
            if (text.isEmpty()) {
                continue;
            }
            // 元数据行（同时保留在正文里，检索有价值）
            if (difficulty == null) {
                difficulty = extractStars(text);
            }
            if (calories == null) {
                calories = extractCalories(text);
            }
            currentBlocks.add(new ParsedBlock(currentH3, text));
        }
        if (!currentBlocks.isEmpty()) {
            sections.add(new ParsedSection(currentH2, currentBlocks));
        }
        return new ParsedDocument(title, difficulty, calories, sections);
    }

    /** "预估烹饪难度：★★★★" → 4 */
    private Integer extractStars(String text) {
        if (!text.contains("预估烹饪难度")) {
            return null;
        }
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c == '★') {
                count++;
            }
        }
        return count;
    }

    /** "预估卡路里：570 大卡" → 570 */
    private Integer extractCalories(String text) {
        if (!text.contains("预估卡路里")) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    /**
     * AST 节点 → 清洗后文本：跳过 HTML 注释与图片，链接保留文字，列表保留序号/符号。
     */
    private String render(Node node) {
        StringBuilder sb = new StringBuilder();
        renderChildren(node, sb);
        return sb.toString();
    }

    private void renderChildren(Node node, StringBuilder sb) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            renderNode(child, sb);
        }
    }

    private void renderNode(Node node, StringBuilder sb) {
        if (node instanceof HtmlBlock || node instanceof HtmlInline || node instanceof Image) {
            return; // 清洗：注释 / 图片语法
        }
        if (node instanceof Text text) {
            sb.append(text.getLiteral());
            return;
        }
        if (node instanceof Code code) {
            sb.append(code.getLiteral());
            return;
        }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
            sb.append('\n');
            return;
        }
        if (node instanceof Link) {
            renderChildren(node, sb); // 保留链接文字，丢弃目标
            return;
        }
        if (node instanceof Heading heading) {
            sb.append("#".repeat(heading.getLevel())).append(' ');
            renderChildren(node, sb);
            sb.append('\n');
            return;
        }
        if (node instanceof BulletList) {
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                sb.append("- ");
                renderChildren(item, sb);
                sb.append('\n');
            }
            return;
        }
        if (node instanceof OrderedList ordered) {
            int index = ordered.getStartNumber();
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                sb.append(index++).append(". ");
                renderChildren(item, sb);
                sb.append('\n');
            }
            return;
        }
        if (node instanceof ListItem || node instanceof Paragraph || node instanceof BlockQuote) {
            renderChildren(node, sb);
            return;
        }
        if (node instanceof FencedCodeBlock fenced) {
            sb.append(fenced.getLiteral());
            return;
        }
        // 兜底：递归子节点
        renderChildren(node, sb);
    }
}
