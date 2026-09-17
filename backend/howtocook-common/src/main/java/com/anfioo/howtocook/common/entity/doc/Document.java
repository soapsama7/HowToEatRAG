package com.anfioo.howtocook.common.entity.doc;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档（菜谱/技巧）元数据表，对应 {@code document}。
 * <p>原始 markdown 本体存 RustFS（object_key 引用），本表只存元数据；
 * Agent 检索仅命中 status='READY' 且未删除的文档（检索 SQL JOIN 保证）。</p>
 */
@Data
@TableName("document")
public class Document {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 菜谱名，如 "红烧鱼"（H1 去掉"的做法"后缀） */
    private String title;

    /** 文档类型：RECIPE / TIP / OTHER（见 DocType） */
    private String docType;

    /** 分类：目录名映射（aquatic→水产 ... tips→技巧） */
    private String category;

    /** RustFS 对象键（服务端生成 uuid.md，防路径穿越） */
    private String objectKey;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 难度星级 1-5（从 ★ 解析） */
    private Integer difficulty;

    /** 预计时长（分钟） */
    private Integer cookMinutes;

    /** 预估卡路里（大卡） */
    private Integer calories;

    /** 版本号：乐观锁 + 重索引版本一致性（version+1 重索引，成功后删旧 version chunk） */
    @Version
    private Integer version;

    /** 索引状态：PENDING / INDEXING / READY / FAILED（见 DocStatus） */
    private String status;

    /** 索引失败原因 */
    private String errorMsg;

    /** chunk 数量 */
    private Integer chunkCount;

    /** 上传者用户 ID */
    private Long uploaderId;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0 未删 1 已删 */
    @TableLogic
    private Integer deleted;
}
