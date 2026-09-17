package com.anfioo.howtocook.common.mapper.chat;

import com.anfioo.howtocook.common.entity.chat.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Message Mapper（MyBatis-Plus 通用 CRUD）。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
