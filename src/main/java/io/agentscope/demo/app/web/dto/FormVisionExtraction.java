package io.agentscope.demo.app.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多图视觉路径下 Agent 的结构化输出：表单补丁、歧义列表与一句中文摘要。
 *
 * <p>经 SSE {@code result} 事件原样序列化给前端；未知字段由 {@link JsonIgnoreProperties#ignoreUnknown()} 忽略。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormVisionExtraction {

    /** 模型有把握的字段键值对（camelCase 键）。 */
    @JsonProperty("form_patch")
    public Map<String, Object> formPatch = new LinkedHashMap<>();

    /** 需用户在表单侧点选确认的字段；空列表表示无歧义。 */
    @JsonProperty("ambiguities")
    public List<AmbiguousFieldDto> ambiguities = new ArrayList<>();

    /** 简短中文总结，展示在对话气泡主文案。 */
    @JsonProperty("reply")
    public String reply;

    /**
     * 上传引导卡字段：多图视觉链路中由服务端固定为 {@code null}；材料卡仅在文本对话命中「如何使用/缺件」等意图时下发。
     */
    @JsonProperty("upload_guide")
    public UploadGuideDto uploadGuide;

    /**
     * 宿主已对多主体工商整族从 {@link #formPatch} 剔除；前端须同步清空表单中对应旧值，避免与「需您确认」不一致。
     */
    @JsonProperty("multi_enterprise_conflict_applied")
    public boolean multiEnterpriseConflictApplied;

    @JsonProperty("multi_transport_conflict_applied")
    public boolean multiTransportConflictApplied;

    @JsonProperty("multi_safety_conflict_applied")
    public boolean multiSafetyConflictApplied;

    public FormVisionExtraction() {}
}
