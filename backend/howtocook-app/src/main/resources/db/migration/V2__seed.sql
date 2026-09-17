-- =====================================================================
-- V2__seed.sql —— 角色种子数据（Step 1.2）
-- admin 账号不在此处创建：密码哈希需由环境变量 ADMIN_PASSWORD 在应用启动时计算
-- （见 app/AdminAccountInitializer：不存在则创建，使用缺省密码时启动日志警告）
-- =====================================================================

INSERT INTO role (code, name, description) VALUES
    ('USER',  '普通用户', '可使用对话、偏好等用户功能'),
    ('ADMIN', '管理员',   '可访问 /api/admin/** 管理端接口')
ON CONFLICT (code) DO NOTHING;
