package io.agentscope.demo.app.session;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.demo.app.config.AgentscopeProperties;
import io.agentscope.demo.app.upload.MaterialSampleIds;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 证照覆盖侧车：与 JsonSession 同目录 {@code {fileRoot}/{sessionId}/upload_material_coverage.json}。
 */
@Component
@ConditionalOnProperty(prefix = "agentscope.session", name = "store", havingValue = "file")
public class FileMaterialCoveragePersistence implements MaterialCoveragePersistence {

    private static final Logger log = LoggerFactory.getLogger(FileMaterialCoveragePersistence.class);
    /** 与 Redis 侧车语义一致，便于 file → redis 迁移脚本对照。 */
    private static final String FILE_NAME = "upload_material_coverage.json";

    private final Path sessionRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public FileMaterialCoveragePersistence(AgentscopeProperties properties) {
        this.sessionRoot = properties.resolvedSessionRoot();
    }

    @Override
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

    @Override
    public void writeMerged(String safeSessionId, Set<String> detected) {
        Path p = coverageFile(safeSessionId);
        try {
            Files.createDirectories(p.getParent());
            CoverageFile cf = new CoverageFile();
            cf.detected = new ArrayList<>(detected);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), cf);
            log.debug("[coverage] file write sessionId={} count={}", safeSessionId, detected.size());
        } catch (IOException e) {
            log.warn("[coverage] file write failed sessionId={} path={}", safeSessionId, p, e);
        }
    }

    private Path coverageFile(String safeSessionId) {
        return sessionRoot.resolve(safeSessionId).resolve(FILE_NAME);
    }

    /** 磁盘 JSON：{@code {"detected":["BUSINESS_LICENSE",...]}} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static final class CoverageFile {
        public List<String> detected = new ArrayList<>();
    }
}
