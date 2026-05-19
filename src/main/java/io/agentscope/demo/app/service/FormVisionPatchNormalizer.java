package io.agentscope.demo.app.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 将视觉模型 {@code form_patch} 中常见的「自创 camelCase / 前缀式键名」归一为前端表单实际使用的键名，并剔除白名单外字段。
 *
 * <p>模型在单图、多图、思考链开启与否等情况下输出键名不稳定；本类在 SSE 下发前做确定性修正，避免 Ant Design Form
 * {@code setFieldsValue} 静默丢弃未知键。
 *
 * <p><b>与前端同步：</b>{@code frontend/src/App.tsx} 中各 {@code Form.Item name} 及 {@code frontend/src/api.ts} 中
 * {@code VISION_ENTERPRISE_FIELD_KEYS}、{@code VISION_TRANSPORT_FIELD_KEYS}、{@code VISION_SAFETY_FIELD_KEYS} 须与本类
 * {@link #CANONICAL_KEYS} 及 {@link io.agentscope.demo.app.service.FormVisionMultiEntityConflictDetector} 族键保持一致（拼写、
 * camelCase）。
 */
public final class FormVisionPatchNormalizer {

    /** 与 {@code frontend/src/App.tsx} 中 {@code Form.Item} 的 {@code name} 一致。 */
    public static final Set<String> CANONICAL_KEYS =
            Set.of(
                    "companyName",
                    "companyShortName",
                    "formerName",
                    "enterpriseNature",
                    "enterpriseType",
                    "businessScope",
                    "companyPhone",
                    "companyEmail",
                    "registrationDate",
                    "registeredRegion",
                    "registeredAddressDetail",
                    "actualLocation",
                    "registeredZip",
                    "registeredCapital",
                    "companyFax",
                    "learnChannel",
                    "unifiedSocialCreditCode",
                    "qualificationIdDocType",
                    "qualificationIdDocNumber",
                    "legalRepresentative",
                    "safetyAdminLicenseName",
                    "safetyLicenseNo",
                    "safetyLicenseValidityMode",
                    "safetyLicenseValidityRange",
                    "safetyIssuingAuthority",
                    "safetyLegalRepresentative",
                    "transportAdminLicenseName",
                    "transportLicenseNo",
                    "transportLicenseValidityMode",
                    "transportLicenseValidityRange",
                    "transportIssuingAuthority",
                    "transportLegalRepresentative");

    private static final Map<String, String> LOWER_KEY_TO_CANONICAL = new HashMap<>();

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}");
    /** 证面常见「2003年04月18日」；归一化为 ISO 供前端 DatePicker（dayjs）稳定解析。 */
    private static final Pattern CHINESE_CALENDAR_DATE =
            Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日?");

    static {
        // 营业执照 / 工商（单图常见别名）
        alias("creditcode", "unifiedSocialCreditCode");
        alias("socialcreditcode", "unifiedSocialCreditCode");
        alias("businessname", "companyName");
        alias("companynamecn", "companyName");
        // 模型常见自造键：与前端 Form.Item name=companyName 对齐
        alias("enterprisename", "companyName");
        alias("enterprise_name", "companyName");
        alias("company_name", "companyName");
        alias("enterprisecompanyname", "companyName");
        alias("legalrep", "legalRepresentative");
        alias("legalrepresentative", "legalRepresentative");
        alias("establishmentdate", "registrationDate");
        alias("foundingdate", "registrationDate");
        alias("address", "registeredAddressDetail");
        alias("registeredaddress", "registeredAddressDetail");
        alias("domicile", "registeredAddressDetail");
        alias("type", "enterpriseType");
        alias("companytype", "enterpriseType");
        // 批量四图路径里 businessLicense* 前缀
        alias("businesslicenseunifiedsocialcreditcode", "unifiedSocialCreditCode");
        alias("businesslicensename", "companyName");
        alias("businesslicenselegalrepresentative", "legalRepresentative");
        alias("businesslicenseregisteredcapital", "registeredCapital");
        alias("businesslicensetype", "enterpriseType");
        alias("businesslicensebusinessscope", "businessScope");
        alias("businesslicenseestablishmentdate", "registrationDate");
        alias("businesslicenseaddress", "registeredAddressDetail");
        // 身份证 → 企业资质证件号码
        alias("idcardidnumber", "qualificationIdDocNumber");
        alias("idcardnumber", "qualificationIdDocNumber");
        alias("idnumber", "qualificationIdDocNumber");
        alias("citizenidnumber", "qualificationIdDocNumber");
        // 道路运输
        alias("transportpermitnumber", "transportLicenseNo");
        alias("transportlicensenumber", "transportLicenseNo");
        alias("transportnumber", "transportLicenseNo");
        alias("transportpermitissuingauthority", "transportIssuingAuthority");
        alias("transportissueauthority", "transportIssuingAuthority");
        alias("transportauthority", "transportIssuingAuthority");
        alias("transportcompanyname", "transportAdminLicenseName");
        alias("transportholdername", "transportAdminLicenseName");
        alias("transportpermitholdername", "transportAdminLicenseName");
        alias("transportbusinessscope", "transportAdminLicenseName");
        alias("transportpermitbusinessscope", "transportAdminLicenseName");
        // 危化 / 安全生产
        alias("safetypermitnumber", "safetyLicenseNo");
        alias("safetylicensenumber", "safetyLicenseNo");
        alias("safetycompanyname", "safetyAdminLicenseName");
        alias("safetypermitholdername", "safetyAdminLicenseName");
        alias("safetyholdername", "safetyAdminLicenseName");
        alias("safetypermitissuingauthority", "safetyIssuingAuthority");
        alias("safetyissueauthority", "safetyIssuingAuthority");
        alias("safetylegalrep", "safetyLegalRepresentative");
        alias("safetylegalrepresentative", "safetyLegalRepresentative");
        alias("safetypermitlegalrepresentative", "safetyLegalRepresentative");
        alias("safetybusinessmode", "safetyAdminLicenseName");
        alias("safetypermitbusinessmode", "safetyAdminLicenseName");
        alias("safetybusinessscope", "safetyAdminLicenseName");
        alias("safetypermitbusinessscope", "safetyAdminLicenseName");
        alias("safetyscope", "safetyAdminLicenseName");
    }

    private static void alias(String lower, String canonical) {
        LOWER_KEY_TO_CANONICAL.put(lower, canonical);
    }

    private FormVisionPatchNormalizer() {}

    public static LinkedHashMap<String, Object> normalize(Map<String, Object> raw) {
        LinkedHashMap<String, Object> flat = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                flat.put(e.getKey().trim(), e.getValue());
            }
        }
        coalesceValidityRanges(flat, "transport");
        coalesceValidityRanges(flat, "safety");

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
            String canon = canonicalKey(e.getKey());
            if (canon == null) {
                continue;
            }
            Object v = normalizeValue(canon, e.getValue());
            if (v == null) {
                continue;
            }
            out.merge(canon, v, (a, b) -> mergeValues(canon, a, b));
        }
        if (!out.containsKey("qualificationIdDocType")
                && out.containsKey("qualificationIdDocNumber")) {
            out.putIfAbsent("qualificationIdDocType", "身份证");
        }
        if (out.containsKey("transportLicenseValidityRange")) {
            out.putIfAbsent("transportLicenseValidityMode", "fixed");
        }
        if (out.containsKey("safetyLicenseValidityRange")) {
            out.putIfAbsent("safetyLicenseValidityMode", "fixed");
        }
        out.entrySet().removeIf(en -> !CANONICAL_KEYS.contains(en.getKey()));
        return out;
    }

    private static void coalesceValidityRanges(LinkedHashMap<String, Object> m, String family) {
        if (!"transport".equals(family) && !"safety".equals(family)) {
            return;
        }
        String pfx = "transport".equals(family) ? "transport" : "safety";
        String startK1 = pfx + "PermitValidityPeriodStart";
        String endK1 = pfx + "PermitValidityPeriodEnd";
        String startK2 = pfx + "ValidityPeriodStart";
        String endK2 = pfx + "ValidityPeriodEnd";
        String rangeKey =
                "transport".equals(family)
                        ? "transportLicenseValidityRange"
                        : "safetyLicenseValidityRange";
        Object s = firstNonNull(m.remove(startK1), m.remove(startK2));
        Object e = firstNonNull(m.remove(endK1), m.remove(endK2));
        if (s != null && e != null) {
            String ss = stringifyDate(s);
            String ee = stringifyDate(e);
            if (ss != null && ee != null) {
                m.put(rangeKey, List.of(ss, ee));
                String modeKey =
                        "transport".equals(family)
                                ? "transportLicenseValidityMode"
                                : "safetyLicenseValidityMode";
                m.putIfAbsent(modeKey, "fixed");
            }
        }
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String stringifyDate(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        var m = ISO_DATE.matcher(s);
        if (m.find()) {
            return m.group();
        }
        String cnIso = registrationDateToIsoOrNull(s);
        return cnIso != null ? cnIso : s;
    }

    /** 将成立日期 / 注册日期类字符串转为 {@code yyyy-MM-dd}；无法识别时返回 {@code null}（丢弃该键）。 */
    static String registrationDateToIsoOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        var isoHead = ISO_DATE.matcher(t);
        if (isoHead.find()) {
            return isoHead.group();
        }
        var cn = CHINESE_CALENDAR_DATE.matcher(t);
        if (cn.find()) {
            int y = Integer.parseInt(cn.group(1));
            int mo = Integer.parseInt(cn.group(2));
            int d = Integer.parseInt(cn.group(3));
            return String.format(Locale.ROOT, "%04d-%02d-%02d", y, mo, d);
        }
        return null;
    }

    private static String canonicalKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return null;
        }
        String k = rawKey.trim();
        if (CANONICAL_KEYS.contains(k)) {
            return k;
        }
        String lower = k.toLowerCase(Locale.ROOT);
        String mapped = LOWER_KEY_TO_CANONICAL.get(lower);
        if (mapped != null) {
            return mapped;
        }
        // idCard*：仅公民身份号码映射到 qualificationIdDocNumber，其余丢弃
        if (lower.startsWith("idcard")) {
            return null;
        }
        // businessLicense* 未命中静态表时尽量兜底到已知工商字段
        if (lower.startsWith("businesslicense")) {
            if (lower.contains("unified") || lower.contains("credit")) {
                return "unifiedSocialCreditCode";
            }
            if (lower.contains("name") && !lower.contains("type")) {
                return "companyName";
            }
            if (lower.contains("legal")) {
                return "legalRepresentative";
            }
            if (lower.contains("capital")) {
                return "registeredCapital";
            }
            if (lower.contains("type")) {
                return "enterpriseType";
            }
            if (lower.contains("scope")) {
                return "businessScope";
            }
            if (lower.contains("establishment") || lower.contains("found")) {
                return "registrationDate";
            }
            if (lower.contains("address") || lower.contains("domicile")) {
                return "registeredAddressDetail";
            }
            return null;
        }
        // transport* / safety* 未命中表则丢弃，避免脏键
        if (lower.startsWith("transport") || lower.startsWith("safety")) {
            return null;
        }
        // enterpriseName / 企业名称 等 → companyName（勿与 enterpriseType 混淆）
        if (lower.contains("enterprise") && lower.contains("name") && !lower.contains("type")) {
            return "companyName";
        }
        if (lower.contains("company")
                && lower.contains("name")
                && !lower.contains("type")
                && !lower.contains("short")
                && !lower.contains("phone")
                && !lower.contains("email")
                && !lower.contains("fax")) {
            return "companyName";
        }
        return null;
    }

    private static Object normalizeValue(String canon, Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            if ("registeredCapital".equals(canon)) {
                return String.valueOf(n);
            }
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            if ("registrationDate".equals(canon)) {
                return registrationDateToIsoOrNull(t);
            }
            return t;
        }
        if (("transportLicenseValidityRange".equals(canon) || "safetyLicenseValidityRange".equals(canon))
                && v instanceof List<?> list
                && list.size() >= 2) {
            String sa = stringifyDate(list.get(0));
            String sb = stringifyDate(list.get(1));
            if (sa != null && sb != null) {
                return List.of(sa, sb);
            }
        }
        return v;
    }

    private static Object mergeValues(String canon, Object a, Object b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if ("unifiedSocialCreditCode".equals(canon)) {
            return pickBetterUscc(String.valueOf(a), String.valueOf(b));
        }
        if ("transportLicenseNo".equals(canon) || "safetyLicenseNo".equals(canon)) {
            return pickLongerPermitNo(String.valueOf(a), String.valueOf(b));
        }
        if ("transportAdminLicenseName".equals(canon) || "safetyAdminLicenseName".equals(canon)) {
            String sa = String.valueOf(a).trim();
            String sb = String.valueOf(b).trim();
            if (sa.isEmpty()) {
                return sb;
            }
            if (sb.isEmpty()) {
                return sa;
            }
            if (sa.contains(sb) || sb.contains(sa)) {
                return sa.length() >= sb.length() ? sa : sb;
            }
            return sa + "；" + sb;
        }
        if (a instanceof String && b instanceof String) {
            String sa = ((String) a).trim();
            String sb = ((String) b).trim();
            if (sa.isEmpty()) {
                return sb;
            }
            if (sb.isEmpty()) {
                return sa;
            }
            return sa.length() >= sb.length() ? sa : sb;
        }
        if (a instanceof List && b instanceof List) {
            return b;
        }
        return b;
    }

    private static String pickBetterUscc(String a, String b) {
        String x = preferUsccOne(a);
        String y = preferUsccOne(b);
        if (x == null) {
            return y;
        }
        if (y == null) {
            return x;
        }
        if (x.length() == 18 && y.length() != 18) {
            return x;
        }
        if (y.length() == 18 && x.length() != 18) {
            return y;
        }
        if (!x.contains("*") && y.contains("*")) {
            return x;
        }
        if (!y.contains("*") && x.contains("*")) {
            return y;
        }
        return x.length() >= y.length() ? x : y;
    }

    private static String preferUsccOne(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 证号类字段：优先保留更长、含中文或字母前缀的证面原文，避免模型只输出数字片段。 */
    private static String pickLongerPermitNo(String a, String b) {
        String x = preferUsccOne(a);
        String y = preferUsccOne(b);
        if (x == null) {
            return y;
        }
        if (y == null) {
            return x;
        }
        if (x.length() != y.length()) {
            return x.length() >= y.length() ? x : y;
        }
        boolean xRich = x.matches(".*[^0-9].*");
        boolean yRich = y.matches(".*[^0-9].*");
        if (xRich && !yRich) {
            return x;
        }
        if (yRich && !xRich) {
            return y;
        }
        return x;
    }
}
