package cn.weidong.llm.aiknowengine.chat.service.impl;

import cn.weidong.llm.aiknowengine.chat.entity.ChatMessage;
import cn.weidong.llm.aiknowengine.chat.mapper.ChatMessageMapper;
import cn.weidong.llm.aiknowengine.chat.service.ChatMessageService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChatMessageServiceImpl
        extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {

    @Override
    public boolean updateTransformContent(String messageId, String transformContent) {
        if (!StringUtils.hasText(messageId)) {
            return false;
        }
        return update(new LambdaUpdateWrapper<ChatMessage>()
                .eq(ChatMessage::getMessageId, messageId)
                .set(ChatMessage::getTransformContent, transformContent));
    }
}
