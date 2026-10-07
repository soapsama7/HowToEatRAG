-- =====================================================================
-- V4：上传内容哈希查重（Review 修订 R3，2026-09-30）
-- 变更：document 增加 content_hash（SHA-256 hex，64 字符）。
--   上传时计算文件哈希，与未删除文档比对，命中即拒绝（防重复文档污染检索结果）。
--   用普通索引而非唯一索引：回收站中的文档允许与重新上传的副本共存，到期自动清除。
--   存量数据由 ContentHashBackfillRunner 启动时从 RustFS 回填。
-- =====================================================================

ALTER TABLE document ADD COLUMN content_hash VARCHAR(64);

CREATE INDEX idx_document_content_hash ON document (content_hash);

COMMENT ON COLUMN document.content_hash IS '文件内容 SHA-256（hex 小写），上传查重用；存量由启动任务回填';
