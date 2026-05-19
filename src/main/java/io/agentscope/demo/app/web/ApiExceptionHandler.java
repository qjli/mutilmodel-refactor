package io.agentscope.demo.app.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 将常见异常统一转换为 RFC 7807 {@link ProblemDetail} JSON，便于前端与网关消费。
 *
 * <p>未在此声明的异常仍由 Spring Boot 默认错误页 / 白标处理；可按需扩展 {@link ExceptionHandler}。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** 业务参数非法（如 sessionId 格式、空文件等），映射 HTTP 400。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("[api] 400 {} — {}", req.getMethod(), req.getRequestURI(), ex);
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        p.setTitle("Bad Request");
        p.setInstance(java.net.URI.create(req.getRequestURI()));
        return p;
    }

    /** 超过 {@code spring.servlet.multipart.max-file-size} 等限制时由框架抛出，映射 HTTP 413。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        log.warn("[api] 413 {} {} multipart limit", req.getMethod(), req.getRequestURI(), ex);
        ProblemDetail p =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.PAYLOAD_TOO_LARGE, "上传文件超过大小限制");
        p.setTitle("Payload Too Large");
        p.setInstance(java.net.URI.create(req.getRequestURI()));
        return p;
    }
}
