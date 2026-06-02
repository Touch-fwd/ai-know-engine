package cn.weidong.llm.aiknowengine.chat.service.impl;

import cn.weidong.llm.aiknowengine.chat.entity.ChatConversation;
import cn.weidong.llm.aiknowengine.chat.mapper.ChatConversationMapper;
import cn.weidong.llm.aiknowengine.chat.service.ChatConversationService;
import cn.weidong.llm.aiknowengine.infra.snowflake.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
public class ChatConversationServiceImpl
        extends ServiceImpl<ChatConversationMapper, ChatConversation>
        implements ChatConversationService {

    private static final String DEFAULT_TITLE = "新会话";
    private static final String ACTIVE_STATUS = "active";

    @Override
    public String createConversation(String userId, String title) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }

        String conversationId = SnowflakeIdGenerator.getInstance().nextIdStr();

        ChatConversation conversation = new ChatConversation();
        conversation.setConversationId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(title) ? title : DEFAULT_TITLE);
        conversation.setStatus(ACTIVE_STATUS);
        save(conversation);

        return conversationId;
    }

    @Override
    public boolean updateTitle(String conversationId, String title) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(title)) {
            return false;
        }
        return update(new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .set(ChatConversation::getTitle, title));
    }

    @Override
    public List<ChatConversation> getConversationsByUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getUserId, userId)
                .orderByDesc(ChatConversation::getUpdatedAt));
    }

    @Override
    public ChatConversation getByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId)
                .last("LIMIT 1"));
    }

    @Override
    public boolean deleteByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        return remove(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getConversationId, conversationId));
    }
}
