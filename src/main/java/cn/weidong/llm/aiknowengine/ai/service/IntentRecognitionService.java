package cn.weidong.llm.aiknowengine.ai.service;

import cn.weidong.llm.aiknowengine.ai.model.IntentRecognitionResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 意图识别服务
 *
 * @author Hollis
 */
public interface IntentRecognitionService {

    @SystemMessage(fromResource = "prompts/intent-recognition-new-prompt.txt")
    IntentRecognitionResult chat(@MemoryId String conversationId, @UserMessage String userMessage);
}
