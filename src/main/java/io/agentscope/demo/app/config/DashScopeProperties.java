package io.agentscope.demo.app.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 阿里云 DashScope（百炼）相关配置（前缀 {@code dashscope.*}）。
 *
 * <p>在 {@link Validated} 下做 Bean 校验：缺少 {@code api-key} 时应用启动失败，避免运行期才暴露配置错误。
 */
@ConfigurationProperties(prefix = "dashscope")
@Validated
public class DashScopeProperties {

    /**
     * DashScope API Key。推荐通过环境变量注入，勿将真实密钥提交到仓库：
     * {@code DASHSCOPE_API_KEY}；本地可在未纳入版本控制的 {@code application-local.yml} 中填写。
     */
    @NotBlank(message = "必须在配置中设置 dashscope.api-key（或通过环境变量 DASHSCOPE_API_KEY 提供）")
    private String apiKey = "";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    /**
     * 是否为视觉模型开启 DashScope extended thinking。默认 {@code false}：{@code qwen-vl-max} 在百炼侧不支持
     * 正 {@code thinking_budget}，开启会导致 400。
     */
    private boolean visionEnableThinking = false;

    public boolean isVisionEnableThinking() {
        return visionEnableThinking;
    }

    public void setVisionEnableThinking(boolean visionEnableThinking) {
        this.visionEnableThinking = visionEnableThinking;
    }

    /**
     * 仅当 {@link #isVisionEnableThinking()} 为 true 时使用：传给 DashScope 的 {@code thinking_budget}。
     */
    @Min(1)
    @Max(131072)
    private int visionThinkingBudget = 8192;

    public int getVisionThinkingBudget() {
        return visionThinkingBudget;
    }

    public void setVisionThinkingBudget(int visionThinkingBudget) {
        this.visionThinkingBudget = visionThinkingBudget;
    }
}
