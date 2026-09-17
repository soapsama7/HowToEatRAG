package com.anfioo.howtocook.common.document;

import java.util.Map;

/**
 * 菜谱目录名 → 中文分类映射（开发文档 §5.1，固定映射表）。
 * <p>template 目录不导入（映射仅用于标注，批量导入时排除）。</p>
 */
public final class CategoryNames {

    private CategoryNames() {
    }

    public static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("aquatic", "水产"),
            Map.entry("breakfast", "早餐"),
            Map.entry("condiment", "调味"),
            Map.entry("dessert", "甜点"),
            Map.entry("drink", "饮品"),
            Map.entry("meat_dish", "荤菜"),
            Map.entry("semi-finished", "半成品"),
            Map.entry("soup", "汤"),
            Map.entry("staple", "主食"),
            Map.entry("vegetable_dish", "素菜"),
            Map.entry("tips", "烹饪技巧")
    );

    /** 目录是否可导入（template 排除） */
    public static boolean importable(String dir) {
        return !"template".equals(dir);
    }
}
