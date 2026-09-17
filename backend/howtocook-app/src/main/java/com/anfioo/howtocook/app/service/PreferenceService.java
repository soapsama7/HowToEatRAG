package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.app.dto.PreferenceRequest;
import com.anfioo.howtocook.app.dto.PreferenceResponse;
import com.anfioo.howtocook.common.entity.user.UserPreference;
import com.anfioo.howtocook.common.enums.user.DietType;
import com.anfioo.howtocook.common.mapper.user.UserPreferenceMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.StringJoiner;

/**
 * 用户偏好服务：读写 + 供 Agent 使用的 Prompt 文本化（Step 4.2 注入对话）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;

    /** 查询偏好；未设置时返回默认值（spiceLevel=0, dietType=NONE，其余空），不报错 */
    public PreferenceResponse getByUserId(long userId) {
        UserPreference pref = userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId));
        if (pref == null) {
            return PreferenceResponse.builder()
                    .spiceLevel(0)
                    .dietType(DietType.NONE.name())
                    .allergens("")
                    .dislikedIngredients("")
                    .favoriteIngredients("")
                    .healthGoal("")
                    .build();
        }
        return PreferenceResponse.builder()
                .spiceLevel(pref.getSpiceLevel() == null ? 0 : pref.getSpiceLevel())
                .dietType(pref.getDietType() == null ? DietType.NONE.name() : pref.getDietType())
                .allergens(nullToEmpty(pref.getAllergens()))
                .dislikedIngredients(nullToEmpty(pref.getDislikedIngredients()))
                .favoriteIngredients(nullToEmpty(pref.getFavoriteIngredients()))
                .healthGoal(nullToEmpty(pref.getHealthGoal()))
                .build();
    }

    /**
     * 保存偏好（幂等 upsert）：存在则更新，不存在则插入。
     */
    public void save(long userId, PreferenceRequest request) {
        if (request.getDietType() != null && !DietType.isValid(request.getDietType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "dietType 非法，允许值：NONE/VEGETARIAN/VEGAN/HALAL/LOW_FAT");
        }

        UserPreference pref = userPreferenceMapper.selectOne(new LambdaQueryWrapper<UserPreference>()
                .eq(UserPreference::getUserId, userId));
        boolean create = pref == null;
        if (create) {
            pref = new UserPreference();
            pref.setUserId(userId);
        }
        pref.setSpiceLevel(request.getSpiceLevel() == null ? 0 : request.getSpiceLevel());
        pref.setDietType(request.getDietType() == null ? DietType.NONE.name() : request.getDietType());
        pref.setAllergens(request.getAllergens());
        pref.setDislikedIngredients(request.getDislikedIngredients());
        pref.setFavoriteIngredients(request.getFavoriteIngredients());
        pref.setHealthGoal(request.getHealthGoal());

        if (create) {
            userPreferenceMapper.insert(pref);
        } else {
            userPreferenceMapper.updateById(pref);
        }
    }

    /**
     * 偏好文本化：把结构化偏好拼成可注入 Prompt 的中文文本（Step 4.2 Agent 使用）。
     * 未设置任何偏好时返回空串（Agent 侧跳过注入）。
     */
    public String loadAsPromptText(long userId) {
        PreferenceResponse pref = getByUserId(userId);
        StringJoiner joiner = new StringJoiner("；");
        String[] spiceNames = {"不吃辣", "微辣", "中辣", "重辣"};
        if (pref.getSpiceLevel() != null && pref.getSpiceLevel() > 0) {
            joiner.add("辣度要求：" + spiceNames[pref.getSpiceLevel()]);
        }
        if (!DietType.NONE.name().equals(pref.getDietType())) {
            DietType dietType = DietType.valueOf(pref.getDietType());
            joiner.add("饮食类型：" + dietType.getDesc());
        }
        if (notBlank(pref.getAllergens())) {
            joiner.add("过敏原（推荐结果中严禁出现）：" + pref.getAllergens());
        }
        if (notBlank(pref.getDislikedIngredients())) {
            joiner.add("忌口食材（尽量避免）：" + pref.getDislikedIngredients());
        }
        if (notBlank(pref.getFavoriteIngredients())) {
            joiner.add("喜好食材（优先推荐）：" + pref.getFavoriteIngredients());
        }
        if (notBlank(pref.getHealthGoal())) {
            joiner.add("健康目标：" + pref.getHealthGoal());
        }
        return joiner.length() == 0 ? "" : "用户饮食偏好：" + joiner + "。";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
