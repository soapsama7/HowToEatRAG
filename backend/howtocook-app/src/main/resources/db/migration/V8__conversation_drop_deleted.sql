-- =====================================================================
-- V8__conversation_drop_deleted.sql —— 会话删除改为物理删（review 2.4）
-- 背景：2.4 已把 message 改为物理删，会话再保留逻辑删（deleted 字段）已无意义，
-- 直接删掉 deleted 列，conversation 删除走物理删。
-- =====================================================================

ALTER TABLE conversation DROP COLUMN deleted;
