package com.anfioo.howtocook.app.controller;

import com.anfioo.howtocook.app.dto.DocumentSummaryResponse;
import com.anfioo.howtocook.app.dto.RecipeDetailResponse;
import com.anfioo.howtocook.app.service.DocumentQueryService;
import com.anfioo.howtocook.common.result.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户侧菜谱接口（/api/documents，要求登录，开发文档 §6）。
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentQueryService documentQueryService;

    /** 菜谱列表（仅 READY，分页 + 关键词） */
    @GetMapping
    public Result<Page<DocumentSummaryResponse>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                      @RequestParam(defaultValue = "20") long pageSize,
                                                      @RequestParam(required = false) String keyword) {
        return Result.ok(documentQueryService.list(pageNum, pageSize, keyword));
    }

    /** 菜谱详情（RustFS 原文） */
    @GetMapping("/{id}")
    public Result<RecipeDetailResponse> detail(@PathVariable long id) {
        return Result.ok(documentQueryService.detail(id));
    }
}
