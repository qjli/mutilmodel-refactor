package io.agentscope.demo;

import java.util.Objects;

/**
 * JsonSession uses {@code sessionId} as a directory name. Reject path traversal and unsafe
 * characters when the id comes from HTTP or other untrusted input.
 */
public final class SessionIds {

    private static final int MAX_LEN = 128;

    private SessionIds() {}

    /**
     * @param raw proposed session id (may be untrusted)
     * @return normalized id safe for filesystem use as a single path segment
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
