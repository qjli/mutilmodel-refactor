package io.agentscope.demo.app.upload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.demo.app.config.AgentscopeProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 会话级「四类证照是否已在视觉上传链路中出现过」的累计推断（基于文件名关键词），供文本对话「还缺什么」类问题对齐
 * {@code upload_guide}。
 */
@Component
public class UploadMaterialCoverageStore {

    private static final Logger log = LoggerFactory.getLogger(UploadMaterialCoverageStore.class);
    private static final String FILE_NAME = "upload_material_coverage.json";

    private final Path sessionRoot;
    private final ObjectMapper objectMapper;

    public UploadMaterialCoverageStore(AgentscopeProperties properties) {
        this.sessionRoot = properties.resolvedSessionRoot();
        this.objectMapper = new ObjectMapper();
    }

    private Path coverageFile(String safeSessionId) {
        return sessionRoot.resolve(safeSessionId).resolve(FILE_NAME);
    }

    /** 读取已合并的代号集合（可能为空，永不为 null）。 */
    public Set<String> readMerged(String safeSessionId) {
        Path p = coverageFile(safeSessionId);
        if (!Files.isRegularFile(p)) {
            return Set.of();
        }
        try {
            CoverageFile cf = objectMapper.readValue(p.toFile(), CoverageFile.class);
            if (cf == null || cf.detected == null || cf.detected.isEmpty()) {
                return Set.of();
            }
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (String id : cf.detected) {
                if (id != null && MaterialSampleIds.CANONICAL_ORDER.contains(id)) {
                    out.add(id);
                }
            }
            return out;
        } catch (IOException e) {
            log.warn("[coverage] read failed sessionId={} path={}", safeSessionId, p, e);
            return Set.of();
        }
    }

    /**
     * 将若干文件名/提示中的推断结果并入会话覆盖（幂等合并）。
     */
    public void mergeFromHints(String safeSessionId, Collection<String> hints) {
        LinkedHashSet<String> next = new LinkedHashSet<>(readMerged(safeSessionId));
        next.addAll(MaterialFilenameInference.inferFromHints(hints));
        write(safeSessionId, next);
    }

    private void write(String safeSessionId, Set<String> detected) {
        Path p = coverageFile(safeSessionId);
        try {
            Files.createDirectories(p.getParent());
            CoverageFile cf = new CoverageFile();
            cf.detected = new ArrayList<>(detected);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), cf);
            log.debug("[coverage] write sessionId={} count={}", safeSessionId, detected.size());
        } catch (IOException e) {
            log.warn("[coverage] write failed sessionId={} path={}", safeSessionId, p, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CoverageFile {
        public List<String> detected = new ArrayList<>();
    }
}
