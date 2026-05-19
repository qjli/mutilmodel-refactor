package io.agentscope.demo.app.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST {@code /api/sessions/{id}/messages} 的请求体：单字段用户正文。
 *
 * @param content 用户输入，非空且上限 16000 字符（防止过大 payload）
 */
public record ChatRequest(
        @NotBlank @Size(max = 16_000) String content) {}
