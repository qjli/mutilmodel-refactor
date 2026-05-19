package io.agentscope.demo.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

/** 解析视觉请求附带的「当前表单」快照（JSON），供多轮上传歧义合并使用。 */
public final class FormVisionFormContextSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FormVisionFormContextSupport() {}

    /**
     * @param formContextJson 前端 {@code formContext} 字段；可为 {@code null}
     * @return 经 {@link FormVisionPatchNormalizer} 白名单归一化后的 camelCase 键表
     */
    public static Map<String, Object> parseAndNormalize(String formContextJson) {
        if (formContextJson == null || formContextJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw =
                    MAPPER.readValue(formContextJson, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (raw == null || raw.isEmpty()) {
                return Map.of();
            }
            return FormVisionPatchNormalizer.normalize(new LinkedHashMap<>(raw));
        } catch (Exception e) {
            return Map.of();
        }
    }
}
