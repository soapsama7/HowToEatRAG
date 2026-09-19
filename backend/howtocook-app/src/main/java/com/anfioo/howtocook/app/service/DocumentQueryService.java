package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.app.dto.DocumentSummaryResponse;
import com.anfioo.howtocook.app.dto.RecipeDetailResponse;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.enums.doc.DocStatus;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.anfioo.howtocook.common.storage.StorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * 用户侧菜谱查询服务（开发文档 §6：仅 READY 文档可见）。
 */
@Service
@RequiredArgsConstructor
public class DocumentQueryService {

    private final DocumentMapper documentMapper;
    private final StorageService storageService;

    /** 菜谱列表：仅 READY，标题关键词模糊检索，ID 倒序 */
    public Page<DocumentSummaryResponse> list(long pageNum, long pageSize, String keyword) {
        Page<Document> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<Document>()
                .eq(Document::getStatus, DocStatus.READY.name())
                .orderByDesc(Document::getId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Document::getTitle, keyword.trim());
        }
        Page<Document> result = documentMapper.selectPage(page, wrapper);
        Page<DocumentSummaryResponse> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream()
                .map(d -> DocumentSummaryResponse.builder()
                        .id(d.getId())
                        .title(d.getTitle())
                        .category(d.getCategory())
                        .difficulty(d.getDifficulty())
                        .cookMinutes(d.getCookMinutes())
                        .calories(d.getCalories())
                        .build())
                .toList());
        return response;
    }

    /** 菜谱详情：仅 READY，含 RustFS 原文全文 */
    public RecipeDetailResponse detail(long id) {
        Document document = documentMapper.selectById(id);
        if (document == null || !DocStatus.READY.name().equals(document.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "菜谱不存在或未就绪");
        }
        return RecipeDetailResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .category(document.getCategory())
                .difficulty(document.getDifficulty())
                .calories(document.getCalories())
                .content(new String(storageService.getObject(document.getObjectKey()), StandardCharsets.UTF_8))
                .build();
    }
}
