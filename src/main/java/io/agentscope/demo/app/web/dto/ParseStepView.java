package io.agentscope.demo.app.web.dto;

/**
 * 文件解析进度里的一步：名称 + UI 状态机状态。
 *
 * @param name 步骤标题（中文）
 * @param state {@code wait} 未开始 / {@code process} 进行中 / {@code finish} 已完成
 */
public record ParseStepView(String name, String state) {}
