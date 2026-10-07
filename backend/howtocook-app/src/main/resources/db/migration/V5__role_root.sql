-- =====================================================================
-- V5__role_root.sql —— 补充 ROOT 角色种子（优化 2.2 三档权限）
-- root 账号不在此处创建：密码哈希需由环境变量 ROOT_PASSWORD 在应用启动时计算
-- （见 app/RootAccountInitializer：不存在则创建，使用缺省密码时启动日志警告）
-- =====================================================================

INSERT INTO role (code, name, description) VALUES
    ('ROOT', '超级管理员', '最高权限：继承 ADMIN，另可提权/降权 admin、封号/解封、查看 agent 日志')
ON CONFLICT (code) DO NOTHING;
