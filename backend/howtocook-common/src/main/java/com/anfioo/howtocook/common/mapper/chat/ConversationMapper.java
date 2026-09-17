package com.anfioo.howtocook.common.mapper.chat;

import com.anfioo.howtocook.common.entity.chat.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Conversation Mapper（MyBatis-Plus 通用 CRUD）。
 */
@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}
