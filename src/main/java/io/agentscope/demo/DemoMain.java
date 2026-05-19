package io.agentscope.demo;

import io.agentscope.demo.demos.SessionPersistenceDemo;
import io.agentscope.demo.demos.SkillDemo;
import io.agentscope.demo.demos.StructuredOutputDemo;
import io.agentscope.demo.demos.VisionDemo;
import java.nio.file.Path;

/**
 * 命令行演示入口（非 Spring Boot）：按子命令运行结构化输出、视觉、Session 持久化、技能等示例。
 *
 * <p>需设置环境变量 {@code DASHSCOPE_API_KEY}。
 */
public final class DemoMain {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
        }
        String cmd = args[0].trim().toLowerCase();
        switch (cmd) {
            case "structured" -> StructuredOutputDemo.runToolChoice();
            case "structured-prompt" -> StructuredOutputDemo.runPromptMode();
            case "vision" -> VisionDemo.run();
            case "session" -> {
                Path root =
                        Path.of(System.getProperty("user.home"), ".agentscope", "multimodal-demo");
                String sid = args.length > 1 ? args[1] : "demo_session_001";
                SessionPersistenceDemo.run(root, sid);
            }
            case "skill" -> SkillDemo.run();
            default -> {
                System.err.println("Unknown command: " + cmd);
                printUsage();
                System.exit(2);
            }
        }
    }

    private static void printUsage() {
        System.err.println(
                "Usage: java -cp <...> io.agentscope.demo.DemoMain "
                        + "<structured|structured-prompt|vision|session|skill> "
                        + "[sessionId]");
        System.err.println("Env: DASHSCOPE_API_KEY (required)");
    }
}
