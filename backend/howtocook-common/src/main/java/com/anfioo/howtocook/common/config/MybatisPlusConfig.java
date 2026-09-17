package com.anfioo.howtocook.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共配置：Mapper 扫描、分页 / 乐观锁插件、时间字段自动填充。
 * <p>Mapper 位于 common 模块、不在主应用包树下，必须显式 @MapperScan。</p>
 */
@Configuration
@MapperScan("com.anfioo.howtocook.common.mapper")
public class MybatisPlusConfig {

    /** 实体与 Mapper 统一在 common 模块 */
    public static final String MAPPER_PACKAGE = "com.anfioo.howtocook.common.mapper";

    /**
     * 插件链：分页（PostgreSQL 方言）+ 乐观锁（document.version）。
     * 顺序约定：分页插件需在乐观锁之前加入（官方建议最后加分页也可，这里按惯例先分页后乐观锁）。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /** created_at / updated_at 自动填充（配合实体 @TableField(fill = ...)） */
    @Bean
    public MetaObjectHandler auditFillMetaObjectHandler() {
        return new MetaObjectHandler() {

            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
