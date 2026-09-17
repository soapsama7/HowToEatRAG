package com.anfioo.howtocook.common.enums.user;

import lombok.Getter;

/**
 * 饮食类型，对应 {@code user_preference.diet_type} 字段。
 */
@Getter
public enum DietType {

    /** 无限制 */
    NONE("无特殊饮食限制"),
    /** 素食（可含蛋奶） */
    VEGETARIAN("素食"),
    /** 纯素 */
    VEGAN("纯素"),
    /** 清真 */
    HALAL("清真"),
    /** 低脂 */
    LOW_FAT("低脂");

    private final String desc;

    DietType(String desc) {
        this.desc = desc;
    }

    /** 是否为合法的饮食类型值 */
    public static boolean isValid(String value) {
        for (DietType type : values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
