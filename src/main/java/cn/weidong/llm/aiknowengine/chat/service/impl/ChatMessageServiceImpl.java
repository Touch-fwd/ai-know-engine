package cn.weidong.llm.aiknowengine.chat.service.impl;

import cn.weidong.llm.aiknowengine.chat.entity.ChatMessage;
import cn.weidong.llm.aiknowengine.chat.mapper.ChatMessageMapper;
import cn.weidong.llm.aiknowengine.chat.service.ChatMessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageServiceImpl
        extends ServiceImpl<ChatMessageMapper, ChatMessage>
        implements ChatMessageService {
}
