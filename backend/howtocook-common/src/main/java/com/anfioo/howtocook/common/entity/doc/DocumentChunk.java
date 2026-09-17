package com.anfioo.howtocook.common.entity.doc;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pgvector.PGvector;
import com.anfioo.howtocook.common.typehandler.VectorTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档 chunk 表，对应 {@code document_chunk}（检索的基本单位）。
 * <p>embedding 为 1024 维向量（qwen3.7-text-embedding）；content_tsv 为生成列（zh_cn 分词），
 * 由数据库自动维护，实体不映射、不可写。</p>
 */
@Data
@TableName(value = "document_chunk", autoResultMap = true)
public class DocumentChunk {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属文档 ID */
    private Long docId;

    /** 冗余 document.version，用于版本一致性判断（检索 SQL 仅取 chunk.version = document.version） */
    private Integer version;

    /** 文档内序号（全局递增） */
    private Integer chunkIndex;

    /** 所属 H2 章节名（"必备原料和工具" / "操作" ...） */
    private String section;

    /** chunk 纯文本（清洗后） */
    private String content;

    /** token 数（估算） */
    private Integer tokenCount;

    /** 1024 维向量（pgvector） */
    @TableField(typeHandler = VectorTypeHandler.class)
    private PGvector embedding;

    /** 创建时间（自动填充） */
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
