package com.anfioo.howtocook.app.service;

import com.anfioo.howtocook.app.dto.SubmissionListResponse;
import com.anfioo.howtocook.app.dto.SubmissionSubmitRequest;
import com.anfioo.howtocook.common.embedding.EmbeddingClient;
import com.anfioo.howtocook.common.entity.doc.DocSubmission;
import com.anfioo.howtocook.common.entity.doc.Document;
import com.anfioo.howtocook.common.entity.user.User;
import com.anfioo.howtocook.common.enums.doc.DocType;
import com.anfioo.howtocook.common.enums.doc.SubmissionStatus;
import com.anfioo.howtocook.common.mapper.doc.DocSubmissionMapper;
import com.anfioo.howtocook.common.mapper.doc.DocumentChunkMapper;
import com.anfioo.howtocook.common.mapper.doc.DocumentMapper;
import com.anfioo.howtocook.common.mapper.user.UserMapper;
import com.anfioo.howtocook.common.result.BusinessException;
import com.anfioo.howtocook.common.result.ErrorCode;
import com.anfioo.howtocook.common.retrieval.ChunkResult;
import com.anfioo.howtocook.common.util.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档提交申请服务（优化 B2/B3）：user 提交 → admin/root 审核 → 同意转正式文档。
 * <p>重复检测分两档：① 内容 SHA-256 精确查重（完全一致直接拒绝）；② 标题向量相似度
 * （复用 pgvector 召回，相似度 ≥ 阈值时作为提示写入 duplicate_hint，不自动拦截，判断权在审核者）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocSubmissionService {

    /** 内容上限：2MB（与上传一致） */
    private static final long MAX_CONTENT_SIZE = 2L * 1024 * 1024;

    /** 标题相似度提示阈值（余弦相似度） */
    private static final double SIMILARITY_THRESHOLD = 0.85;

    private final DocSubmissionMapper submissionMapper;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final UserMapper userMapper;
    private final EmbeddingClient embeddingClient;
    private final DocumentAdminService documentAdminService;
    private final ObjectMapper objectMapper;

    /** 提交申请：校验 → 精确查重 → 相似度提示 → 落 PENDING */
    @Transactional
    public DocSubmission submit(long userId, SubmissionSubmitRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容不能为空");
        }
        validateDocType(req.getDocType());

        byte[] bytes = req.getContent().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "内容超过 2MB 上限");
        }

        // ① 精确查重：内容完全一致直接拒绝
        String contentHash = DigestUtil.sha256Hex(bytes);
        Document duplicate = documentMapper.selectByContentHash(contentHash);
        if (duplicate != null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "知识库已存在相同内容：docId=" + duplicate.getId() + "（" + duplicate.getTitle() + "）");
        }

        // ② 相似度提示（不拦截）
        List<Map<String, Object>> hint = detectSimilar(req.getTitle().trim());

        DocSubmission sub = new DocSubmission();
        sub.setUserId(userId);
        sub.setTitle(req.getTitle().trim());
        sub.setDocType(req.getDocType() == null ? DocType.OTHER.name() : req.getDocType());
        sub.setCategory(req.getCategory());
        sub.setContent(req.getContent());
        sub.setStatus(SubmissionStatus.PENDING.name());
        sub.setDuplicateHint(toJson(hint));
        submissionMapper.insert(sub);
        return sub;
    }

    /** 分页查询（审核用，可按状态筛选，时间倒序，附带提交人用户名与相似度提示） */
    public Page<SubmissionListResponse> list(long pageNum, long pageSize, String status) {
        Page<DocSubmission> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<DocSubmission> wrapper = new LambdaQueryWrapper<DocSubmission>()
                .orderByDesc(DocSubmission::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(DocSubmission::getStatus, status);
        }
        Page<DocSubmission> result = submissionMapper.selectPage(page, wrapper);

        Map<Long, String> nameMap = loadUsernames(result.getRecords());

        Page<SubmissionListResponse> response = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        response.setRecords(result.getRecords().stream()
                .map(s -> toResponse(s, nameMap.get(s.getUserId())))
                .toList());
        return response;
    }

    /** 同意：转正式文档（复用上传建档链路）→ 置 APPROVED */
    @Transactional
    public void approve(long id) {
        DocSubmission sub = requirePending(id);
        byte[] bytes = sub.getContent().getBytes(StandardCharsets.UTF_8);
        documentAdminService.createDocument(bytes, sub.getTitle(), sub.getDocType(), sub.getCategory(), sub.getUserId());
        sub.setStatus(SubmissionStatus.APPROVED.name());
        sub.setRejectReason(null);
        submissionMapper.updateById(sub);
        log.info("提交申请已通过并转正式文档: submissionId={}, title={}", id, sub.getTitle());
    }

    /** 拒绝：置 REJECTED + 拒绝原因 */
    public void reject(long id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写拒绝原因");
        }
        DocSubmission sub = requirePending(id);
        sub.setStatus(SubmissionStatus.REJECTED.name());
        sub.setRejectReason(reason);
        submissionMapper.updateById(sub);
        log.info("提交申请已拒绝: submissionId={}, reason={}", id, reason);
    }

    /** 标题相似度检测：embedding → 向量召回 top-5 → 按文档去重取最高分 → 阈值过滤 */
    private List<Map<String, Object>> detectSimilar(String title) {
        try {
            float[] vec = embeddingClient.embedAll(List.of(title)).get(0);
            List<ChunkResult> hits = chunkMapper.vectorRecall(new PGvector(vec), 5);
            Map<Long, ChunkResult> byDoc = new LinkedHashMap<>();
            for (ChunkResult h : hits) {
                ChunkResult prev = byDoc.get(h.getDocId());
                if (prev == null || (h.getScore() != null && prev.getScore() != null && h.getScore() > prev.getScore())) {
                    byDoc.put(h.getDocId(), h);
                }
            }
            List<Map<String, Object>> result = new ArrayList<>();
            for (ChunkResult h : byDoc.values()) {
                if (h.getScore() != null && h.getScore() >= SIMILARITY_THRESHOLD) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("docId", h.getDocId());
                    m.put("title", h.getTitle());
                    m.put("similarity", Math.round(h.getScore() * 1000) / 1000.0);
                    result.add(m);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("相似度检测失败，跳过提示: title={}, error={}", title, e.getMessage());
            return List.of();
        }
    }

    /** 批量查提交人用户名（避免 N+1） */
    private Map<Long, String> loadUsernames(List<DocSubmission> records) {
        Set<Long> userIds = records.stream().map(DocSubmission::getUserId).collect(Collectors.toSet());
        Map<Long, String> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        for (User u : users) {
            map.put(u.getId(), u.getUsername());
        }
        return map;
    }

    private SubmissionListResponse toResponse(DocSubmission s, String username) {
        return SubmissionListResponse.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .username(username)
                .title(s.getTitle())
                .docType(s.getDocType())
                .category(s.getCategory())
                .status(s.getStatus())
                .rejectReason(s.getRejectReason())
                .duplicateHint(parseHint(s.getDuplicateHint()))
                .content(s.getContent())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private DocSubmission requirePending(long id) {
        DocSubmission sub = submissionMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "提交申请不存在");
        }
        if (!SubmissionStatus.PENDING.name().equals(sub.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该申请已处理");
        }
        return sub;
    }

    private void validateDocType(String docType) {
        if (docType != null && !DocType.RECIPE.name().equals(docType)
                && !DocType.TIP.name().equals(docType) && !DocType.OTHER.name().equals(docType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "docType 仅允许 RECIPE/TIP/OTHER");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseHint(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
