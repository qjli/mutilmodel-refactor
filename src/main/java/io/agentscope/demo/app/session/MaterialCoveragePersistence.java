package io.agentscope.demo.app.session;

import java.util.Set;

/** 会话级「已上传证照类型」侧车存储，与 Agent {@link io.agentscope.core.session.Session} 后端对齐。 */
public interface MaterialCoveragePersistence {

    Set<String> readMerged(String safeSessionId);

    void writeMerged(String safeSessionId, Set<String> detected);
}
