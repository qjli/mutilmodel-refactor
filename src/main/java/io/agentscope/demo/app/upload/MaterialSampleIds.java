package io.agentscope.demo.app.upload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与前端 {@code public/samples}、技能 {@code upload_guide_dialog} 白名单一致的四类证照代号及中文名。
 */
public final class MaterialSampleIds {

    public static final String BUSINESS_LICENSE = "BUSINESS_LICENSE";
    public static final String ID_CARD_FRONT = "ID_CARD_FRONT";
    public static final String ROAD_TRANSPORT_PERMIT = "ROAD_TRANSPORT_PERMIT";
    public static final String SAFETY_PRODUCTION_PERMIT = "SAFETY_PRODUCTION_PERMIT";

    /** 稳定展示顺序：营业执照 → 身份证 → 道路运输类 → 危化经营类 */
    public static final List<String> CANONICAL_ORDER =
            List.of(BUSINESS_LICENSE, ID_CARD_FRONT, ROAD_TRANSPORT_PERMIT, SAFETY_PRODUCTION_PERMIT);

    private static final Map<String, String> ZH = new LinkedHashMap<>();

    static {
        ZH.put(BUSINESS_LICENSE, "营业执照");
        ZH.put(ID_CARD_FRONT, "身份证人像面");
        ZH.put(ROAD_TRANSPORT_PERMIT, "道路危险货物运输许可证");
        ZH.put(SAFETY_PRODUCTION_PERMIT, "危险化学品经营许可证");
    }

    private MaterialSampleIds() {}

    public static String chineseTitle(String id) {
        return ZH.getOrDefault(id, id);
    }

    public static String defaultSubtitle(String id) {
        return switch (id) {
            case BUSINESS_LICENSE -> "企业登记主体信息";
            case ID_CARD_FRONT -> "法人或经办人身份核验";
            case ROAD_TRANSPORT_PERMIT -> "危化品道路运输资质";
            case SAFETY_PRODUCTION_PERMIT -> "安全生产与危化经营许可信息";
            default -> "请确保影像清晰、四角完整";
        };
    }
}
