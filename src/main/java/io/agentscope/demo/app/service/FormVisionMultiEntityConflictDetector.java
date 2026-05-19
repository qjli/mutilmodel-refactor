package io.agentscope.demo.app.service;

import io.agentscope.demo.app.web.dto.AmbiguousFieldDto;
import io.agentscope.demo.app.web.dto.AmbiguousOptionDto;
import io.agentscope.demo.app.web.dto.FormVisionExtraction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 在 {@link FormVisionPatchNormalizer} 之后执行：多证互斥时<strong>不猜测</strong>唯一取值，从 {@code form_patch}
 * 剔除相关键并注入 {@code ambiguities}，与技能 {@code form_vision_fill}「多主体」规则对齐。
 *
 * <p>策略：一旦检出「多主体」信号（多张营业执照 / 多统一码、或多张同类许可证证号互斥），对<strong>整族</strong>相关
 * 字段做清空 + 逐项人工确认（候选来自 raw 单键归一化去重 + 归一化后快照）；单候选时 Radio
 * 文案须<strong>直接展示识别到的字面量</strong>（不得使用「采用模型识别值」等无信息标签），清空项的 {@code suggested_value} 为常量
 * {@link #CLEAR_FIELD_SENTINEL}，由前端识别后清空控件。
 */
public final class FormVisionMultiEntityConflictDetector {

    /** 与前端 {@link io.agentscope.demo.app.web} 约定：点选后表示不采用模型自动值、由用户手填。 */
    public static final String CLEAR_FIELD_SENTINEL = "__CLEAR_FIELD__";

    /**
     * 从任意 OCR 长文本中抓取疑似统一社会信用代码的 18 位串（字母数字，可含掩码 *），用于多图时模型把两证码挤在同一
     * 字段、或仅用非标键名导致键级收集遗漏的场景；与键级 {@link #collectUsccFromRaw} 互补，不改变归一化结果。
     */
    private static final Pattern USCC_18_IN_TEXT =
            Pattern.compile("(?<![0-9A-Za-z*])([0-9A-Za-z*]{18})(?![0-9A-Za-z*])");

    /** 证面常见「名称 / 企业名称 / 业户名称」标签后的主体名；量词用贪婪，避免 {@code {2,200}?} 非贪婪只吃到 2 个字。 */
    private static final Pattern ZH_ENTITY_NAME_AFTER_LABEL =
            Pattern.compile("(?:名称|企业名称|业户名称)\\s*[:：]\\s*([^\\n；;]{4,200})");

    private static final Pattern ORG_MARKERS =
            Pattern.compile("(公司|企业|厂|中心|集团|合作社|经营部|商行|店|个体工商户)");
    private static final Pattern YE_HU = Pattern.compile("业户名称\\s*[:：]\\s*([^；;\\n]+)");
    private static final Pattern QIYE_NAME = Pattern.compile("企业名称\\s*[:：]\\s*([^；;\\n]+)");

    /**
     * 与技能「多张营业执照多主体」所列工商单行字段一致，并含 {@code legalRepresentative}；多主体时整族清空并逐项
     * 歧义。
     */
    private static final List<String> ENTERPRISE_BASIC_KEYS = List.of(
            "companyName",
            "companyShortName",
            "formerName",
            "unifiedSocialCreditCode",
            "enterpriseNature",
            "enterpriseType",
            "registeredRegion",
            "registeredAddressDetail",
            "actualLocation",
            "registeredZip",
            "registrationDate",
            "registeredCapital",
            "businessScope",
            "legalRepresentative");

    /** 已由 {@link #companyNameAmbiguity} / {@link #usccAmbiguity} 精确处理的键，避免族内歧义覆盖多候选。 */
    private static final Set<String> ENTERPRISE_KEYS_WITH_STRUCTURED_AMBIGUITY =
            Set.of("companyName", "unifiedSocialCreditCode");

    private static final Set<String> ENTERPRISE_KEY_SET = Set.copyOf(ENTERPRISE_BASIC_KEYS);

    private static List<String> enterpriseFamilyKeysForBulkAmbiguity() {
        List<String> out = new ArrayList<>();
        for (String k : ENTERPRISE_BASIC_KEYS) {
            if (!ENTERPRISE_KEYS_WITH_STRUCTURED_AMBIGUITY.contains(k)) {
                out.add(k);
            }
        }
        return out;
    }

    private static final List<String> TRANSPORT_PERMIT_KEYS =
            List.of(
                    "transportAdminLicenseName",
                    "transportLicenseNo",
                    "transportLicenseValidityMode",
                    "transportLicenseValidityRange",
                    "transportIssuingAuthority",
                    "transportLegalRepresentative");

    private static final Set<String> TRANSPORT_KEY_SET = Set.copyOf(TRANSPORT_PERMIT_KEYS);

    private static final List<String> SAFETY_PERMIT_KEYS =
            List.of(
                    "safetyAdminLicenseName",
                    "safetyLicenseNo",
                    "safetyLicenseValidityMode",
                    "safetyLicenseValidityRange",
                    "safetyIssuingAuthority",
                    "safetyLegalRepresentative");

    private static final Set<String> SAFETY_KEY_SET = Set.copyOf(SAFETY_PERMIT_KEYS);

    private FormVisionMultiEntityConflictDetector() {}

    /**
     * @param extraction 当前轮视觉结构化结果（{@code form_patch} 已归一化）
     * @param rawPatch 归一化前模型原始 {@code form_patch} 快照（用于读取已被白名单丢弃的「业户名称 / 企业名称」等键）
     */
    public static void apply(FormVisionExtraction extraction, Map<String, Object> rawPatch) {
        if (extraction == null) {
            return;
        }
        Map<String, Object> raw = rawPatch == null ? Map.of() : rawPatch;
        LinkedHashMap<String, Object> patch = ensureMutablePatch(extraction);
        LinkedHashMap<String, Object> valueSnapshot = new LinkedHashMap<>(patch);

        LinkedHashSet<String> companyCandidates = new LinkedHashSet<>();
        collectCompanyNamesFromRaw(raw, companyCandidates);
        collectZhLabeledCompanyNamesFromRawStringValues(raw, companyCandidates);
        addIfNonBlank(companyCandidates, stringVal(patch.get("companyName")));
        extractEmbeddedNames(stringVal(patch.get("transportAdminLicenseName")), YE_HU, companyCandidates);
        extractEmbeddedNames(stringVal(patch.get("safetyAdminLicenseName")), QIYE_NAME, companyCandidates);

        List<String> orgLike = companyCandidates.stream().filter(FormVisionMultiEntityConflictDetector::looksLikeOrgName).distinct().toList();
        List<String> conflictCluster = buildConflictCluster(orgLike);

        LinkedHashSet<String> usccCandidates = new LinkedHashSet<>();
        collectUsccFromRaw(raw, usccCandidates);
        collectUsccFromRawStringValues(raw, usccCandidates);
        addIfNonBlank(usccCandidates, stringVal(patch.get("unifiedSocialCreditCode")));
        List<String> usccDistinct = usccCandidates.stream().map(String::trim).distinct().toList();
        List<String> usccConflict = buildUsccConflictCluster(usccDistinct);

        boolean multiEnterprise =
                conflictCluster.size() >= 2 || usccConflict.size() >= 2 || companyCandidates.size() >= 2;

        LinkedHashSet<String> transportNos = collectDistinctStringValuesForCanonical(raw, "transportLicenseNo");
        addIfNonBlank(transportNos, stringVal(patch.get("transportLicenseNo")));
        boolean multiTransport = buildPermitNoConflictCluster(new ArrayList<>(transportNos)).size() >= 2;

        LinkedHashSet<String> safetyNos = collectDistinctStringValuesForCanonical(raw, "safetyLicenseNo");
        addIfNonBlank(safetyNos, stringVal(patch.get("safetyLicenseNo")));
        boolean multiSafety = buildPermitNoConflictCluster(new ArrayList<>(safetyNos)).size() >= 2;

        if (multiEnterprise) {
            stripAmbiguitiesForFieldKeys(extraction, ENTERPRISE_KEY_SET);
            removeKeys(patch, ENTERPRISE_KEY_SET);
            List<String> nameOptions = pickCompanyNameDisambiguationOptions(conflictCluster, orgLike, companyCandidates);
            if (!nameOptions.isEmpty()) {
                appendAmbiguity(extraction, companyNameAmbiguity(nameOptions));
            }
            if (usccConflict.size() >= 2) {
                appendAmbiguity(extraction, usccAmbiguity(usccConflict));
            } else {
                String usccSnap = stringVal(valueSnapshot.get("unifiedSocialCreditCode"));
                if (usccSnap != null && !usccSnap.isBlank()) {
                    appendAmbiguity(
                            extraction, adoptOrClearUnifiedSocialCreditWhenMultiEnterpriseSingle(usccSnap));
                }
            }
            addFamilyAmbiguities(extraction, raw, valueSnapshot, enterpriseFamilyKeysForBulkAmbiguity());
            appendReplyHint(extraction);
        } else {
            if (conflictCluster.size() >= 2 && !hasAmbiguityFor(extraction, "companyName")) {
                patch.remove("companyName");
                patch.remove("companyShortName");
                appendAmbiguity(extraction, companyNameAmbiguity(conflictCluster));
                appendReplyHint(extraction);
            }
            if (usccConflict.size() >= 2 && !hasAmbiguityFor(extraction, "unifiedSocialCreditCode")) {
                patch.remove("unifiedSocialCreditCode");
                appendAmbiguity(extraction, usccAmbiguity(usccConflict));
                appendReplyHint(extraction);
            }
        }

        if (multiTransport) {
            stripAmbiguitiesForFieldKeys(extraction, TRANSPORT_KEY_SET);
            removeKeys(patch, TRANSPORT_KEY_SET);
            addFamilyAmbiguities(extraction, raw, valueSnapshot, TRANSPORT_PERMIT_KEYS);
            appendReplyHint(extraction);
        }

        if (multiSafety) {
            stripAmbiguitiesForFieldKeys(extraction, SAFETY_KEY_SET);
            removeKeys(patch, SAFETY_KEY_SET);
            addFamilyAmbiguities(extraction, raw, valueSnapshot, SAFETY_PERMIT_KEYS);
            appendReplyHint(extraction);
        }

        extraction.multiEnterpriseConflictApplied = multiEnterprise;
        extraction.multiTransportConflictApplied = multiTransport;
        extraction.multiSafetyConflictApplied = multiSafety;
    }

    private static void removeKeys(LinkedHashMap<String, Object> patch, Set<String> keys) {
        for (String k : keys) {
            patch.remove(k);
        }
    }

    /** 优先使用互斥簇，其次 orgLike，否则退回原始企业名候选（含未通过 looksLikeOrgName 过滤的证面串）。 */
    private static List<String> pickCompanyNameDisambiguationOptions(
            List<String> conflictCluster, List<String> orgLike, LinkedHashSet<String> companyCandidates) {
        if (conflictCluster != null && conflictCluster.size() >= 2) {
            return new ArrayList<>(conflictCluster);
        }
        if (orgLike != null && orgLike.size() >= 2) {
            return new ArrayList<>(orgLike.subList(0, Math.min(8, orgLike.size())));
        }
        if (companyCandidates != null && companyCandidates.size() >= 2) {
            List<String> out = new ArrayList<>();
            for (String c : companyCandidates) {
                out.add(c);
                if (out.size() >= 2) {
                    break;
                }
            }
            return out;
        }
        return List.of();
    }

    private static void stripAmbiguitiesForFieldKeys(FormVisionExtraction extraction, Set<String> fieldKeys) {
        if (extraction.ambiguities == null || extraction.ambiguities.isEmpty()) {
            return;
        }
        List<AmbiguousFieldDto> kept = new ArrayList<>();
        for (AmbiguousFieldDto a : extraction.ambiguities) {
            if (a == null || a.fieldKey == null || fieldKeys.contains(a.fieldKey)) {
                continue;
            }
            kept.add(a);
        }
        extraction.ambiguities = kept;
    }

    /**
     * 对族内每个字段：合并 raw 单键归一化后的字符串候选 + 快照中的单值；≥2 个互斥候选则两选项；单候选则「采用 /
     * 清空」。
     */
    private static void addFamilyAmbiguities(
            FormVisionExtraction extraction,
            Map<String, Object> raw,
            LinkedHashMap<String, Object> snapshot,
            List<String> fieldKeys) {
        for (String fieldKey : fieldKeys) {
            LinkedHashSet<String> distinct = collectDistinctStringValuesForCanonical(raw, fieldKey);
            String snap = stringVal(snapshot.get(fieldKey));
            if (snap != null && !snap.isBlank()) {
                distinct.add(snap.trim());
            }
            if (distinct.isEmpty()) {
                continue;
            }
            List<String> values = new ArrayList<>(distinct);
            AmbiguousFieldDto dto = new AmbiguousFieldDto();
            dto.fieldKey = fieldKey;

            if (values.size() >= 2) {
                dto.questionForUser = familyAmbiguityQuestionTwoCandidates(fieldKey);
                String v0 = truncateForOption(values.get(0));
                String v1 = truncateForOption(values.get(1));
                dto.options.add(new AmbiguousOptionDto(fieldKey + "_a", "候选一：" + v0, values.get(0)));
                dto.options.add(new AmbiguousOptionDto(fieldKey + "_b", "候选二：" + v1, values.get(1)));
            } else {
                dto.questionForUser = familyAmbiguityQuestionSingleCandidate(fieldKey);
                String v = values.get(0);
                dto.options.add(
                        new AmbiguousOptionDto(
                                fieldKey + "_use",
                                optionLabelUseRecognizedContent(fieldKey, v),
                                v));
                dto.options.add(
                        new AmbiguousOptionDto(
                                fieldKey + "_clear",
                                optionLabelClearRecognizedField(fieldKey),
                                CLEAR_FIELD_SENTINEL));
            }
            appendAmbiguity(extraction, dto);
        }
    }

    private static String fieldLabelZh(String fieldKey) {
        return switch (fieldKey) {
            case "companyName" -> "公司名称";
            case "companyShortName" -> "公司简称";
            case "formerName" -> "曾用名";
            case "unifiedSocialCreditCode" -> "统一社会信用代码";
            case "enterpriseNature" -> "企业性质";
            case "enterpriseType" -> "企业类型";
            case "registeredRegion" -> "注册地行政区划";
            case "registeredAddressDetail" -> "注册地址";
            case "actualLocation" -> "实际经营地址";
            case "registeredZip" -> "注册地邮编";
            case "registrationDate" -> "成立日期 / 注册日期";
            case "registeredCapital" -> "注册资本";
            case "businessScope" -> "经营范围";
            case "legalRepresentative" -> "法定代表人";
            case "transportAdminLicenseName" -> "运输许可证照名称";
            case "transportLicenseNo" -> "运输许可证编号";
            case "transportLicenseValidityMode" -> "运输证有效期模式";
            case "transportLicenseValidityRange" -> "运输证有效期";
            case "transportIssuingAuthority" -> "运输证发证机关";
            case "transportLegalRepresentative" -> "运输证企业负责人";
            case "safetyAdminLicenseName" -> "安全生产/危化证照名称";
            case "safetyLicenseNo" -> "安全生产/危化许可证编号";
            case "safetyLicenseValidityMode" -> "安全生产证有效期模式";
            case "safetyLicenseValidityRange" -> "安全生产证有效期";
            case "safetyIssuingAuthority" -> "安全生产证发证机关";
            case "safetyLegalRepresentative" -> "安全生产证法定代表人/负责人";
            default -> fieldKey;
        };
    }

    private static String truncateForOption(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        if (t.length() <= 120) {
            return t;
        }
        return t.substring(0, 117) + "…";
    }

    /** 双候选：问题区只点明字段与「二选一」，具体字面量在 Radio「候选一/二」中展示。 */
    private static String familyAmbiguityQuestionTwoCandidates(String fieldKey) {
        return "「" + fieldLabelZh(fieldKey) + "」：两条识别不一致，请对照证面在下方择一。";
    }

    /** 单候选（采用 / 清空）：略长说明，因需在「采用识别字面量」与「清空」之间做决策。 */
    private static String familyAmbiguityQuestionSingleCandidate(String fieldKey) {
        return "「"
                + fieldLabelZh(fieldKey)
                + "」（"
                + fieldKey
                + "）：多证信息互斥，无法自动唯一绑定到单一证照。请对照证面在下方选项中择一。";
    }

    /**
     * 单候选时「采用」项：必须把识别字面量写进 label，右侧确认区才有可核对的实际意义（与「候选一/二」风格一致）。
     */
    private static String optionLabelUseRecognizedContent(String fieldKey, String recognizedValue) {
        return "「"
                + fieldLabelZh(fieldKey)
                + "」识别内容：「"
                + truncateForOption(recognizedValue)
                + "」";
    }

    private static String optionLabelClearRecognizedField(String fieldKey) {
        return "不采用上述识别内容，清空「" + fieldLabelZh(fieldKey) + "」（请稍后按证面手填）";
    }

    /**
     * 将 raw 中<strong>每个键值对单独</strong>经 {@link FormVisionPatchNormalizer#normalize} 归一，收集映射到
     * {@code canonicalKey} 的字符串取值并去重，用于从「多前缀键 / 长 OCR」中恢复多候选。
     */
    private static LinkedHashSet<String> collectDistinctStringValuesForCanonical(
            Map<String, Object> raw, String canonicalKey) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null || canonicalKey == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            LinkedHashMap<String, Object> one = new LinkedHashMap<>();
            one.put(e.getKey(), e.getValue());
            LinkedHashMap<String, Object> n = FormVisionPatchNormalizer.normalize(one);
            Object v = n.get(canonicalKey);
            if (v instanceof String s && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    private static List<String> buildPermitNoConflictCluster(List<String> nos) {
        LinkedHashSet<String> inConflict = new LinkedHashSet<>();
        for (int i = 0; i < nos.size(); i++) {
            for (int j = i + 1; j < nos.size(); j++) {
                if (incompatiblePermitNo(nos.get(i), nos.get(j))) {
                    inConflict.add(nos.get(i));
                    inConflict.add(nos.get(j));
                }
            }
        }
        return new ArrayList<>(inConflict);
    }

    private static boolean incompatiblePermitNo(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = a.trim();
        String y = b.trim();
        if (x.length() < 4 || y.length() < 4) {
            return false;
        }
        if (x.equalsIgnoreCase(y)) {
            return false;
        }
        if (x.contains(y) || y.contains(x)) {
            return false;
        }
        return true;
    }

    private static LinkedHashMap<String, Object> ensureMutablePatch(FormVisionExtraction extraction) {
        if (!(extraction.formPatch instanceof LinkedHashMap<?, ?>)) {
            extraction.formPatch = new LinkedHashMap<>(extraction.formPatch);
        }
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, Object> patch = (LinkedHashMap<String, Object>) extraction.formPatch;
        return patch;
    }

    private static void appendReplyHint(FormVisionExtraction extraction) {
        String hint =
                "\n\n【系统提示】检测到多张证照存在互斥信息，已撤销相关自动回填字段；"
                        + "请在右侧表单上方黄色「需您确认」区域<strong>逐条</strong>点选后再继续；"
                        + "不可靠项请选择「不采用上述识别内容」类选项以清空字段。";
        String r = extraction.reply;
        if (r == null || r.isBlank()) {
            extraction.reply = hint.trim();
        } else if (!r.contains("【系统提示】检测到多张证照存在互斥信息")) {
            extraction.reply = r + hint;
        }
    }

    private static boolean hasAmbiguityFor(FormVisionExtraction extraction, String fieldKey) {
        if (extraction.ambiguities == null) {
            return false;
        }
        for (AmbiguousFieldDto a : extraction.ambiguities) {
            if (fieldKey.equals(a.fieldKey)) {
                return true;
            }
        }
        return false;
    }

    private static void appendAmbiguity(FormVisionExtraction extraction, AmbiguousFieldDto dto) {
        List<AmbiguousFieldDto> list = mutableAmbiguities(extraction);
        list.add(dto);
        extraction.ambiguities = list;
    }

    private static List<AmbiguousFieldDto> mutableAmbiguities(FormVisionExtraction extraction) {
        if (extraction.ambiguities == null || extraction.ambiguities.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(extraction.ambiguities);
    }

    private static AmbiguousFieldDto companyNameAmbiguity(List<String> names) {
        AmbiguousFieldDto a = new AmbiguousFieldDto();
        a.fieldKey = "companyName";
        a.questionForUser =
                "多张证照上出现不同的「企业 / 业户名称」，无法自动确定表单「公司名称」应以哪一证为准；请任选其一。";
        int i = 0;
        for (String n : names) {
            String id = "ent_" + (i++);
            a.options.add(new AmbiguousOptionDto(id, "证照所示企业名称：" + n, n));
        }
        return a;
    }

    private static AmbiguousFieldDto usccAmbiguity(List<String> codes) {
        AmbiguousFieldDto a = new AmbiguousFieldDto();
        a.fieldKey = "unifiedSocialCreditCode";
        a.questionForUser =
                "多张证照上出现不同的统一社会信用代码（或含掩码与完整号码混排），无法自动确定应以哪一证为准；请任选其一。";
        int i = 0;
        for (String c : codes) {
            String id = "uscc_" + (i++);
            a.options.add(new AmbiguousOptionDto(id, "证照所示统一社会信用代码：" + c, c));
        }
        return a;
    }

    /**
     * 多主体工商场景下 patch 已剔除统一码；若 OCR/模型仅收敛到一条号码，仍须用户显式「采用 / 清空」，避免与未选企业误绑。
     */
    private static AmbiguousFieldDto adoptOrClearUnifiedSocialCreditWhenMultiEnterpriseSingle(String code) {
        String trimmed = code.trim();
        AmbiguousFieldDto a = new AmbiguousFieldDto();
        a.fieldKey = "unifiedSocialCreditCode";
        a.questionForUser =
                "已检出多张证照疑似分属不同主体，系统无法自动判定下述统一社会信用代码与哪一证严格对应；若与所选「公司名称」一致请点「采用」，否则请清空后按证面手填。";
        a.options.add(
                new AmbiguousOptionDto(
                        "uscc_use",
                        optionLabelUseRecognizedContent("unifiedSocialCreditCode", trimmed),
                        trimmed));
        a.options.add(
                new AmbiguousOptionDto(
                        "uscc_clear",
                        optionLabelClearRecognizedField("unifiedSocialCreditCode"),
                        CLEAR_FIELD_SENTINEL));
        return a;
    }

    private static List<String> buildConflictCluster(List<String> orgLike) {
        LinkedHashSet<String> inConflict = new LinkedHashSet<>();
        for (int i = 0; i < orgLike.size(); i++) {
            for (int j = i + 1; j < orgLike.size(); j++) {
                if (incompatibleOrgNames(orgLike.get(i), orgLike.get(j))) {
                    inConflict.add(orgLike.get(i));
                    inConflict.add(orgLike.get(j));
                }
            }
        }
        return new ArrayList<>(inConflict);
    }

    private static List<String> buildUsccConflictCluster(List<String> codes) {
        LinkedHashSet<String> inConflict = new LinkedHashSet<>();
        for (int i = 0; i < codes.size(); i++) {
            for (int j = i + 1; j < codes.size(); j++) {
                if (incompatibleUscc(codes.get(i), codes.get(j))) {
                    inConflict.add(codes.get(i));
                    inConflict.add(codes.get(j));
                }
            }
        }
        return new ArrayList<>(inConflict);
    }

    private static boolean incompatibleOrgNames(String a, String b) {
        String na = normalizeOrg(a);
        String nb = normalizeOrg(b);
        if (na.length() < 4 || nb.length() < 4) {
            return false;
        }
        if (na.equals(nb)) {
            return false;
        }
        if (na.contains(nb) || nb.contains(na)) {
            return false;
        }
        return true;
    }

    private static String normalizeOrg(String s) {
        return s.replace('\u3000', ' ')
                .trim()
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private static boolean looksLikeOrgName(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        if (t.length() < 4) {
            return false;
        }
        return ORG_MARKERS.matcher(t).find() || t.length() >= 6;
    }

    private static boolean incompatibleUscc(String a, String b) {
        String na = normalizeUscc(a);
        String nb = normalizeUscc(b);
        if (na.length() < 10 || nb.length() < 10) {
            return false;
        }
        return !na.equals(nb);
    }

    private static String normalizeUscc(String s) {
        return s.replaceAll("\\s", "").replace("*", "").toUpperCase(Locale.ROOT);
    }

    private static void collectCompanyNamesFromRaw(Map<String, Object> raw, LinkedHashSet<String> out) {
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey() == null || !(e.getValue() instanceof String)) {
                continue;
            }
            String k = e.getKey().toLowerCase(Locale.ROOT);
            String v = ((String) e.getValue()).trim();
            if (v.isEmpty()) {
                continue;
            }
            if ("companyname".equals(k)
                    || "enterprisename".equals(k)
                    || "enterprise_name".equals(k)
                    || "company_name".equals(k)
                    || "name".equals(k)) {
                out.add(v);
                continue;
            }
            if (k.contains("enterprise") && k.contains("name") && !k.contains("type")) {
                out.add(v);
                continue;
            }
            if (k.contains("businesslicense") && (k.contains("name") || k.contains("title"))) {
                out.add(v);
                continue;
            }
            if ((k.contains("safety") || k.contains("hazard")) && k.contains("company")) {
                out.add(v);
                continue;
            }
            if (k.contains("transport") && k.contains("company")) {
                out.add(v);
                continue;
            }
            if (k.contains("safety") && k.contains("holder")) {
                out.add(v);
                continue;
            }
            if (k.contains("transport") && k.contains("holder")) {
                out.add(v);
                continue;
            }
        }
    }

    private static void collectUsccFromRaw(Map<String, Object> raw, LinkedHashSet<String> out) {
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey() == null || !(e.getValue() instanceof String)) {
                continue;
            }
            String k = e.getKey().toLowerCase(Locale.ROOT);
            String v = ((String) e.getValue()).trim();
            if (v.length() < 10) {
                continue;
            }
            if (!(k.contains("unified") || k.contains("credit") || k.contains("socialcredit"))) {
                continue;
            }
            String digits = v.replaceAll("[^0-9A-Za-z*]", "");
            if (digits.length() >= 15) {
                out.add(v);
            }
        }
    }

    private static void collectUsccFromRawStringValues(Map<String, Object> raw, LinkedHashSet<String> out) {
        if (raw == null) {
            return;
        }
        for (Object o : raw.values()) {
            if (!(o instanceof String s) || s.length() < 18) {
                continue;
            }
            String compact = s.replaceAll("\\s+", "");
            var m = USCC_18_IN_TEXT.matcher(compact);
            while (m.find()) {
                String g = m.group(1).trim();
                if (plausibleUsccToken(g)) {
                    out.add(g);
                }
            }
        }
    }

    private static boolean plausibleUsccToken(String g) {
        if (g == null || g.length() != 18) {
            return false;
        }
        int digits = 0;
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c != '*' && Character.isDigit(c)) {
                digits++;
            }
        }
        return digits >= 5;
    }

    private static void collectZhLabeledCompanyNamesFromRawStringValues(
            Map<String, Object> raw, LinkedHashSet<String> out) {
        if (raw == null) {
            return;
        }
        for (Object o : raw.values()) {
            if (!(o instanceof String s) || s.length() < 6) {
                continue;
            }
            var m = ZH_ENTITY_NAME_AFTER_LABEL.matcher(s);
            while (m.find()) {
                String name = m.group(1).trim();
                if (looksLikeOrgName(name)) {
                    out.add(name);
                }
            }
        }
    }

    private static void extractEmbeddedNames(String blob, Pattern p, LinkedHashSet<String> out) {
        if (blob == null || blob.isBlank()) {
            return;
        }
        var m = p.matcher(blob);
        while (m.find()) {
            String g = m.group(1).trim();
            if (!g.isEmpty()) {
                out.add(g);
            }
        }
    }

    private static void addIfNonBlank(LinkedHashSet<String> out, String s) {
        if (s != null && !s.isBlank()) {
            out.add(s.trim());
        }
    }

    private static String stringVal(Object o) {
        return o instanceof String ? (String) o : null;
    }
}
