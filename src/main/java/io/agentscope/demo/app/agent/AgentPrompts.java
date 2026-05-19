package io.agentscope.demo.app.agent;

import java.util.Set;

/** 各链路共用的系统提示片段（技能正文在 classpath skills/*.md）。 */
public final class AgentPrompts {

    private static final String PERMIT_NAMES =
            "营业执照、身份证人像面、道路危险货物运输许可证、危险化学品经营许可证";

    private AgentPrompts() {}

    public static String formVisionCore() {
        return "你是企业工商与资质信息填报助手。"
                + "技能 **form_vision_fill** 全文已注入；按其中 camelCase 键、ISO 日期、ambiguities 与 reply 四节骨架执行。"
                + "禁止声称无法加载技能。form_patch 仅写有把握字段；许可证用 safety* / transport* 前缀，勿自造键。"
                + "证照中文名仅允许四种：" + PERMIT_NAMES + "。reply 禁止 Markdown 图片与 http(s) 链接。";
    }

    public static String visionUserPreamble(int imageCount, String fileNames) {
        return "用户已上传 " + imageCount + " 张图片（文件名：" + fileNames + "）。"
                + "按 form_vision_fill 抽取表单字段；upload_guide 须为 null（本链路仅影像分析）。";
    }

    public static String chatStructuredIntro() {
        return formVisionCore() + " 结构化输出类型为 ChatFormAssistantResult（reply、form_patch、upload_guide）。";
    }

    public static String chatNoUploadGuide() {
        return "本轮未加载 upload_guide_dialog；upload_guide 必须为 null；reply 勿引导「见下方材料卡片」。";
    }

    public static String chatUploadGuideGeneral() {
        return "已加载 upload_guide_dialog：在 reply 中说明操作与四类证照（" + PERMIT_NAMES + "）。"
                + "材料示意卡由服务端生成，你可将 upload_guide 置为 null。"
                + "入口为对话列底部「上传文件」；卡片配图为样图非真实证照。";
    }

    public static String chatUploadGuideRemaining(Set<String> coverage) {
        return "本轮为仍缺材料追问；已加载 upload_guide_dialog。"
                + "【本会话材料覆盖推断】（文件名关键词，非图像识别）已出现："
                + ChatIntents.coverageSummary(coverage)
                + "；尚未出现："
                + ChatIntents.missingSummary(coverage)
                + "。reply 仅描述仍缺项；upload_guide 由服务端与推断对齐，你可置为 null。";
    }
}
