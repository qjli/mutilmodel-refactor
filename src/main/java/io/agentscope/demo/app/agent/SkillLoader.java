package io.agentscope.demo.app.agent;

import io.agentscope.core.skill.AgentSkill;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 从 classpath {@code /skills/*.md} 加载 {@link AgentSkill}。 */
public final class SkillLoader {

    private static final Logger log = LoggerFactory.getLogger(SkillLoader.class);

    private SkillLoader() {}

    /** 证照影像 → form_patch / ambiguities / reply（classpath {@code /skills/form_vision_fill.md}）。 */
    public static AgentSkill formVisionFill() throws IOException {
        return load("form_vision_fill", "Enterprise form vision: camelCase form_patch, ambiguities, reply sections.");
    }

    /** 纯文本对话：如何使用、缺哪些材料（classpath {@code /skills/upload_guide_dialog.md}）。 */
    public static AgentSkill uploadGuideDialog() throws IOException {
        return load(
                "upload_guide_dialog",
                "Text chat only: how to upload, missing materials. Reply rules; upload_guide is built by the server.");
    }

    private static AgentSkill load(String name, String description) throws IOException {
        String path = "/skills/" + name + ".md";
        String md;
        try (InputStream in =
                Objects.requireNonNull(SkillLoader.class.getResourceAsStream(path), "classpath:" + path)) {
            md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        log.debug("[skill] loaded {} chars={}", name, md.length());
        return AgentSkill.builder().name(name).description(description).skillContent(md).build();
    }
}
