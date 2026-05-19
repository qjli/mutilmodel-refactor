package io.agentscope.demo.app.service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.session.Session;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.SessionIds;
import io.agentscope.demo.app.agent.AgentPrompts;
import io.agentscope.demo.app.agent.SkillLoader;
import io.agentscope.demo.app.upload.UploadMaterialCoverageStore;
import io.agentscope.demo.app.web.dto.FormVisionExtraction;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 多图证照视觉分析（SSE）：读 multipart 图片 → 流式 ReActAgent → 归一化 patch → 多主体歧义检测 → 推送 result。
 *
 * <p>事件类型见 {@link #sendJson}：{@code progress} / {@code thinking} / {@code assistant_text} / {@code result} /
 * {@code done} / {@code error}。{@code formContext} 为前端当前表单 JSON，用于多轮上传与「已填报」合并歧义。
 */
@Service
public class FormVisionStreamService {

    private static final Logger log = LoggerFactory.getLogger(FormVisionStreamService.class);

    private final Session agentscopeSession;
    private final DashScopeChatModel formVisionDashScopeChatModel;
    private final UploadMaterialCoverageStore uploadMaterialCoverageStore;

    public FormVisionStreamService(
            Session agentscopeSession,
            @Qualifier("formVisionDashScopeChatModel") DashScopeChatModel formVisionDashScopeChatModel,
            UploadMaterialCoverageStore uploadMaterialCoverageStore) {
        this.agentscopeSession = agentscopeSession;
        this.formVisionDashScopeChatModel = formVisionDashScopeChatModel;
        this.uploadMaterialCoverageStore = uploadMaterialCoverageStore;
    }

    /**
     * 在调用方线程（通常为 {@code agentscopeTaskExecutor}）中执行完整分析；通过 {@code emitter} 推送事件。
     *
     * <p>方法结束前会 {@code complete()} 或 {@code completeWithError()}，控制器线程不得阻塞于此。
     */
    public void runAnalysis(
            String sessionId, List<MultipartFile> files, String formContextJson, SseEmitter emitter) {
        final long t0 = System.nanoTime();
        String safeId = "";
        try {
            safeId = SessionIds.requireSafeSessionId(sessionId);
            List<MultipartFile> parts =
                    files.stream().filter(f -> f != null && !f.isEmpty()).toList();
            if (parts.isEmpty()) {
                sendJson(emitter, Map.of("type", "error", "message", "请至少选择一张非空图片"));
                emitter.complete();
                return;
            }

            List<String> names =
                    parts.stream()
                            .map(
                                    f ->
                                            f.getOriginalFilename() != null
                                                            && !f.getOriginalFilename().isBlank()
                                                    ? f.getOriginalFilename()
                                                    : "image")
                            .toList();
            int total = parts.size();
            log.info("[vision] start sessionId={} imageCount={}", safeId, total);

            List<byte[]> imageBytes = new ArrayList<>();
            List<String> mediaTypes = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                sendJson(
                        emitter,
                        Map.of(
                                "type",
                                "progress",
                                "phase",
                                "load_image",
                                "done",
                                i + 1,
                                "total",
                                total,
                                "label",
                                "读取第 " + (i + 1) + " 张 / 共 " + total + " 张",
                                "fileName",
                                names.get(i)));
                imageBytes.add(parts.get(i).getBytes());
                mediaTypes.add(mediaTypeFor(parts.get(i)));
            }

            // 按文件名关键词累计本会话已出现证照类型（非图像识别）
            uploadMaterialCoverageStore.mergeFromHints(safeId, names);

            sendJson(
                    emitter,
                    Map.of(
                            "type",
                            "progress",
                            "phase",
                            "infer",
                            "done",
                            total,
                            "total",
                            total,
                            "label",
                            "正在调用视觉模型…"));

            List<ContentBlock> blocks = new ArrayList<>();
            blocks.add(
                    TextBlock.builder()
                            .text(
                                    AgentPrompts.visionUserPreamble(
                                            total, String.join("、", names)))
                            .build());
            // 多图按顺序追加 ImageBlock（base64），与 DashScope 多模态消息格式一致
            for (int i = 0; i < imageBytes.size(); i++) {
                blocks.add(
                        ImageBlock.builder()
                                .source(
                                        Base64Source.builder()
                                                .data(
                                                        Base64.getEncoder()
                                                                .encodeToString(imageBytes.get(i)))
                                                .mediaType(mediaTypes.get(i))
                                                .build())
                                .build());
            }
            Msg userMsg = Msg.builder().role(MsgRole.USER).content(blocks).build();

            Toolkit toolkit = new Toolkit();
            SkillBox skillBox = new SkillBox(toolkit);
            skillBox.registerSkill(SkillLoader.formVisionFill());

            ReActAgent agent =
                    ReActAgent.builder()
                            .name("FormVisionAgent")
                            .sysPrompt(
                                    AgentPrompts.formVisionCore()
                                            + " 仅加载 form_vision_fill；upload_guide 恒为 null。")
                            .model(formVisionDashScopeChatModel)
                            .toolkit(toolkit)
                            .skillBox(skillBox)
                            .memory(new InMemoryMemory())
                            .maxIters(12)
                            .structuredOutputReminder(StructuredOutputReminder.PROMPT)
                            .build();

            agent.loadIfExists(agentscopeSession, safeId);

            // 订阅推理链与摘要流，供前端展示「思考中」与中间文本
            StreamOptions streamOpts =
                    StreamOptions.builder()
                            .eventTypes(
                                    EventType.REASONING,
                                    EventType.SUMMARY,
                                    EventType.AGENT_RESULT,
                                    EventType.HINT)
                            .incremental(true)
                            .includeReasoningChunk(true)
                            .includeReasoningResult(true)
                            .includeSummaryChunk(true)
                            .includeSummaryResult(true)
                            .build();

            AtomicReference<FormVisionExtraction> structured = new AtomicReference<>();

            agent.stream(List.of(userMsg), streamOpts, FormVisionExtraction.class)
                    .doOnNext(
                            event -> {
                                try {
                                    if (event.getType() == EventType.REASONING
                                            && event.getMessage() != null) {
                                        String delta = reasoningDeltaOrText(event.getMessage());
                                        if (!delta.isBlank()) {
                                            sendJson(
                                                    emitter,
                                                    Map.of("type", "thinking", "delta", delta));
                                        }
                                    }
                                    if ((event.getType() == EventType.SUMMARY
                                                    || event.getType() == EventType.AGENT_RESULT)
                                            && event.getMessage() != null) {
                                        String delta = textOrEmpty(event.getMessage());
                                        if (!delta.isBlank()) {
                                            sendJson(
                                                    emitter,
                                                    Map.of(
                                                            "type",
                                                            "assistant_text",
                                                            "delta",
                                                            delta));
                                        }
                                        if (event.getMessage().hasStructuredData()) {
                                            structured.set(
                                                    event.getMessage()
                                                            .getStructuredData(
                                                                    FormVisionExtraction.class));
                                        }
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .blockLast(Duration.ofMinutes(12));

            FormVisionExtraction extraction = structured.get();
            // 流结束未拿到结构化结果时，再同步 call 一次兜底
            if (extraction == null) {
                Msg block =
                        agent.call(userMsg, FormVisionExtraction.class).block(Duration.ofMinutes(8));
                if (block != null && block.hasStructuredData()) {
                    extraction = block.getStructuredData(FormVisionExtraction.class);
                }
            }
            if (extraction == null) {
                extraction = new FormVisionExtraction();
                extraction.reply = "模型未返回可用的结构化结果，请稍后重试或检查图片清晰度。";
            }

            extraction.uploadGuide = null; // 视觉链路不下发材料卡，由文本对话意图触发
            LinkedHashMap<String, Object> raw =
                    extraction.formPatch == null
                            ? new LinkedHashMap<>()
                            : new LinkedHashMap<>(extraction.formPatch);
            extraction.formPatch = FormVisionPatchNormalizer.normalize(raw);
            Map<String, Object> existingForm = FormVisionFormContextSupport.parseAndNormalize(formContextJson);
            // 多主体 / 跨轮冲突：可能清空 patch 并填充 ambiguities
            FormVisionMultiEntityConflictDetector.apply(extraction, raw, existingForm);

            HashMap<String, Object> result = new HashMap<>();
            result.put("type", "result");
            result.put("reply", extraction.reply != null ? extraction.reply : "");
            result.put(
                    "formPatch",
                    extraction.formPatch != null ? extraction.formPatch : Map.of());
            result.put(
                    "ambiguities",
                    extraction.ambiguities != null ? extraction.ambiguities : List.of());
            result.put("uploadGuide", null);
            result.put("multi_enterprise_conflict_applied", extraction.multiEnterpriseConflictApplied);
            result.put("multi_transport_conflict_applied", extraction.multiTransportConflictApplied);
            result.put("multi_safety_conflict_applied", extraction.multiSafetyConflictApplied);
            sendJson(emitter, result);

            agent.saveTo(agentscopeSession, safeId);
            sendJson(emitter, Map.of("type", "done"));
            emitter.complete();
            log.info("[vision] done sessionId={} totalMs={}", safeId, (System.nanoTime() - t0) / 1_000_000L);
        } catch (Exception e) {
            log.error("[vision] failed sessionId={}", safeId, e);
            try {
                sendJson(
                        emitter,
                        Map.of(
                                "type",
                                "error",
                                "message",
                                e.getMessage() != null ? e.getMessage() : e.toString()));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(e);
        }
    }

    private static String textOrEmpty(Msg msg) {
        if (msg == null) {
            return "";
        }
        String t = msg.getTextContent();
        return t != null ? t : "";
    }

    private static String reasoningDeltaOrText(Msg msg) {
        if (msg != null && msg.hasContentBlocks(ThinkingBlock.class)) {
            StringBuilder sb = new StringBuilder();
            for (ThinkingBlock tb : msg.getContentBlocks(ThinkingBlock.class)) {
                if (tb != null && tb.getThinking() != null && !tb.getThinking().isBlank()) {
                    sb.append(tb.getThinking());
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return textOrEmpty(msg);
    }

    /** 浏览器常上报 application/octet-stream，则按扩展名推断 image/jpeg 等。 */
    private static String mediaTypeFor(MultipartFile f) {
        String ct = f.getContentType();
        if (ct != null
                && !ct.isBlank()
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(ct)) {
            return ct;
        }
        String name = f.getOriginalFilename();
        if (name == null) {
            return "image/jpeg";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    /** SSE {@code data} 行：Spring 将 Map 序列化为 JSON，前端按 {@code type} 字段分发。 */
    private void sendJson(SseEmitter emitter, Map<String, ?> payload) throws IOException {
        emitter.send(SseEmitter.event().data(payload, MediaType.APPLICATION_JSON));
    }
}
