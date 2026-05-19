package io.agentscope.demo;

import io.agentscope.demo.demos.AutoContextDemo;
import io.agentscope.demo.demos.Mem0LongTermDemo;
import io.agentscope.demo.demos.SessionPersistenceDemo;
import io.agentscope.demo.demos.SkillDemo;
import io.agentscope.demo.demos.StructuredOutputDemo;
import io.agentscope.demo.demos.VisionDemo;
import java.nio.file.Path;

/** Dispatch demos by name. Requires {@code DASHSCOPE_API_KEY} except {@code mem0} also needs Mem0. */
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
            case "autocontext" -> AutoContextDemo.run();
            case "skill" -> SkillDemo.run();
            case "mem0" -> Mem0LongTermDemo.run();
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
                        + "<structured|structured-prompt|vision|session|autocontext|skill|mem0> "
                        + "[sessionId]");
        System.err.println("Env: DASHSCOPE_API_KEY (required for all except none)");
        System.err.println("Env: MEM0_API_KEY (+ optional MEM0_API_BASE_URL, MEM0_API_TYPE) for mem0");
    }
}
