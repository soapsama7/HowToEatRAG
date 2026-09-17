package com.anfioo.howtocook.common.mapper.user;

import com.anfioo.howtocook.common.entity.user.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * User Mapper（MyBatis-Plus 通用 CRUD）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
