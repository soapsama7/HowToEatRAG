-- =====================================================================
-- V1__init.sql —— 初始建表（Step 1.1）
-- 与开发文档 §3 DDL 逐字段一致；10 张表 + 索引
-- 前提：数据库已含 vector / zhparser 扩展与 zh_cn 检索配置（docker 初始化脚本完成）
-- =====================================================================

-- ========== 用户权限 ==========
CREATE TABLE "user" (
  id           BIGSERIAL PRIMARY KEY,
  username     VARCHAR(50)  NOT NULL UNIQUE,
  password     VARCHAR(100) NOT NULL,               -- BCrypt 哈希
  nickname     VARCHAR(50),
  status       SMALLINT     NOT NULL DEFAULT 1,     -- 1 正常 0 禁用
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted      SMALLINT     NOT NULL DEFAULT 0      -- 逻辑删除
);

CREATE TABLE role (
  id           BIGSERIAL PRIMARY KEY,
  code         VARCHAR(20)  NOT NULL UNIQUE,        -- USER / ADMIN
  name         VARCHAR(50)  NOT NULL,
  description  VARCHAR(200)
);

CREATE TABLE user_role (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  role_id      BIGINT       NOT NULL,
  CONSTRAINT uk_user_role UNIQUE (user_id, role_id)
);

-- ========== 用户偏好 ==========
CREATE TABLE user_preference (
  id                     BIGSERIAL PRIMARY KEY,
  user_id                BIGINT       NOT NULL UNIQUE,
  spice_level            SMALLINT     DEFAULT 0,    -- 0 不辣 1 微辣 2 中辣 3 重辣
  diet_type              VARCHAR(20)  DEFAULT 'NONE', -- NONE/VEGETARIAN/VEGAN/HALAL/LOW_FAT...
  allergens              TEXT,                      -- 逗号分隔，如 "虾,花生"
  disliked_ingredients   TEXT,                      -- 忌口食材
  favorite_ingredients   TEXT,                      -- 喜好食材
  health_goal            VARCHAR(200),              -- 健康目标自由文本
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ========== 知识库 ==========
CREATE TABLE document (
  id            BIGSERIAL PRIMARY KEY,
  title         VARCHAR(200) NOT NULL,              -- 菜谱名，如 "红烧鱼"
  doc_type      VARCHAR(20)  NOT NULL,              -- RECIPE / TIP / OTHER
  category      VARCHAR(30),                       -- 目录名映射：aquatic→水产 ... tips→技巧
  object_key    VARCHAR(300) NOT NULL,              -- RustFS 对象键（随机 UUID.md，防路径穿越）
  file_size     BIGINT,
  difficulty    SMALLINT,                           -- 难度星级 1-5（从 ★ 解析）
  cook_minutes  INT,                                -- 预计时长（分钟，正文解析可得则填）
  calories      INT,                                -- 预估卡路里（大卡）
  version       INT           NOT NULL DEFAULT 1,   -- 乐观锁 + 版本一致性
  status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING/INDEXING/READY/FAILED
  error_msg     VARCHAR(1000),
  chunk_count   INT           DEFAULT 0,
  uploader_id   BIGINT,
  created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
  deleted       SMALLINT       NOT NULL DEFAULT 0
);
-- Agent 检索只允许命中 status='READY' 且 deleted=0 的文档（由检索 SQL JOIN 保证）

CREATE TABLE document_chunk (
  id           BIGSERIAL PRIMARY KEY,
  doc_id       BIGINT        NOT NULL,
  version      INT           NOT NULL,              -- 冗余 document.version，用于版本一致性判断
  chunk_index  INT           NOT NULL,              -- 文档内序号
  section      VARCHAR(100),                        -- 所属 H2 章节名（"必备原料和工具"/"操作"...）
  content      TEXT          NOT NULL,              -- chunk 纯文本
  token_count  INT,
  embedding    vector(1024)  NOT NULL,              -- qwen3.7-text-embedding，1024 维
  content_tsv  TSVECTOR      GENERATED ALWAYS AS (to_tsvector('zh_cn', content)) STORED,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_chunk_hnsw  ON document_chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunk_tsv   ON document_chunk USING gin (content_tsv);
CREATE INDEX idx_chunk_doc   ON document_chunk (doc_id, version);

-- ========== 会话 ==========
CREATE TABLE conversation (
  id          BIGSERIAL PRIMARY KEY,
  user_id     BIGINT        NOT NULL,
  title       VARCHAR(200),                          -- 首条消息生成或默认 "新对话"
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  deleted     SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE message (
  id               BIGSERIAL PRIMARY KEY,
  conversation_id  BIGINT       NOT NULL,
  role             VARCHAR(20)  NOT NULL,            -- USER / ASSISTANT / TOOL / SYSTEM
  message_type     VARCHAR(30)  NOT NULL,            -- USER_MESSAGE / AGENT_THINKING / TOOL_CALL / TOOL_RESULT / REFERENCE / FINAL_ANSWER
  content          TEXT,                             -- 正文（回答内容 / 工具名 / 引用说明）
  tool_name        VARCHAR(100),                     -- TOOL_CALL/TOOL_RESULT 时记录
  "references"     JSONB        DEFAULT '[]',        -- 引用列表（references 为 PG 保留字，必须带引号；开发文档 §3 原文未加引号，已修正）
  trace            JSONB        DEFAULT '[]',        -- Agent 执行轨迹（结构化步骤，非真实 CoT）
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_msg_conv ON message (conversation_id, created_at);

-- ========== 运维 ==========
CREATE TABLE audit_log (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT,
  username     VARCHAR(50),
  operation    VARCHAR(100)  NOT NULL,               -- 操作名（如 UPLOAD_DOCUMENT）
  method       VARCHAR(200),                         -- 请求方法与路径
  params       TEXT,                                 -- 入参摘要（截断，脱敏）
  result       VARCHAR(20),                          -- SUCCESS / FAIL
  ip           VARCHAR(50),
  cost_ms      BIGINT,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE index_task (
  id           BIGSERIAL PRIMARY KEY,
  task_no      VARCHAR(64)   NOT NULL UNIQUE,        -- UUID
  doc_id       BIGINT        NOT NULL,
  task_type    VARCHAR(20)   NOT NULL,               -- INDEX / REINDEX / DELETE_CLEAN
  status       VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING/PROCESSING/SUCCESS/FAILED
  error_msg    VARCHAR(1000),
  retry_count  INT           DEFAULT 0,
  created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
