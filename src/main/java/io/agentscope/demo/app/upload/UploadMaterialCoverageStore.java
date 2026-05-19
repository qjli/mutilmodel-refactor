package io.agentscope.demo.app.upload;

import io.agentscope.demo.app.session.MaterialCoveragePersistence;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 会话级「四类证照是否已在视觉上传链路中出现过」的累计推断（基于文件名关键词），供文本对话「还缺什么」类问题对齐
 * {@code upload_guide}。
 */
@Component
public class UploadMaterialCoverageStore {

    private final MaterialCoveragePersistence persistence;

    public UploadMaterialCoverageStore(MaterialCoveragePersistence persistence) {
        this.persistence = persistence;
    }

    public Set<String> readMerged(String safeSessionId) {
        return persistence.readMerged(safeSessionId);
    }

    /** 将若干文件名/提示中的推断结果并入会话覆盖（幂等合并）。 */
    public void mergeFromHints(String safeSessionId, Collection<String> hints) {
        LinkedHashSet<String> next = new LinkedHashSet<>(readMerged(safeSessionId));
        next.addAll(MaterialFilenameInference.inferFromHints(hints));
        persistence.writeMerged(safeSessionId, next);
    }
}
