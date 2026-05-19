package io.agentscope.demo.app.web.dto;

import java.util.List;

/**
 * 单文件解析任务的当前快照：百分比、阶段枚举与步骤条数据。
 *
 * @param jobId 任务 id
 * @param fileName 展示用文件名
 * @param percent 总进度 0–100
 * @param phase 粗粒度阶段（如 reading / parsing / done），便于前端区分样式
 * @param steps 纵向步骤列表，每项含名称与三态（wait / process / finish）
 */
public record FileJobStatusResponse(
        String jobId,
        String fileName,
        int percent,
        String phase,
        List<ParseStepView> steps) {}
