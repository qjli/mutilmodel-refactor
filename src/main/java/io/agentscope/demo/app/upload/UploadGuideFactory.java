package io.agentscope.demo.app.upload;

import io.agentscope.demo.app.web.dto.UploadGuideDto;
import io.agentscope.demo.app.web.dto.UploadGuideMaterialItem;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 服务端构造 {@link UploadGuideDto}（与前端样图 id 白名单一致）。 */
public final class UploadGuideFactory {

    private UploadGuideFactory() {}

    /** 泛询问「如何使用 / 要什么材料」：固定四证示意卡。 */
    public static UploadGuideDto fourSamples() {
        UploadGuideDto dto = new UploadGuideDto();
        dto.cardTitle = "使用说明 · 四证样例示意";
        dto.satisfiedLabels = List.of();
        dto.missingItems = new ArrayList<>();
        for (String id : MaterialSampleIds.CANONICAL_ORDER) {
            UploadGuideMaterialItem it = new UploadGuideMaterialItem();
            it.sampleImageId = id;
            it.title = MaterialSampleIds.chineseTitle(id);
            it.subtitle = "示意样图";
            dto.missingItems.add(it);
        }
        return dto;
    }

    /**
     * @param coverageIds 已推断出现过的证照 id
     * @return 四类齐备则 null，否则仅含仍缺项
     */
    public static UploadGuideDto remaining(Set<String> coverageIds) {
        LinkedHashSet<String> cov = new LinkedHashSet<>();
        if (coverageIds != null) {
            for (String id : coverageIds) {
                if (MaterialSampleIds.CANONICAL_ORDER.contains(id)) {
                    cov.add(id);
                }
            }
        }
        List<String> missing =
                MaterialSampleIds.CANONICAL_ORDER.stream().filter(id -> !cov.contains(id)).toList();
        if (missing.isEmpty()) {
            return null;
        }
        UploadGuideDto dto = new UploadGuideDto();
        dto.cardTitle = "仍建议补充以下证照（示意样图）";
        dto.satisfiedLabels = new ArrayList<>();
        for (String id : MaterialSampleIds.CANONICAL_ORDER) {
            if (cov.contains(id)) {
                dto.satisfiedLabels.add(MaterialSampleIds.chineseTitle(id));
            }
        }
        dto.missingItems = new ArrayList<>();
        for (String id : missing) {
            UploadGuideMaterialItem it = new UploadGuideMaterialItem();
            it.sampleImageId = id;
            it.title = MaterialSampleIds.chineseTitle(id);
            it.subtitle = "请确保图片中文字清晰可见";
            dto.missingItems.add(it);
        }
        return dto;
    }
}
