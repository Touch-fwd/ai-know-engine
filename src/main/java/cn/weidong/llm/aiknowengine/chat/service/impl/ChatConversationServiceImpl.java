package cn.weidong.llm.aiknowengine.chat.service.impl;

import cn.weidong.llm.aiknowengine.chat.entity.ChatConversation;
import cn.weidong.llm.aiknowengine.chat.mapper.ChatConversationMapper;
import cn.weidong.llm.aiknowengine.chat.service.ChatConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ChatConversationServiceImpl
        extends ServiceImpl<ChatConversationMapper, ChatConversation>
        implements ChatConversationService {
}
