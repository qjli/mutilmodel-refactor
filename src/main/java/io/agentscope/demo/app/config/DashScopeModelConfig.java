package io.agentscope.demo.app.config;

import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.demo.DashScopeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 声明本应用用到的 {@link DashScopeChatModel} Bean（文本对话与视觉识别各一枚，按名称区分注入）。
 *
 * <p>与 {@link DashScopeSupport} 静态工厂配合，将 {@link DashScopeProperties} 中的密钥与开关落到具体模型实例。
 */
@Configuration
@EnableConfigurationProperties(DashScopeProperties.class)
public class DashScopeModelConfig {

    private static final Logger log = LoggerFactory.getLogger(DashScopeModelConfig.class);

    /**
     * 文本对话：{@code qwen-max}，非流式，便于 {@link io.agentscope.core.agent.CallableAgent#call} 同步阻塞返回。
     */
    @Bean(name = "chatDashScopeChatModel")
    public DashScopeChatModel chatDashScopeChatModel(DashScopeProperties properties) {
        log.info("[dashscope] chat bean model=qwen-max stream=false");
        return DashScopeSupport.chatModel(properties.getApiKey(), "qwen-max", false);
    }

    /**
     * 多图表单识别：默认 {@link DashScopeSupport#visionModel} 中的 {@code qwen3-vl-plus}，流式，供 ReActAgent
     * 流事件推送前端。
     *
     * <p><b>说明：</b>部分视觉模型与 extended thinking / {@code thinking_budget} 组合不兼容时会返回 400；请按
     * 百炼文档核对当前模型后再打开 {@link DashScopeProperties#isVisionEnableThinking()}。
     */
    @Bean(name = "formVisionDashScopeChatModel")
    public DashScopeChatModel formVisionDashScopeChatModel(DashScopeProperties properties) {
        if (properties.isVisionEnableThinking()) {
            log.info(
                    "[dashscope] vision bean model=qwen3-vl-plus stream=true enableThinking=true thinkingBudget={}",
                    properties.getVisionThinkingBudget());
            return DashScopeSupport.visionModel(
                    properties.getApiKey(), true, true, properties.getVisionThinkingBudget());
        }
        log.info("[dashscope] vision bean model=qwen3-vl-plus stream=true enableThinking=false");
        return DashScopeSupport.visionModel(properties.getApiKey(), true, false);
    }
}
