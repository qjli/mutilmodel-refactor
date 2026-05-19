//package io.agentscope.demo.app.web;
//
//import io.agentscope.demo.app.service.FileJobService;
//import io.agentscope.demo.app.web.dto.FileAnalyzeResponse;
//import io.agentscope.demo.app.web.dto.FileJobStatusResponse;
//import java.io.IOException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestPart;
//import org.springframework.web.bind.annotation.RestController;
//import org.springframework.web.multipart.MultipartFile;
//
///**
// * 与「单文件解析任务」相关的演示接口：受理上传并轮询任务状态（非 AgentScope 视觉主流程）。
// *
// * <p>多图表单识别请使用 {@link FormVisionController} 的 SSE 接口。
// */
//@RestController
//@RequestMapping("/api/files")
//public class FileController {
//
//    private static final Logger log = LoggerFactory.getLogger(FileController.class);
//
//    private final FileJobService fileJobService;
//
//    public FileController(FileJobService fileJobService) {
//        this.fileJobService = fileJobService;
//    }
//
//    /** 受理单个 {@code multipart file} 字段，返回 {@code jobId} 供轮询。 */
//    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public FileAnalyzeResponse analyze(@RequestPart("file") MultipartFile file) throws IOException {
//        FileAnalyzeResponse r = fileJobService.start(file);
//        log.info("[file-demo] analyze started jobId={} fileName={}", r.jobId(), r.fileName());
//        return r;
//    }
//
//    /** 查询模拟解析进度（内存任务表，进程重启后丢失）。 */
//    @GetMapping("/{jobId}")
//    public FileJobStatusResponse status(@PathVariable String jobId) {
//        FileJobStatusResponse s = fileJobService.status(jobId);
//        log.debug("[file-demo] status jobId={} percent={} phase={}", jobId, s.percent(), s.phase());
//        return s;
//    }
//}
