package com.anfioo.howtocook.common.mapper.doc;

import com.anfioo.howtocook.common.entity.doc.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Document Mapper（MyBatis-Plus 通用 CRUD + 回收站专用 SQL）。
 * <p>回收站操作需绕过 {@code @TableLogic}（逻辑删文档对常规查询不可见），
 * 故移入/恢复/物理删除/回收站查询均用手写 SQL 显式携带 deleted 条件。</p>
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    /** 移入回收站：逻辑删 + 记录删除时间（updated_at 同步刷新，未删行才生效） */
    @Update("UPDATE document SET deleted = 1, deleted_at = now(), updated_at = now() "
            + "WHERE id = #{id} AND deleted = 0")
    int moveToRecycleBin(@Param("id") long id);

    /** 从回收站恢复：反逻辑删 + 清空删除时间（仅已删行生效） */
    @Update("UPDATE document SET deleted = 0, deleted_at = NULL "
            + "WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") long id);

    /** 彻底清除：物理删行（仅回收站中的行生效） */
    @Delete("DELETE FROM document WHERE id = #{id} AND deleted = 1")
    int purgeById(@Param("id") long id);

    /** 查回收站中的文档（绕过逻辑删过滤；purge 前取 objectKey 用） */
    @Select("SELECT * FROM document WHERE id = #{id} AND deleted = 1")
    Document selectDeletedById(@Param("id") long id);

    /** 回收站分页列表（按删除时间倒序；分页由 MP 拦截器改写） */
    @Select("SELECT * FROM document WHERE deleted = 1 ORDER BY deleted_at DESC")
    Page<Document> selectRecycleBin(Page<Document> page);

    /** 定时清理：查超过保留期的过期文档（每轮限量，防止单次任务过重） */
    @Select("SELECT * FROM document WHERE deleted = 1 AND deleted_at IS NOT NULL AND deleted_at < #{threshold} "
            + "ORDER BY deleted_at LIMIT 200")
    List<Document> selectExpired(@Param("threshold") LocalDateTime threshold);

    /**
     * 按内容哈希查文档（上传查重用，R3 返工）：手写 SQL 绕过 @TableLogic，
     * 覆盖包括回收站在内的所有未物理删除条目——否则「删除→重传→恢复」会产生重复文档。
     */
    @Select("SELECT * FROM document WHERE content_hash = #{contentHash} ORDER BY id LIMIT 1")
    Document selectByContentHash(@Param("contentHash") String contentHash);
}
