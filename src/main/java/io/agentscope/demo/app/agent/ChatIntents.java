package io.agentscope.demo.app.agent;

import io.agentscope.demo.app.upload.MaterialSampleIds;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 文本对话意图：与 {@link io.agentscope.demo.app.service.DemoChatService} 路由一致。 */
public final class ChatIntents {

    private static final Pattern UPLOAD_GUIDE =
            Pattern.compile(
                    "如何使用|怎么用|怎么上传|如何上传|使用说明|新手指南|第一次用|初次使用"
                            + "|本页|本页面|整页|页面上|页面.*上传|页面.*怎么|不会上传|咋上传|咋传|教我上传|上传教程|上传步骤"
                            + "|在哪上传|在哪里上传|上传入口|上传.*按钮|单选|多选|一次.*张|支持.*格式"
                            + "|缺什么|还缺|还需要|还要什么|还要哪些|缺哪些|缺哪|要准备|准备哪些|哪些材料|哪些证照|什么材料|什么照片|什么文件|哪些文件|传什么"
                            + "|样例图|样例|示意图|示范图|材料清单"
                            + "|能做什么|有什么用|本助手\\s*能",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern REMAINING_FILES =
            Pattern.compile(
                    "还需要什么|还需要哪些|还缺什么|还缺哪些|还要上传什么|还要传什么|还要准备什么|"
                            + "没传哪些|没上传哪些|剩下哪些|尚缺哪些|仍缺哪些|缺哪些|"
                            + "需要什么文件|需要哪些文件|要什么文件|"
                            + "哪些(图片|照片|文件|材料|证照).*(还没|尚未|没有)|"
                            + "哪些还没(传|上传)|要(准备|补传)哪些",
                    Pattern.CASE_INSENSITIVE);

    private ChatIntents() {}

    public static boolean uploadGuide(String text) {
        return text != null && UPLOAD_GUIDE.matcher(text).find();
    }

    public static boolean remainingFiles(String text) {
        return text != null && REMAINING_FILES.matcher(text).find();
    }

    public static String coverageSummary(Set<String> coverageIds) {
        if (coverageIds == null || coverageIds.isEmpty()) {
            return "（尚无）";
        }
        List<String> zh = new ArrayList<>();
        for (String id : MaterialSampleIds.CANONICAL_ORDER) {
            if (coverageIds.contains(id)) {
                zh.add(MaterialSampleIds.chineseTitle(id));
            }
        }
        return zh.isEmpty() ? "（尚无）" : String.join("、", zh);
    }

    public static String missingSummary(Set<String> coverageIds) {
        List<String> miss = new ArrayList<>();
        for (String id : MaterialSampleIds.CANONICAL_ORDER) {
            if (coverageIds == null || !coverageIds.contains(id)) {
                miss.add(MaterialSampleIds.chineseTitle(id));
            }
        }
        return String.join("、", miss);
    }
}
