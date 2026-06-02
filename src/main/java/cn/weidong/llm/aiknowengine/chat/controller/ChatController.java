package cn.weidong.llm.aiknowengine.chat.controller;

import cn.weidong.llm.aiknowengine.ai.service.CommonChatService;
import cn.weidong.llm.aiknowengine.ai.service.IntentRecognitionService;
import cn.weidong.llm.aiknowengine.ai.service.TitleSummaryService;
import cn.weidong.llm.aiknowengine.chat.entity.ChatConversation;
import cn.weidong.llm.aiknowengine.chat.entity.ChatMessage;
import cn.weidong.llm.aiknowengine.chat.entity.ChatParam;
import cn.weidong.llm.aiknowengine.chat.memory.DatabaseChatMemoryStore;
import cn.weidong.llm.aiknowengine.chat.service.ChatApplicationService;
import cn.weidong.llm.aiknowengine.chat.service.ChatConversationService;
import cn.weidong.llm.aiknowengine.chat.service.ChatMessageService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String chatModelApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String chatModelBaseUrl;

    @Value("${langchain4j.open-ai.title-chat-model.model-name}")
    private String titleChatModelName;

    @Value("${langchain4j.open-ai.title-chat-model.temperature}")
    private Double titleChatModelTemperature;

    @Value("${langchain4j.open-ai.title-chat-model.enable-thinking}")
    private Boolean titleChatModelEnableThinking;

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatConversationService chatConversationService;
    private final ChatMessageService chatMessageService;
    private final ChatModel chatModel;
    private IntentRecognitionService intentRecognitionService;

    @Autowired
    private DatabaseChatMemoryStore databaseChatMemoryStore;

    @Autowired
    private ChatApplicationService chatApplicationService;

    @Autowired
    private CommonChatService commonChatService;

    public ChatController(ChatConversationService chatConversationService,
                          ChatMessageService chatMessageService,
                          ChatModel chatModel) {
        this.chatConversationService = chatConversationService;
        this.chatMessageService = chatMessageService;
        this.chatModel = chatModel;
    }

    @PostConstruct
    public void init() {
        intentRecognitionService = AiServices.builder(IntentRecognitionService.class).chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder().id(memoryId).maxMessages(10).chatMemoryStore(databaseChatMemoryStore).build()).build();
    }

    /**
     * 流式对话接口
     * <p>
     * 入参：JSON body，包含 userId、content（用户问题）、conversationId（可选）
     * 返回：SSE 流，每个 token 逐字推送；流结束前推送一条 [DONE] 事件携带 conversationId
     * <p>
     * 进度通知格式：{@code [PROGRESS]:xxx...}，用于在前端展示当前处理阶段，减少等待焦虑。
     * 推送环节包括：意图识别、问题改写、问题路由、排序筛选、生成回答等。
     *
     * @param request 流式对话请求
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> send(
            @RequestBody ChatStreamRequest request) {
        String userId = request == null ? null : request.userId();
        String content = request == null ? null : request.content();
        String conversationId = request == null ? null : request.conversationId();
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(content)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId 和 content 不能为空");
        }

        // 1. 处理会话：没有 conversationId 则创建新会话
        final String finalConversationId;
        if (conversationId == null || conversationId.isBlank()) {

            // 同步：先用 content 前 20 个字符作为临时标题，快速建会话
            String tempTitle = content.substring(0, Math.min(content.length(), 20));
            finalConversationId = chatConversationService.createConversation(userId, tempTitle);
            log.info("创建新会话: conversationId={}, tempTitle={}", finalConversationId, tempTitle);

            // 异步：用虚拟线程调用 LLM 生成摘要标题，完成后回写到数据库
            Thread.ofVirtual().name("title-summary-" + finalConversationId).start(() -> {
                try {
                    OpenAiChatModel titleChatModel = OpenAiChatModel.builder()
                            .apiKey(chatModelApiKey)
                            .modelName(titleChatModelName)
                            .temperature(titleChatModelTemperature)
                            .baseUrl(chatModelBaseUrl)
                            .customParameters(Map.of("enable_thinking", titleChatModelEnableThinking))
                            .build();
                    TitleSummaryService titleSummaryService = AiServices.builder(TitleSummaryService.class)
                            .chatModel(titleChatModel)
                            .build();
                    String aiTitle = titleSummaryService.generateTitle(content);
                    chatConversationService.updateTitle(finalConversationId, aiTitle);
                    log.info("异步标题更新完成: conversationId={}, title={}", finalConversationId, aiTitle);
                } catch (Exception e) {
                    log.warn("异步标题生成失败, 保留临时标题: conversationId={}", finalConversationId, e);
                }
            });
        } else {
            finalConversationId = conversationId;
        }

        // 2. 保存用户消息
        String messageId = chatMessageService.saveUserMessage(finalConversationId, content);
        String assistantMessageId = chatMessageService.saveAssistantMessage(finalConversationId);

        // 清除该会话的内存缓存，确保从DB重新加载最新消息（含刚保存的用户消息）
        databaseChatMemoryStore.evictCache(finalConversationId);

        // 3. 流式返回：先发送意图识别进度，再执行意图识别
        //    使用 Mono.fromCallable + subscribeOn(boundedElastic) 将阻塞调用移到弹性线程池，
        //    释放 WebFlux 事件循环，确保进度消息能立即 flush 到前端
        Flux<String> chatStream = Flux.just("[PROGRESS]:正在识别您的意图...")
                .concatWith(
                        Mono.fromCallable(() -> intentRecognitionService.chat(finalConversationId, content))
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMapMany(intentRecognitionResult -> {
                                    // 意图识别完成后清除缓存，避免意图识别的AI响应污染后续RAG对话的历史记忆
                                    databaseChatMemoryStore.evictCache(finalConversationId);

                                    // 4. 如果用户问题不相关，使用一个通用的LLM做对话
                                    if (!intentRecognitionResult.related()) {
                                        StringBuilder contentBuilder = new StringBuilder();
                                        return Flux.concat(
                                                Flux.just("[PROGRESS]:正在为您生成回答..."),
                                                commonChatService.streamChat(userId, content)
                                                        .doOnNext(token -> {
                                                            contentBuilder.append(token);
                                                        })
                                                        .doOnComplete(() -> chatMessageService.updateContent(assistantMessageId, contentBuilder.toString()))
                                        );
                                    }

                                    // 5. 相关问题，走RAG流程（进度由内部组件发出）
                                    return chatApplicationService.chat(new ChatParam(userId, finalConversationId, messageId, content, assistantMessageId, intentRecognitionResult));
                                })
                )
                .doOnError(e -> log.error("流式对话异常: conversationId={}", finalConversationId, e));

        return Flux.just(toSse("conversation", "{\"conversationId\":\"" + finalConversationId + "\"}"))
                .concatWith(chatStream.map(this::toChatSse))
                .concatWith(Mono.just(toSse("done", finalConversationId)));
    }

    private String toChatSse(String data) {
        if (data != null && data.startsWith("[PROGRESS]:")) {
            return toSse("progress", data.substring("[PROGRESS]:".length()));
        }
        return toSse("delta", data);
    }

    private String toSse(String event, String data) {
        String safeData = data == null ? "" : data.replace("\r", "");
        StringBuilder builder = new StringBuilder("event: ").append(event).append('\n');
        for (String line : safeData.split("\n", -1)) {
            builder.append("data: ").append(line).append('\n');
        }
        return builder.append('\n').toString();
    }

    public record ChatStreamRequest(String userId, String content, String conversationId) {
    }

    @GetMapping("/conversations")
    public List<ChatConversation> conversations(@RequestParam String userId) {
        return chatConversationService.getConversationsByUserId(userId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessage> messages(@PathVariable String conversationId) {
        ChatConversation conversation = chatConversationService.getByConversationId(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + conversationId);
        }
        return chatMessageService.getMessagesByConversationId(conversationId);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@PathVariable String conversationId) {
        ChatConversation conversation = chatConversationService.getByConversationId(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + conversationId);
        }
        chatMessageService.deleteMessagesByConversationId(conversationId);
        chatConversationService.deleteByConversationId(conversationId);
    }
}
