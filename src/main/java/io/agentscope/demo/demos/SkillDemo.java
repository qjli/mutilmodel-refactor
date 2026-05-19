package io.agentscope.demo.demos;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.demo.DashScopeSupport;

/**
 * 通过 {@link SkillBox} 注册小型 {@link AgentSkill} 的 CLI 演示；默认不开启代码执行沙箱。
 *
 * <p>若需沙箱脚本，须显式配置（生产环境勿盲目开启）：
 *
 * <pre>{@code
 * skillBox.codeExecution()
 *     .workDir("/data/agent-workspace")
 *     .includeFolders(Set.of("scripts/", "assets/"))
 *     .includeExtensions(Set.of(".py", ".sh"))
 *     .withShell(customShellToolWithApproval)
 *     .withRead()
 *     .enable();
 * }</pre>
 */
public final class SkillDemo {

    private SkillDemo() {}

    public static void run() {
        Toolkit toolkit = new Toolkit();
        SkillBox skillBox = new SkillBox(toolkit);

        AgentSkill demoSkill =
                AgentSkill.builder()
                        .name("demo_echo_skill")
                        .description(
                                "Use when the user asks to run the demo skill or wants the official"
                                        + " demo slogan.")
                        .skillContent(
                                "# Demo skill\n"
                                        + "When loaded, respond with the exact line:\n"
                                        + "DEMO_SKILL_ACTIVE=v1\n"
                                        + "Do not claim you executed code.\n")
                        .addResource(
                                "references/notes.md",
                                "# Notes\nExtended content lives here for progressive disclosure.\n")
                        .build();

        skillBox.registerSkill(demoSkill);

        ReActAgent agent =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("You are a helpful assistant. Use skills when they match.")
                        .model(DashScopeSupport.chatModel("qwen-max", true))
                        .toolkit(toolkit)
                        .skillBox(skillBox)
                        .memory(new InMemoryMemory())
                        .build();

        Msg userMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(
                                TextBlock.builder()
                                        .text(
                                                "Please activate the demo_echo_skill skill and"
                                                        + " follow its instruction exactly.")
                                        .build())
                        .build();

        Msg response = agent.call(userMsg).block();
        System.out.println(response.getTextContent());
    }
}
