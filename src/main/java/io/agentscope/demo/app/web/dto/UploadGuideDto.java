package io.agentscope.demo.app.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * 方案 C：结构化「材料清单」卡，与 {@code reply} 气泡分离，由前端独立版式渲染。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadGuideDto {

    /** 卡片标题，如「材料清单 · 仍缺补充」；可空，前端用默认文案。 */
    @JsonProperty("card_title")
    public String cardTitle;

    /** 本轮已覆盖的材料简述（与上传文件名或证类对应的中文短语）。 */
    @JsonProperty("satisfied_labels")
    public List<String> satisfiedLabels = new ArrayList<>();

    /** 仍建议用户补充拍摄的证类条目。 */
    @JsonProperty("missing_items")
    public List<UploadGuideMaterialItem> missingItems = new ArrayList<>();

    public UploadGuideDto() {}
}
