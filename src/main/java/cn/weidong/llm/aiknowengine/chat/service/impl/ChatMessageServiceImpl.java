package cn.weidong.llm.aiknowengine.chat.service.impl;

import cn.weidong.llm.aiknowengine.chat.constants.ChatMessageType;
import cn.weidong.llm.aiknowengine.chat.entity.ChatMessage;
import cn.weidong.llm.aiknowengine.chat.mapper.ChatMessageMapper;
import cn.weidong.llm.aiknowengine.chat.service.ChatMessageService;
import cn.weidong.llm.aiknowengine.infra.snowflake.SnowflakeIdGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class ChatMessageServiceImpl
        extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {

    @Override
    public String saveUserMessage(String conversationId, String content) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }

        return saveMessage(conversationId, ChatMessageType.USER, content);
    }

    @Override
    public String saveAssistantMessage(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId must not be blank");
        }

        return saveMessage(conversationId, ChatMessageType.ASSISTANT, null);
    }

    @Override
    public boolean updateTransformContent(String messageId, String transformContent) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        return update(new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId)
                .set(ChatMessage::getTransformContent, transformContent));
    }

    @Override
    public boolean updateContent(String messageId, String content) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        return update(new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId)
                .set(ChatMessage::getContent, content));
    }

    @Override
    public boolean updateRagReferences(String messageId, List<ChatMessage.RagReference> ragReferenceChunks) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        return update(new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId)
                .set(ChatMessage::getRagReferences, ragReferenceChunks));
    }

    @Override
    public boolean deleteMessagesByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return false;
        }
        return remove(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId));
    }

    @Override
    public List<ChatMessage> getMessagesByConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreatedAt));
    }

    @Override
    public List<ChatMessage> getRecentMessages(String conversationId, int limit) {
        if (!StringUtils.hasText(conversationId) || limit <= 0) {
            return Collections.emptyList();
        }

        List<ChatMessage> messages = list(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getCreatedAt)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + limit));

        List<ChatMessage> orderedMessages = new ArrayList<>(messages);
        Collections.reverse(orderedMessages);
        return orderedMessages;
    }

    private String saveMessage(String conversationId, ChatMessageType type, String content) {
        String messageId = SnowflakeIdGenerator.getInstance().nextIdStr();

        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setConversationId(conversationId);
        message.setType(type);
        message.setContent(content);
        save(message);

        return messageId;
    }
}
