package com.anfioo.howtocook.common.mapper.doc;

import com.anfioo.howtocook.common.entity.doc.DocumentChunk;
import com.anfioo.howtocook.common.retrieval.ChunkResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pgvector.PGvector;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * DocumentChunk Mapper（MyBatis-Plus 通用 CRUD + 混合检索两路原生召回 SQL）。
 * <p>召回 SQL 与开发文档 §5.3 一致：仅命中 READY 且未删除文档、chunk.version = document.version。
 * 列别名按驼峰映射到 {@link ChunkResult}（依赖 MyBatis-Plus 默认 mapUnderscoreToCamelCase）。</p>
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {

    /** 向量召回：余弦距离转相似度分（score = 1 - distance），近邻优先 */
    @Select("""
            SELECT c.id AS chunk_id, c.doc_id, d.title, c.section, c.content,
                   1 - (c.embedding <=> #{embedding,typeHandler=com.anfioo.howtocook.common.typehandler.VectorTypeHandler}::vector) AS score
            FROM document_chunk c
            JOIN document d ON d.id = c.doc_id AND d.status = 'READY' AND d.deleted = 0
            WHERE c.version = d.version
            ORDER BY c.embedding <=> #{embedding,typeHandler=com.anfioo.howtocook.common.typehandler.VectorTypeHandler}::vector
            LIMIT #{limit}
            """)
    List<ChunkResult> vectorRecall(@Param("embedding") PGvector embedding, @Param("limit") int limit);

    /** 关键词召回：zh_cn（zhparser）分词全文匹配，ts_rank 相关性排序 */
    @Select("""
            SELECT c.id AS chunk_id, c.doc_id, d.title, c.section, c.content,
                   ts_rank(c.content_tsv, plainto_tsquery('zh_cn', #{query})) AS score
            FROM document_chunk c
            JOIN document d ON d.id = c.doc_id AND d.status = 'READY' AND d.deleted = 0
            WHERE c.version = d.version
              AND c.content_tsv @@ plainto_tsquery('zh_cn', #{query})
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<ChunkResult> keywordRecall(@Param("query") String query, @Param("limit") int limit);
}
