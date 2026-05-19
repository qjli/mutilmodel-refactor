package io.agentscope.demo.demos;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.DashScopeSupport;

/**
 * 结构化输出演示：{@code agent.call(msg, Schema.class)}，对比 {@link StructuredOutputReminder#TOOL_CHOICE}
 * 与 {@link StructuredOutputReminder#PROMPT} 两种提醒模式。
 */
public final class StructuredOutputDemo {

    private StructuredOutputDemo() {}

    public static void runToolChoice() {
        run(StructuredOutputReminder.TOOL_CHOICE);
    }

    public static void runPromptMode() {
        run(StructuredOutputReminder.PROMPT);
    }

    public static void run(StructuredOutputReminder reminder) {
        ReActAgent agent =
                ReActAgent.builder()
                        .name("AnalysisAgent")
                        .sysPrompt(
                                "You extract product offers as structured data. "
                                        + "Respond only via the required structured output channel.")
                        .model(DashScopeSupport.chatModel("qwen-max", true))
                        .toolkit(new Toolkit())
                        .memory(new InMemoryMemory())
                        .structuredOutputReminder(reminder)
                        .build();

        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "Extract: Acme Noise Cancelling Earbuds, "
                                                        + "price USD 79.99, tags: audio, travel, gift")
                                        .build())
                        .build();

        Msg response = agent.call(userMsg, ProductOffer.class).block();
        ProductOffer data = response.getStructuredData(ProductOffer.class);

        if (data.priceUsd != null && data.priceUsd < 0) {
            throw new IllegalArgumentException("Invalid price from model output");
        }

        System.out.println("name=" + data.name);
        System.out.println("priceUsd=" + data.priceUsd);
        System.out.println("tags=" + data.tags);
    }

    /** DTO for structured output: no-arg ctor + Jackson mapping. */
    public static class ProductOffer {
        @JsonProperty("product_name")
        public String name;

        @JsonProperty("price_usd")
        public Double priceUsd;

        public java.util.List<String> tags;

        public ProductOffer() {}
    }
}
