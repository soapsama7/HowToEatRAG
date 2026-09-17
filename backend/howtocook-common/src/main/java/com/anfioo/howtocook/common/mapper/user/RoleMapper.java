package com.anfioo.howtocook.common.mapper.user;

import com.anfioo.howtocook.common.entity.user.Role;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Role Mapper（MyBatis-Plus 通用 CRUD）。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
