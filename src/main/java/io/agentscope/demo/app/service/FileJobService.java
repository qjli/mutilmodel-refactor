package io.agentscope.demo.app.service;

import io.agentscope.demo.app.web.dto.FileAnalyzeResponse;
import io.agentscope.demo.app.web.dto.FileJobStatusResponse;
import io.agentscope.demo.app.web.dto.ParseStepView;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 单文件「解析任务」的内存模拟：用于演示上传后轮询进度条，与 AgentScope 视觉主链路解耦。
 *
 * <p>任务仅存在于 JVM 内，重启即丢失；生产应换为持久化队列或真实解析流水线。
 */
@Service
public class FileJobService {

    private static final Logger log = LoggerFactory.getLogger(FileJobService.class);

    /** 前端步骤条展示的固定步骤名称（与进度百分比解耦）。 */
    private static final List<String> STEP_NAMES =
            List.of("排队", "读取文件", "解析结构", "写入缓存", "完成");

    private final ConcurrentHashMap<String, FileJob> jobs = new ConcurrentHashMap<>();
    /** 单线程调度：按延迟触发各阶段进度更新，避免并发写同一 Job 状态。 */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "file-job-sim");
                t.setDaemon(true);
                return t;
            });

    /**
     * 消费上传流（丢弃内容）、登记任务并按时间表推进模拟进度。
     *
     * @throws IllegalArgumentException 空文件
     */
    public FileAnalyzeResponse start(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择一个文件");
        }
        String jobId = UUID.randomUUID().toString();
        String fileName =
                file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                        ? "upload.bin"
                        : file.getOriginalFilename();
        long size = file.getSize();
        try (var in = file.getInputStream()) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        FileJob job = new FileJob(jobId, fileName, size);
        jobs.put(jobId, job);
        log.info("[file-job] registered jobId={} fileName={} sizeBytes={}", jobId, fileName, size);
        scheduler.schedule(() -> job.update(20, "reading", 1), 300, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> job.update(55, "parsing", 2), 900, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> job.update(85, "parsing", 3), 1600, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> job.update(100, "done", 4), 2300, TimeUnit.MILLISECONDS);
        return new FileAnalyzeResponse(jobId, fileName, size);
    }

    /** 返回当前快照；未知 {@code jobId} 时抛 {@link IllegalArgumentException} → 400。 */
    public FileJobStatusResponse status(String jobId) {
        FileJob job = jobs.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("任务不存在或已过期");
        }
        return job.toView();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    /** 单个模拟任务的可变状态与步骤视图重建逻辑。 */
    private static final class FileJob {
        private final String id;
        private final String fileName;
        private final long sizeBytes;
        private final List<ParseStepView> steps = new ArrayList<>();
        private volatile int percent;
        private volatile String phase = "queued";

        FileJob(String id, String fileName, long sizeBytes) {
            this.id = id;
            this.fileName = fileName;
            this.sizeBytes = sizeBytes;
            rebuildSteps(0, 0);
        }

        synchronized void update(int percent, String phase, int activeIndex) {
            this.percent = percent;
            this.phase = phase;
            rebuildSteps(percent, activeIndex);
        }

        /** 根据总进度与当前活动步骤索引，生成供 UI 展示的 {@link ParseStepView} 列表。 */
        private void rebuildSteps(int percent, int activeIndex) {
            steps.clear();
            if (percent >= 100) {
                for (String name : STEP_NAMES) {
                    steps.add(new ParseStepView(name, "finish"));
                }
                return;
            }
            for (int i = 0; i < STEP_NAMES.size(); i++) {
                String state;
                if (i < activeIndex) {
                    state = "finish";
                } else if (i == activeIndex) {
                    state = "process";
                } else {
                    state = "wait";
                }
                steps.add(new ParseStepView(STEP_NAMES.get(i), state));
            }
        }

        synchronized FileJobStatusResponse toView() {
            return new FileJobStatusResponse(id, fileName, percent, phase, List.copyOf(steps));
        }
    }
}
