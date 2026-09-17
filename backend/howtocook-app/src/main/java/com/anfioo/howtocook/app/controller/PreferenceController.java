package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.dto.PreferenceRequest;
import com.anfioo.howtocook.app.dto.PreferenceResponse;
import com.anfioo.howtocook.app.service.PreferenceService;
import cn.dev33.satoken.stp.StpUtil;
import com.anfioo.howtocook.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户偏好接口（需登录）。
 */
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;

    /** 查询当前用户偏好（未设置时返回默认值） */
    @GetMapping
    public Result<PreferenceResponse> get() {
        return Result.ok(preferenceService.getByUserId(StpUtil.getLoginIdAsLong()));
    }

    /** 保存当前用户偏好（幂等 upsert） */
    @PutMapping
    public Result<Void> save(@Valid @RequestBody PreferenceRequest request) {
        preferenceService.save(StpUtil.getLoginIdAsLong(), request);
        return Result.ok();
    }
}
