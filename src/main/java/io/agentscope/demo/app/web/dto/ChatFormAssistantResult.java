package io.agentscope.demo.app.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文本对话路径下 {@link io.agentscope.core.agent.CallableAgent#call} 的结构化输出类型（Jackson 与模型 JSON 字段对齐）。
 *
 * <p>与 {@link ChatResponse} 对应：{@code reply}、{@code form_patch}、{@code upload_guide} 等字段映射到 HTTP
 * JSON。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatFormAssistantResult {

    /** 面向用户的中文自然语言说明。 */
    @JsonProperty("reply")
    public String reply;

    /** 建议回填的表单字段子集；不确定的字段不应出现。 */
    @JsonProperty("form_patch")
    public Map<String, Object> formPatch = new LinkedHashMap<>();

    /** 可选：材料清单卡；规则见技能 {@code upload_guide_dialog}。 */
    @JsonProperty("upload_guide")
    public UploadGuideDto uploadGuide;

    public ChatFormAssistantResult() {}
}
