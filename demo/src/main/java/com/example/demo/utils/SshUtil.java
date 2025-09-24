package com.example.demo.utils;

import com.jcraft.jsch.*;
import java.io.*;
import java.util.Properties;

public class SshUtil {
    // 执行远程命令
    public static String executeCommand(String host, int port, String user, String password, String command)
            throws IOException, JSchException, InterruptedException {
        StringBuilder output = new StringBuilder();
        Session session = null;
        try {
            // 创建SSH会话
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(30000); // 30秒超时
            // 执行命令
            Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.setInputStream(null);
            ((ChannelExec) channel).setErrStream(System.err);
            InputStream in = channel.getInputStream();
            channel.connect();
            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            channel.disconnect();
            session.disconnect();
        } catch (JSchException | IOException e) {
            e.printStackTrace();
            throw new RuntimeException("SSH执行命令失败", e);
        }
        return output.toString();
    }

    // 上传文件到远程服务器
    public static void uploadFile(String localPath, String host, int port, String user, String password,
                                  String remoteDir, String remoteFileName)
            throws JSchException, IOException, SftpException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(user, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp sftp = (ChannelSftp) channel;
        sftp.cd(remoteDir);
        sftp.put(localPath, remoteFileName);
        sftp.exit();
        session.disconnect();
    }

    // 从远程服务器下载文件
    public static void downloadFile(String remotePath, String host, int port, String user, String password,
                                    String localPath) throws IOException, JSchException, SftpException {
        int maxRetries = 3;
        int retryDelay = 5000; // 5秒重试间隔
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftp = null;
        System.out.println("开始下载文件: " + remotePath + " -> " + localPath);
        System.out.println("目标服务器: " + user + "@" + host + ":" + port);
        try {
            // 确保本地目录存在
            File localFile = new File(localPath);
            File parentDir = localFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                System.out.println("创建本地目录: " + parentDir.getAbsolutePath());
                if (!parentDir.mkdirs()) {
                    throw new IOException("无法创建本地目录: " + parentDir.getAbsolutePath());
                }
            }
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    System.out.println("尝试连接 (尝试 #" + attempt + ")");
                    // 创建会话
                    session = jsch.getSession(user, host, port);
                    session.setPassword(password);
                    // 配置会话
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    config.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
                    session.setConfig(config);
                    // 设置连接超时
                    session.setTimeout(15000);
                    System.out.println("正在建立SSH连接...");
                    session.connect();
                    System.out.println("SSH连接成功");
                    // 打开SFTP通道
                    System.out.println("打开SFTP通道...");
                    Channel channel = session.openChannel("sftp");
                    channel.connect();
                    sftp = (ChannelSftp) channel;
                    // 检查远程文件是否存在
                    System.out.println("检查远程文件是否存在: " + remotePath);
                    try {
                        SftpATTRS attrs = sftp.lstat(remotePath);
                        if (attrs != null) {
                            System.out.println("远程文件存在，大小: " + attrs.getSize() + " 字节");
                        }
                    } catch (SftpException e) {
                        if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                            throw new FileNotFoundException("远程文件不存在: " + remotePath);
                        } else {
                            throw e;
                        }
                    }
                    // 下载文件
                    System.out.println("开始下载: " + remotePath + " -> " + localPath);
                    sftp.get(remotePath, localPath);
                    System.out.println("文件下载成功");
                    // 验证本地文件
                    File downloadedFile = new File(localPath);
                    if (!downloadedFile.exists() || downloadedFile.length() == 0) {
                        throw new IOException("文件下载不完整: " + localPath);
                    }
                    // 成功下载后退出重试循环
                    return;
                } catch (JSchException | SftpException e) {
                    // 处理特定错误
                    if (e.getMessage().contains("Connection refused") || e.getMessage().contains("connect timed out")) {
                        System.err.println("连接失败: " + e.getMessage());

                        if (attempt < maxRetries) {
                            System.err.println("将在 " + (retryDelay/1000) + " 秒后重试...");
                            Thread.sleep(retryDelay);
                            // 每次重试增加等待时间
                            retryDelay += 2000;
                        } else {
                            throw new JSchException("连接失败，尝试 " + maxRetries + " 次后仍无法连接", e);
                        }
                    } else {
                        // 非连接错误直接抛出
                        throw e;
                    }
                } finally {
                    // 清理资源
                    if (sftp != null) {
                        sftp.exit();
                    }
                    if (session != null && session.isConnected()) {
                        session.disconnect();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JSchException("下载过程被中断", e);
        }
    }
}
