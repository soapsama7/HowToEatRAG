-- =====================================================================
-- V3：文档回收站（Review 修订 R2，2026-09-30）
-- 变更：document 增加 deleted_at；DELETE 语义改为「移入回收站」：
--   逻辑删 + 记录删除时间，chunk 与 RustFS 对象保留；
--   保留期内可恢复，超过保留期由定时任务彻底清除（物理删 document/chunks/RustFS 对象）。
-- 检索不受影响：召回 SQL 自带 d.deleted = 0 过滤（DocumentChunkMapper）。
-- =====================================================================

ALTER TABLE document ADD COLUMN deleted_at TIMESTAMPTZ;

COMMENT ON COLUMN document.deleted_at IS
  '进入回收站时间（逻辑删除时间；NULL=未删除）；超过保留期由定时任务物理清除';
