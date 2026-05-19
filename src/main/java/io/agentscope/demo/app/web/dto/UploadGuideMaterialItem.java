package io.agentscope.demo.app.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 上传引导卡中「仍缺材料」单项：由模型给出稳定代号，前端映射为样例缩略图 URL。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadGuideMaterialItem {

    /**
     * 与技能 {@code upload_guide_dialog} 白名单一致，仅四值之一：{@code BUSINESS_LICENSE}、{@code
     * ID_CARD_FRONT}、{@code ROAD_TRANSPORT_PERMIT}、{@code SAFETY_PRODUCTION_PERMIT}。
     */
    @JsonProperty("sample_image_id")
    public String sampleImageId;

    /** 卡片主标题（证照中文名）。 */
    @JsonProperty("title")
    public String title;

    /** 可选副文案（版式提示、区块说明等）。 */
    @JsonProperty("subtitle")
    public String subtitle;

    public UploadGuideMaterialItem() {}
}
