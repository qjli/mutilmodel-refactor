package io.agentscope.demo.app.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * 单个表单字段存在多个合理候选时的结构化描述，驱动前端 Radio 组。
 */
public class AmbiguousFieldDto {

    /** 与前端 Form.Item {@code name} 一致的 camelCase 字段名。 */
    @JsonProperty("field_key")
    public String fieldKey;

    /** 展示给用户的一句说明（为何需要确认）。 */
    @JsonProperty("question_for_user")
    public String questionForUser;

    /** 候选列表，通常不少于 2 条才有意义。 */
    @JsonProperty("options")
    public List<AmbiguousOptionDto> options = new ArrayList<>();

    public AmbiguousFieldDto() {}
}
