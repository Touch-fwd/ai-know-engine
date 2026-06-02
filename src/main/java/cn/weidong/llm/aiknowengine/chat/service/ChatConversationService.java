package cn.weidong.llm.aiknowengine.chat.service;

import cn.weidong.llm.aiknowengine.chat.entity.ChatConversation;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ChatConversationService extends IService<ChatConversation> {

    String createConversation(String userId, String title);

    boolean updateTitle(String conversationId, String title);
}
