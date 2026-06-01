package cn.weidong.llm.aiknowengine.chat.service;

import cn.weidong.llm.aiknowengine.chat.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ChatMessageService extends IService<ChatMessage> {

    boolean updateTransformContent(String messageId, String transformContent);
}
