package io.agentscope.demo.app.web;

import io.agentscope.demo.app.service.DemoChatService;
import io.agentscope.demo.app.web.dto.ChatRequest;
import io.agentscope.demo.app.web.dto.ChatResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话内文本对话：调用 AgentScope {@link io.agentscope.core.ReActAgent} 与 DashScope 文本模型，返回回复及可选表单补丁。
 *
 * <p>{@code sessionId} 会先经 {@link io.agentscope.demo.SessionIds#requireSafeSessionId(String)} 规范化，避免路径穿越。
 */
@RestController
@RequestMapping("/api/sessions")
@Validated
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final DemoChatService demoChatService;

    public ChatController(DemoChatService demoChatService) {
        this.demoChatService = demoChatService;
    }

    /** 提交一轮用户文本，阻塞直至模型返回（或超时/异常在业务层被消化为错误文案）。 */
    @PostMapping("/{sessionId}/messages")
    public ChatResponse send(
            @PathVariable String sessionId, @Valid @RequestBody ChatRequest body) {
        int len = body.content() == null ? 0 : body.content().length();
        log.info("[chat-http] POST messages pathSessionId={} contentChars={}", sessionId, len);
        return demoChatService.chat(sessionId, body);
    }
}
