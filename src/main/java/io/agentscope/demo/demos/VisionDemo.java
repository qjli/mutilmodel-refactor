package io.agentscope.demo.demos;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.DashScopeSupport;
import java.util.List;

/** Minimal vision path: Base64 PNG + {@code DashScopeChatFormatter} + qwen-vl-max. */
public final class VisionDemo {

    private VisionDemo() {}

    public static void run() {
        ReActAgent agent =
                ReActAgent.builder()
                        .name("VisionAssistant")
                        .sysPrompt("You describe images briefly and accurately.")
                        .model(DashScopeSupport.visionModel(true))
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        String redSquareBase64 =
                "iVBORw0KGgoAAAANSUhEUgAAABQAAAAUCAIAAAAC64paAAAAFklEQVR42mP8z8DAwMj4n4FhFIw"
                        + "CMgBmBQEAAhUCYwAAAABJRU5ErkJggg==";

        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                List.of(
                                        TextBlock.builder()
                                                .text("What color is this image? One sentence.")
                                                .build(),
                                        ImageBlock.builder()
                                                .source(
                                                        Base64Source.builder()
                                                                .data(redSquareBase64)
                                                                .mediaType("image/png")
                                                                .build())
                                                .build()))
                        .build();

        Msg response = agent.call(userMsg).block();
        System.out.println(response.getTextContent());
    }
}
