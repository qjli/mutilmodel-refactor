package io.agentscope.demo;

import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;

/**
 * DashScope（百炼）模型构建工厂：CLI {@link io.agentscope.demo.DemoMain} 与 Spring {@link
 * io.agentscope.demo.app.config.DashScopeModelConfig} 共用，避免重复配置 API Key、流式与 thinking 开关。
 */
public final class DashScopeSupport {

    private DashScopeSupport() {}

    /** 读取必填环境变量；CLI 演示在缺少密钥时快速失败。 */
    public static String requireEnv(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return v;
    }

    private static String requireApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DashScope API key must not be blank");
        }
        return apiKey.trim();
    }

    /** CLI / 示例：从环境变量读取密钥。 */
    public static DashScopeChatModel chatModel(String modelName, boolean stream) {
        return chatModel(requireEnv("DASHSCOPE_API_KEY"), modelName, stream);
    }

    /** 文本对话模型：显式传入 API Key 与模型名（Spring 使用 {@code qwen-max}）。 */
    public static DashScopeChatModel chatModel(String apiKey, String modelName, boolean stream) {
        return DashScopeChatModel.builder()
                .apiKey(requireApiKey(apiKey))
                .modelName(modelName)
                .stream(stream)
                .enableThinking(false)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(GenerateOptions.builder().build())
                .build();
    }

    /** CLI / 示例：从环境变量读取密钥。 */
    public static DashScopeChatModel visionModel(boolean stream) {
        return visionModel(requireEnv("DASHSCOPE_API_KEY"), stream, false);
    }

    public static DashScopeChatModel visionModel(boolean stream, boolean enableThinking) {
        return visionModel(requireEnv("DASHSCOPE_API_KEY"), stream, enableThinking);
    }

    /**
     * Spring 或显式传入配置中的 API Key（例如 {@code dashscope.api-key}），使用默认思考 token 上限。
     *
     * @see #visionModel(String, boolean, boolean, int)
     */
    public static DashScopeChatModel visionModel(String apiKey, boolean stream, boolean enableThinking) {
        return visionModel(apiKey, stream, enableThinking, 8192);
    }

    /**
     * DashScope 在开启 extended thinking 时要求 {@code thinking_budget} 为正整数；仅
     * {@code enableThinking(true)} 而未在 {@link GenerateOptions} 中设置 budget 会触发 400
     * {@code InternalError.Algo.InvalidParameter}。
     */
    public static DashScopeChatModel visionModel(
            String apiKey, boolean stream, boolean enableThinking, int thinkingBudget) {
        GenerateOptions.Builder opts = GenerateOptions.builder();
        if (enableThinking) {
            opts.thinkingBudget(thinkingBudget);
        }
        return DashScopeChatModel.builder()
                .apiKey(requireApiKey(apiKey))
                .modelName("qwen3-vl-plus")
                .stream(stream)
                .enableThinking(enableThinking)
                .formatter(new DashScopeChatFormatter())
                .defaultOptions(opts.build())
                .build();
    }
}
