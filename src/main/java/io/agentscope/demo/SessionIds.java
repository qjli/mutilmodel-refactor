package io.agentscope.demo;

import java.util.Objects;

/**
 * 会话 ID 安全校验：{@link io.agentscope.core.session.JsonSession} 与 Redis Session 均以 {@code sessionId}
 * 作为目录名或键的一部分；来自 HTTP 的 id 必须拒绝路径穿越与非法字符。
 */
public final class SessionIds {

    /** 与前端 UUID 及运维习惯对齐的上限，防止异常超长路径/键。 */
    private static final int MAX_LEN = 128;

    private SessionIds() {}

    /**
     * @param raw 客户端传入的 sessionId（不可信）
     * @return 修剪后的安全 id，可作为单一路径段或 Redis 键片段
     * @throws IllegalArgumentException 空、过长、含 {@code ..}、分隔符或非法字符
     */
    public static String requireSafeSessionId(String raw) {
        Objects.requireNonNull(raw, "sessionId");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (trimmed.length() > MAX_LEN) {
            throw new IllegalArgumentException("sessionId too long (max " + MAX_LEN + ")");
        }
        if (trimmed.contains("..")
                || trimmed.indexOf('/') >= 0
                || trimmed.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("sessionId must not contain path separators or '..'");
        }
        if (!trimmed.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException(
                    "sessionId may only contain letters, digits, underscore, hyphen");
        }
        return trimmed;
    }
}
