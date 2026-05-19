package io.agentscope.demo.app.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * 歧义字段下的一个可选项：展示文案 + 写入表单时使用的取值 + 稳定 id。
 */
public class AmbiguousOptionDto {

    /** 前端 Radio {@code value}，在回调里用于比对选中项。 */
    @JsonProperty("option_id")
    public String optionId;

    /** 用户可见标签。 */
    @JsonProperty("label")
    public String label;

    /** 用户确认后应写入 {@link AmbiguousFieldDto#fieldKey} 对应控件的值。 */
    @JsonProperty("suggested_value")
    public String suggestedValue;

    public AmbiguousOptionDto() {}

    public AmbiguousOptionDto(String optionId, String label, String suggestedValue) {
        this.optionId = optionId;
        this.label = label;
        this.suggestedValue = suggestedValue;
    }

    /** 若模型未填 suggested_value，则回退用 label 作为表单值。 */
    public String safeSuggestedValue() {
        return suggestedValue != null && !suggestedValue.isBlank() ? suggestedValue : label;
    }

    /** 若 label 为空则回退展示 option_id。 */
    public String safeLabel() {
        return label != null && !label.isBlank() ? label : Objects.toString(optionId, "");
    }
}
