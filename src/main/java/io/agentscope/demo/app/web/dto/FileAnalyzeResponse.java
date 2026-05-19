package io.agentscope.demo.app.web.dto;

/**
 * 提交单文件解析任务后的立即响应：携带异步任务 id 与元数据。
 *
 * @param jobId 轮询 {@code GET /api/files/{jobId}} 时使用
 * @param fileName 原始文件名（可能为默认占位）
 * @param sizeBytes 上传字节数（演示用，不参与真实解析）
 */
public record FileAnalyzeResponse(String jobId, String fileName, long sizeBytes) {}
