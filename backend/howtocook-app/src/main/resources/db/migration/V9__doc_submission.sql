-- =====================================================================
-- V9__doc_submission.sql —— 用户文档提交申请表（优化 B2：内容贡献闭环）
-- user 提交菜谱/技巧 → admin/root 审核 → 同意转正式文档（走上传后续索引链路）
-- duplicate_hint 存相似度检测提示（优化 B3：提交时向量检索，供审核者参考，不自动拦截）
-- =====================================================================

CREATE TABLE doc_submission (
  id             BIGSERIAL PRIMARY KEY,
  user_id        BIGINT       NOT NULL,                    -- 提交人
  title          VARCHAR(200) NOT NULL,                    -- 菜谱/技巧标题
  doc_type       VARCHAR(20)  NOT NULL,                    -- RECIPE / TIP / OTHER
  category       VARCHAR(30),                              -- 分类
  content        TEXT         NOT NULL,                    -- markdown 原文
  status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / APPROVED / REJECTED
  reject_reason  VARCHAR(500),                             -- 拒绝原因
  duplicate_hint JSONB        DEFAULT '[]',                -- 相似文档提示 [{docId,title,similarity}]
  created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_submission_status ON doc_submission (status, created_at);
