package cn.weidong.llm.aiknowengine.chat.entity;


import cn.weidong.llm.aiknowengine.ai.model.IntentRecognitionResult;

public record ChatParam(String userId, String conversationId, String messageId, String content, String assistantMessageId,
                        IntentRecognitionResult intentRecognitionResult) {
}
