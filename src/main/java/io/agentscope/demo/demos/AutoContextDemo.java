package io.agentscope.demo.demos;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.ContextOffloadTool;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.DashScopeSupport;

/**
 * AutoContextMemory with {@link ContextOffloadTool} registered — suitable for long-running chats
 * where {@link io.agentscope.core.memory.InMemoryMemory} would grow without bound.
 */
public final class AutoContextDemo {

    private AutoContextDemo() {}

    public static void run() {
        var summarizationModel = DashScopeSupport.chatModel("qwen-max", true);
        AutoContextConfig config =
                AutoContextConfig.builder()
                        .msgThreshold(8)
                        .lastKeep(4)
                        .tokenRatio(0.35)
                        .build();
        AutoContextMemory memory = new AutoContextMemory(config, summarizationModel);

        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new ContextOffloadTool(memory));

        ReActAgent agent =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("You answer briefly.")
                        .model(DashScopeSupport.chatModel("qwen-max", true))
                        .memory(memory)
                        .toolkit(toolkit)
                        .build();

        for (int i = 0; i < 6; i++) {
            Msg m =
                    Msg.builder()
                            .role(MsgRole.USER)
                            .content(
                                    TextBlock.builder()
                                            .text("Turn " + i + ": reply with exactly: ok-" + i)
                                            .build())
                            .build();
            agent.call(m).block();
        }

        Msg probe =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text("Summarize prior turns in one short sentence.")
                                        .build())
                        .build();
        Msg out = agent.call(probe).block();
        System.out.println(out.getTextContent());
    }
}
