package io.agentscope.demo.app.upload;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 从用户上传时的<strong>文件名</strong>或对话中的简短文件名提示推断已出现的证照类型（启发式，演示用）。
 *
 * <p>命中多条时全部并入集合；无法识别则不加，由后续对话引导用户改名或继续上传。
 */
public final class MaterialFilenameInference {

    private static final Pattern BUSINESS =
            Pattern.compile("营业执照|营业\\s*执照|统一社会信用代码|社会信用代码|工商执照");
    private static final Pattern ID_CARD =
            Pattern.compile("身份证|身分证|居民身份证|人像面|人像|idcard|id_card|证件正面");
    private static final Pattern ROAD =
            Pattern.compile(
                    "道路危险货物运输|危货运输|危险货物运输|道路运输证.*危|危化品运输|危险品运输"
                            + "|运输许可证.*危|road[_-]?transport|transport[_-]?permit");
    private static final Pattern SAFETY =
            Pattern.compile(
                    "危险化学品经营|危化品经营|危化经营|危险品经营|安全生产许可证|安许|safety[_-]?production"
                            + "|hazmat.*business|chemical.*business|经营许可.*危化");

    private MaterialFilenameInference() {}

    /** 从一段文本（通常为原始文件名）推断可能对应的 {@link MaterialSampleIds} 集合。 */
    public static Set<String> inferFromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        String s = raw.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (BUSINESS.matcher(s).find()) {
            out.add(MaterialSampleIds.BUSINESS_LICENSE);
        }
        if (ID_CARD.matcher(s).find()) {
            out.add(MaterialSampleIds.ID_CARD_FRONT);
        }
        if (ROAD.matcher(s).find()) {
            out.add(MaterialSampleIds.ROAD_TRANSPORT_PERMIT);
        }
        if (SAFETY.matcher(s).find()) {
            out.add(MaterialSampleIds.SAFETY_PRODUCTION_PERMIT);
        }
        return out;
    }

    /** 合并多文件名/提示文本的推断结果（去重，保持 canonical id）。 */
    public static Set<String> inferFromHints(Iterable<String> hints) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String h : hints) {
            merged.addAll(inferFromText(h));
        }
        return merged;
    }
}
