package io.agentscope.demo.demos;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.session.JsonSession;
import io.agentscope.core.session.Session;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.DashScopeSupport;
import io.agentscope.demo.SessionIds;
import java.nio.file.Path;

/**
 * Non-interactive session round-trip: {@code loadIfExists} → turn → {@code saveTo} → new agent
 * loads prior memory using a sanitized session id.
 */
public final class SessionPersistenceDemo {

    private SessionPersistenceDemo() {}

    public static void run(Path sessionRoot, String rawSessionId) {
        String sessionId = SessionIds.requireSafeSessionId(rawSessionId);
        Session session = new JsonSession(sessionRoot);

        InMemoryMemory memoryRound1 = new InMemoryMemory();
        ReActAgent agent1 =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("You are a concise assistant.")
                        .toolkit(new Toolkit())
                        .memory(memoryRound1)
                        .model(DashScopeSupport.chatModel("qwen-max", true))
                        .build();

        agent1.loadIfExists(session, sessionId);

        Msg seed =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "Remember this codeword for later turns: "
                                                        + "AGENTSCOPE_SESSION_DEMO")
                                        .build())
                        .build();
        agent1.call(seed).block();
        agent1.saveTo(session, sessionId);

        InMemoryMemory memoryRound2 = new InMemoryMemory();
        ReActAgent agent2 =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("You are a concise assistant.")
                        .toolkit(new Toolkit())
                        .memory(memoryRound2)
                        .model(DashScopeSupport.chatModel("qwen-max", true))
                        .build();

        boolean loaded = agent2.loadIfExists(session, sessionId);
        if (!loaded) {
            throw new IllegalStateException("Expected session to exist after save");
        }

        Msg followUp =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text("What was the codeword I asked you to remember?")
                                        .build())
                        .build();
        Msg answer = agent2.call(followUp).block();
        agent2.saveTo(session, sessionId);

        System.out.println(answer.getTextContent());
    }
}
