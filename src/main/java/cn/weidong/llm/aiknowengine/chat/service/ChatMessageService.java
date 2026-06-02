package cn.weidong.llm.aiknowengine.chat.service;

import cn.weidong.llm.aiknowengine.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    String saveUserMessage(String conversationId, String content);

    String saveAssistantMessage(String conversationId);

    boolean updateTransformContent(String messageId, String transformContent);

    boolean updateContent(String messageId, String content);

    boolean updateRagReferences(String messageId, List<ChatMessage.RagReference> ragReferenceChunks);

    boolean deleteMessagesByConversationId(String conversationId);

    List<ChatMessage> getRecentMessages(String conversationId, int limit);
}
