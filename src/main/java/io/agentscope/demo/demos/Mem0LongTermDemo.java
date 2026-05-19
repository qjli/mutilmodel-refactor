package io.agentscope.demo.demos;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.mem0.Mem0ApiType;
import io.agentscope.core.memory.mem0.Mem0LongTermMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.DashScopeSupport;

/**
 * Long-term memory via Mem0. Set {@code MEM0_API_KEY}; for self-hosted Mem0 set {@code
 * MEM0_API_TYPE=self-hosted} and {@code MEM0_API_BASE_URL}.
 */
public final class Mem0LongTermDemo {

    private Mem0LongTermDemo() {}

    public static void run() {
        String mem0Key = System.getenv("MEM0_API_KEY");
        if (mem0Key == null || mem0Key.isBlank()) {
            throw new IllegalStateException("Set MEM0_API_KEY to run mem0 demo");
        }

        String base =
                firstNonBlank(
                        System.getenv("MEM0_API_BASE_URL"),
                        defaultBaseUrl(System.getenv("MEM0_API_TYPE")));

        Mem0ApiType apiType = parseApiType(System.getenv("MEM0_API_TYPE"));

        Mem0LongTermMemory longTerm =
                Mem0LongTermMemory.builder()
                        .agentName("multimodal-demo")
                        .userId("demo-user")
                        .apiBaseUrl(base)
                        .apiKey(mem0Key)
                        .apiType(apiType)
                        .build();

        ReActAgent agent =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("You are a helpful assistant with long-term memory.")
                        .model(DashScopeSupport.chatModel("qwen-max", true))
                        .toolkit(new Toolkit())
                        .memory(new InMemoryMemory())
                        .longTermMemory(longTerm)
                        .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                        .build();

        Msg m =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "Remember: my favorite demo color is teal. Reply"
                                                        + " OK.")
                                        .build())
                        .build();
        Msg r1 = agent.call(m).block();
        System.out.println("turn1=" + r1.getTextContent());

        Msg recall =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text("What is my favorite demo color? One word answer.")
                                        .build())
                        .build();
        Msg r2 = agent.call(recall).block();
        System.out.println("turn2=" + r2.getTextContent());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b;
    }

    private static String defaultBaseUrl(String typeRaw) {
        Mem0ApiType t = parseApiType(typeRaw);
        return t == Mem0ApiType.SELF_HOSTED ? "http://localhost:8000" : "https://api.mem0.ai";
    }

    private static Mem0ApiType parseApiType(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mem0ApiType.PLATFORM;
        }
        String v = raw.trim().toLowerCase();
        if (v.contains("self")) {
            return Mem0ApiType.SELF_HOSTED;
        }
        return Mem0ApiType.PLATFORM;
    }
}
