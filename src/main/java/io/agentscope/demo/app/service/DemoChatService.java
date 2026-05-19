package io.agentscope.demo.app.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.SessionIds;
import io.agentscope.demo.app.agent.AgentPrompts;
import io.agentscope.demo.app.agent.ChatIntents;
import io.agentscope.demo.app.agent.SkillLoader;
import io.agentscope.demo.app.upload.UploadGuideFactory;
import io.agentscope.demo.app.upload.UploadMaterialCoverageStore;
import io.agentscope.demo.app.web.dto.ChatFormAssistantResult;
import io.agentscope.demo.app.web.dto.ChatRequest;
import io.agentscope.demo.app.web.dto.ChatResponse;
import io.agentscope.demo.app.web.dto.UploadGuideDto;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DemoChatService {

    private static final Logger log = LoggerFactory.getLogger(DemoChatService.class);

    private final JsonSession jsonSession;
    private final DashScopeChatModel chatDashScopeChatModel;
    private final UploadMaterialCoverageStore uploadMaterialCoverageStore;

    public DemoChatService(
            JsonSession jsonSession,
            @Qualifier("chatDashScopeChatModel") DashScopeChatModel chatDashScopeChatModel,
            UploadMaterialCoverageStore uploadMaterialCoverageStore) {
        this.jsonSession = jsonSession;
        this.chatDashScopeChatModel = chatDashScopeChatModel;
        this.uploadMaterialCoverageStore = uploadMaterialCoverageStore;
    }

    public ChatResponse chat(String sessionId, ChatRequest request) {
        String safeId = SessionIds.requireSafeSessionId(sessionId);
        String text = request.content().trim();
        final long t0 = System.nanoTime();

        final boolean uploadGuideIntent = ChatIntents.uploadGuide(text);
        final boolean remainingFilesIntent = ChatIntents.remainingFiles(text);

        log.info("[chat] start sessionId={} contentChars={}", safeId, text.length());

        try {
            Toolkit toolkit = new Toolkit();
            SkillBox skillBox = new SkillBox(toolkit);
            skillBox.registerSkill(SkillLoader.formVisionFill());
            if (uploadGuideIntent) {
                skillBox.registerSkill(SkillLoader.uploadGuideDialog());
            }

            String sysPrompt = AgentPrompts.chatStructuredIntro();
            if (uploadGuideIntent) {
                Set<String> coverage = uploadMaterialCoverageStore.readMerged(safeId);
                sysPrompt +=
                        remainingFilesIntent
                                ? AgentPrompts.chatUploadGuideRemaining(coverage)
                                : AgentPrompts.chatUploadGuideGeneral();
            } else {
                sysPrompt += AgentPrompts.chatNoUploadGuide();
            }

            ReActAgent agent =
                    ReActAgent.builder()
                            .name("ConsoleChatAgent")
                            .sysPrompt(sysPrompt)
                            .model(chatDashScopeChatModel)
                            .toolkit(toolkit)
                            .skillBox(skillBox)
                            .memory(new InMemoryMemory())
                            .maxIters(8)
                            .structuredOutputReminder(StructuredOutputReminder.PROMPT)
                            .build();

            agent.loadIfExists(jsonSession, safeId);

            Msg response =
                    agent.call(
                                    Msg.builder()
                                            .role(MsgRole.USER)
                                            .content(TextBlock.builder().text(text).build())
                                            .build(),
                                    ChatFormAssistantResult.class)
                            .block(Duration.ofMinutes(3));
            agent.saveTo(jsonSession, safeId);

            if (response == null) {
                return new ChatResponse("模型未返回内容，请稍后重试。", null, Instant.now(), null);
            }

            ChatFormAssistantResult structured =
                    response.hasStructuredData()
                            ? response.getStructuredData(ChatFormAssistantResult.class)
                            : null;

            String reply =
                    structured != null && structured.reply != null && !structured.reply.isBlank()
                            ? structured.reply
                            : response.getTextContent();
            if (reply == null || reply.isBlank()) {
                reply = "（无文本回复）";
            }

            Map<String, Object> patch = null;
            if (structured != null
                    && structured.formPatch != null
                    && !structured.formPatch.isEmpty()) {
                patch =
                        FormVisionPatchNormalizer.normalize(
                                new LinkedHashMap<>(structured.formPatch));
            }

            UploadGuideDto uploadGuide = resolveUploadGuide(uploadGuideIntent, remainingFilesIntent, safeId);

            log.info(
                    "[chat] ok sessionId={} elapsedMs={} patchKeys={} uploadGuide={}",
                    safeId,
                    (System.nanoTime() - t0) / 1_000_000L,
                    patch != null ? patch.size() : 0,
                    uploadGuide != null);

            return new ChatResponse(reply, patch, Instant.now(), uploadGuide);
        } catch (IOException e) {
            log.error("[chat] skill load failed sessionId={}", safeId, e);
            return new ChatResponse("服务初始化失败：无法加载技能资源。", null, Instant.now(), null);
        } catch (Exception e) {
            log.error("[chat] failed sessionId={}", safeId, e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new ChatResponse("调用语言模型失败：" + detail, null, Instant.now(), null);
        }
    }

    private UploadGuideDto resolveUploadGuide(
            boolean uploadGuideIntent, boolean remainingFilesIntent, String safeId) {
        if (!uploadGuideIntent) {
            return null;
        }
        if (remainingFilesIntent) {
            return UploadGuideFactory.remaining(uploadMaterialCoverageStore.readMerged(safeId));
        }
        return UploadGuideFactory.fourSamples();
    }
}
