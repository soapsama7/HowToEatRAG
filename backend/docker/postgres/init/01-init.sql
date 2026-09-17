-- =====================================================================
-- 01-init.sql —— PostgreSQL 首次初始化脚本（数据卷为空时由 entrypoint 自动执行）
-- 目的：启用 pgvector（向量检索）与 zhparser（中文全文检索），并建立 zh_cn 检索配置
-- =====================================================================

-- 1) 扩展：向量（1024 维 embedding）+ 中文分词
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 2) 中文全文检索配置：基于 zhparser，映射词性
--    zhparser 合法 word type 见 ts_token_type('zhparser')，共 26 个单字母别名（不存在 vn/nz 这类组合别名）。
--    本配置挑选「有实义、对菜谱检索有用」的词性（其余如 u 助词 / d 副词 / w 标点 / r 代词 等作为噪音不映射）：
--      n 名词 / v 动词 / a 形容词  —— 内容主干
--      b 区别词（如「红烧」）/ i 成语 / l 习用语 / j 简称  —— 菜名、烹饪术语常落在这里，
--                                                             不映射会在 tsvector 中被丢弃，直接损害关键词召回
--      z 状态词
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'zh_cn') THEN
        CREATE TEXT SEARCH CONFIGURATION zh_cn (PARSER = zhparser);
        ALTER TEXT SEARCH CONFIGURATION zh_cn ADD MAPPING FOR n,v,a,b,i,l,j,z WITH simple;
        RAISE NOTICE 'zh_cn 检索配置已创建';
    ELSE
        RAISE NOTICE 'zh_cn 检索配置已存在，跳过创建';
    END IF;
END
$$;

-- 3) 初始化自检：配置必须存在、且分词结果必须包含菜名实词，否则直接报错暴露问题
--    （避免出现「扩展装了但 zh_cn 配置没建成」这类静默失败）
DO $$
DECLARE
    tsv tsvector;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_ts_config WHERE cfgname = 'zh_cn') THEN
        RAISE EXCEPTION 'zh_cn 检索配置创建失败';
    END IF;

    tsv := to_tsvector('zh_cn', '红烧鱼的做法');
    RAISE NOTICE 'zh_cn 分词自检: %', tsv;
    IF tsv::text NOT LIKE '%红烧%' OR tsv::text NOT LIKE '%鱼%' THEN
        RAISE EXCEPTION 'zh_cn 分词自检失败（菜名实词未进入 tsvector）: %', tsv;
    END IF;
END
$$;
