package com.example.demo;

import com.example.demo.Exception.ServiceException;
import com.example.demo.entity.Video;
//import com.example.demo.entity.VideoAnalysisTimeline;
//import com.example.demo.mapper.TryJSONMapper;
import com.example.demo.mapper.VideoMapper;
import com.example.demo.service.JsonService;
import com.example.demo.service.SshVideoProcessingServiceImpl;
import com.example.demo.service.VideoAnalysisService;
import com.example.demo.utils.SshUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class DemoApplicationTests {
    @Autowired
    private SshVideoProcessingServiceImpl videoProcessingService;
    @Autowired
    private VideoMapper videoMapper;
    @Autowired
    private VideoAnalysisService videoAnalysisService;
    @Autowired
    private SshVideoProcessingServiceImpl sshVideoProcessingServiceImpl;
    @Value("${temp.dir:temp_videos}")
    private String TEMP_DIR;//app.temp.dir=C:/Users/lzy/Desktop/1/result/origin
    @Value("${processing.dir:processed_videos}")
    private String PROCESSING_DIR;//app.processing.dir=C:/Users/lzy/Desktop/1/result/processed
    @Autowired
    private JsonService jsonService;

    @Test
    public void testServiceInjection() {
        assertNotNull(jsonService);
    }
    @Test
    public void testFindByPath() {
        // 插入测试数据
        Video video = new Video();
        video.setUserId(1L);
        video.setOriginalFilename("originalFilename");
//        video.setOriginalVideoPath("http://172.22.189.77:8080/video/download/f9716836-f23411-4676-a314-4a3cacfa0b88.mp4");
        video.setStatus("completed");
        videoMapper.insert(video);
        // 查询数据
//        Video result = videoMapper.findByPath("http://172.22.189.77:8080/video/download/f9716836-f23411-4676-a314-4a3cacfa0b88.mp4");
//        System.out.println(result);
    }
    @Test
    void test(){
        System.out.println(Paths.get(TEMP_DIR));
//        videoAnalysisService.getAnalysisResultByVideoId(184L);
    }

    @Test
    void download() {
        System.out.println(Paths.get(TEMP_DIR));
        jsonService.parseResultJson(184L,"C:\\Users\\lzy\\Desktop\\1\\result\\processed\\results_a9f9123e-2cbc-4b06-9ecf-56d6acf400aa.json");
    }
    public void a(String videoUrl, String taskId, Path tempDirPath, Path processingDirPath, String ip) {
        // 查找视频记录
        Video video = videoMapper.findByPath(videoUrl);
        // 更新视频状态为处理中
        video.setStatus("processing");
        video.setProgress(0);
        videoMapper.update(video);
        try {
            Long videoId = video.getId();
            // 服务器配置
            String serverHost = "10.11.5.171";  // 服务器IP
            int serverPort = 22;  // SSH端口
            String serverUser = "user3";  // 服务器用户名
            String serverPassword = "123123";  // 服务器密码
            String condaEnv = "cq";
            String mmactionPath = "/home/user3/mmaction2_YF_2"; // MMAction2项目根目录
// 服务器上的Python脚本路径!!!!!!!!!!!!!!!!!!!!!没有文件夹得创建（添加代码
//            String serverScriptPath = mmactionPath+"/demo/cutted_demo_spatiotemporal_det_slowfast.py";
//            String serverScriptPath = mmactionPath+"/demo/aaarandom.py";
            String serverScriptPath = mmactionPath+"/demo/cutted_demo_spatiotemporal_det_slowfast.py";
            String serverTempDir = mmactionPath+"/demo/tmp/"; //临时路径（原始的
            String serverOutputDir = mmactionPath+"/demo/processed_result/";   //输出路径（处理的
            // 构建服务器上的文件路径
            String serverVideoPath = serverTempDir + taskId + ".mp4"; //原始的
            String serverOutputPath = serverOutputDir + "processed_" + taskId + ".mp4";
            String serverJsonPath = serverOutputDir + "results_" + taskId + ".json";

            // 本地视频路径
            String localTempVideoPath = tempDirPath.resolve(taskId+".mp4").toString();
            //处理后的
            String localOutputPath = processingDirPath.resolve("processed_"+taskId + ".mp4").toString();
            String localJsonPath = processingDirPath.resolve("results_" + taskId + ".json").toString();

            // 将视频上传到服务器临时目录     从本地传到服务器
            SshUtil.uploadFile(localTempVideoPath, serverHost, serverPort, serverUser, serverPassword,
                    serverTempDir, taskId + ".mp4");
            video.setProgress(10);
            videoMapper.update(video);  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            // 构建命令 - 使用完整路径，确保目录存在
            String command =
                    "source /home/user3/miniconda3/etc/profile.d/conda.sh && " +
                            "conda activate " + condaEnv + " && " +
                            "python " + serverScriptPath +
                            " --video " + serverVideoPath +
                            " --out-filename " + serverOutputPath +
                            " --video_id " + videoId +
                            " --result-json " + serverJsonPath;
            System.out.println("执行命令: " + command);
            String executionResult = SshUtil.executeCommand(serverHost, serverPort, serverUser, serverPassword, command);
            System.out.println("服务器执行结果: " + executionResult);
            // 检查执行结果
            if (executionResult.contains("ERROR") || executionResult.contains("failed")) {
                throw new RuntimeException("服务器上执行Python脚本失败");
            }
            /// /////////////////////////////////////////////////////
            // 将视频从服务器下载到本地
            SshUtil.downloadFile(serverOutputPath, serverHost, serverPort, serverUser, serverPassword, localOutputPath);
            SshUtil.downloadFile(serverJsonPath, serverHost, serverPort, serverUser, serverPassword, localJsonPath);
            String resultVideoUrl = "http://"+ip+":8080/video/download/processed_"+taskId+".mp4";

            // 更新视频状态
            video.setProcessedVideoUrl(resultVideoUrl);
            video.setProcessedVideoPath(localOutputPath);
            video.setProcessedResultJsonPath(localJsonPath);
            video.setStatus("completed");
            video.setProgress(100);
            videoMapper.update(video);
            System.out.println("111111111111111111111111111111"+videoMapper.findById(video.getId()));
            // 保存分析结果到数据库
            jsonService.parseResultJson(video.getId(), localJsonPath);//////////////////////////////////////////////////////////////////////////////
        } catch (Exception e) {
            // 更新视频状态为失败
            video.setStatus("failed");
            video.setProgress(0);
            video.setProcessedVideoPath(null);
            videoMapper.update(video);
        }
    }
    @Test
    void process(){
        videoProcessingService.processVideo("http://localhost:8080/video/download/22a440c1-4d99-4273-8832-8a00da618c50.mp4",
            "22a440c1-4d99-4273-8832-8a00da618c50",
            Paths.get(TEMP_DIR),Paths.get(PROCESSING_DIR),
            "localhost");
    }
}