package io.agentscope.demo.app.session;

import java.util.Set;

/**
 * 会话级「已上传证照类型」侧车存储，与 Agent {@link io.agentscope.core.session.Session} 使用相同
 * {@code store}（file / redis），但键与格式由本项目定义。
 */
public interface MaterialCoveragePersistence {

    /** @param safeSessionId 经 {@link io.agentscope.demo.SessionIds} 校验后的 id */
    Set<String> readMerged(String safeSessionId);

    /** @param detected 规范证照 id 集合（见 {@link io.agentscope.demo.app.upload.MaterialSampleIds}） */
    void writeMerged(String safeSessionId, Set<String> detected);
}
