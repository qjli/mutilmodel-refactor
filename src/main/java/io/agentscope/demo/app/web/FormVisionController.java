package io.agentscope.demo.app.web;

import io.agentscope.demo.app.service.FormVisionStreamService;
import java.util.List;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 多图 + 表单结构化识别：以 SSE（{@code text/event-stream}）向客户端推送进度、思考片段与最终结果。
 *
 * <p>请求体为 {@code multipart/form-data}，字段名 {@code files} 可重复多次表示多图；服务端在独立线程中执行
 * {@link FormVisionStreamService#runAnalysis}，避免阻塞 Tomcat 工作线程。
 *
 * <p>控制器挂在 {@code /api/sessions} 下，与会话资源 URL 习惯一致。
 */
@RestController
@RequestMapping("/api/sessions")
public class FormVisionController {

    private static final Logger log = LoggerFactory.getLogger(FormVisionController.class);

    /**
     * 若连接长期不完成，SseEmitter 到点会抛超时异常，避免线程与句柄泄漏；30 分钟与一次多图分析的上限匹配。
     */
    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

    /** 真正干活的业务类：读图、调模型、往 emitter 里推事件。 */
    private final FormVisionStreamService formVisionStreamService;
    /**
     * 专用于本接口的线程池：下面把重活 {@code runAnalysis} 丢进池里，当前 Servlet 线程只负责「立刻把
     * SseEmitter 返回给浏览器」。
     */
    private final Executor agentscopeTaskExecutor;

    public FormVisionController(
            FormVisionStreamService formVisionStreamService,
            @Qualifier("agentscopeTaskExecutor") Executor agentscopeTaskExecutor) {
        this.formVisionStreamService = formVisionStreamService;
        this.agentscopeTaskExecutor = agentscopeTaskExecutor;
    }

    /**
     * 上传多张图片并流式返回 JSON 事件（{@code data: {...}\\n\\n}）：{@code progress}、{@code thinking}、
     * {@code assistant_text}、{@code result}、{@code done}、{@code error}。
     */
    @PostMapping(
            value = "/{sessionId}/vision/form-stream",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter visionFormStream(
            @PathVariable String sessionId,
            // 与前端 multipart 字段名一致；多图时 Spring 会捆成 List（顺序通常与表单字段顺序一致）
            @RequestPart("files") List<MultipartFile> files) {
        int n = files == null ? 0 : files.size();
        log.info("[vision-sse] accepted pathSessionId={} multipartPartCount={}", sessionId, n);
        // 先创建连接对象并设置超时；此时尚未向客户端写任何事件，浏览器已拿到 HTTP 200 + text/event-stream
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 禁止在本方法内同步调用 runAnalysis：内部有 Flux.blockLast，会阻塞很久，拖死 Tomcat 线程
        agentscopeTaskExecutor.execute(() -> formVisionStreamService.runAnalysis(sessionId, files, emitter));
        // 立即返回，长任务在后台跑；后续事件全部由 FormVisionStreamService 写入同一 emitter
        return emitter;
    }
}
