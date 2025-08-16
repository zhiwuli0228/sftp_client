package com.zhiwu.sftp.service;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/8/17 2:43
 */
public class AsyncSftpService {

    private final SshClient sshClient;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private final BlockingQueue<SftpClient> pool;
    private final ExecutorService executor;

    public AsyncSftpService(String host, int port, String username, String password, int poolSize, int threadSize) throws IOException {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;

        this.sshClient = SshClient.setUpDefaultClient();
        sshClient.start();

        this.pool = new LinkedBlockingQueue<>(poolSize);
        this.executor = Executors.newFixedThreadPool(threadSize);

        // 预先建立 poolSize 个客户端
        for (int i = 0; i < poolSize; i++) {
            pool.offer(createSftpClient());
        }
    }

    /**
     * 提交异步下载任务
     */
    public CompletableFuture<Boolean> downloadFile(String remotePath, String localPath) {
        return submitTask(() -> {
            try (SftpClient client = borrowClient()) {
                client.read(remotePath).transferTo(java.nio.file.Files.newOutputStream(Paths.get(localPath)));
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * 提交异步删除任务
     */
    public CompletableFuture<Boolean> deleteFile(String remotePath) {
        return submitTask(() -> {
            try (SftpClient client = borrowClient()) {
                client.remove(remotePath);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * 提交自定义 SFTP 操作
     */
    public <T> CompletableFuture<T> submitTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    /**
     * 获取 SftpClient
     */
    private SftpClient borrowClient() throws IOException {
        SftpClient client = pool.poll();
        if (client == null || !client.isOpen()) {
            return createSftpClient();
        }
        return client;
    }

    /**
     * 创建新的 SftpClient
     */
    private SftpClient createSftpClient() throws IOException {
        try {
            ClientSession session = sshClient.connect(username, host, port).verify(5000).getSession();
            session.addPasswordIdentity(password);
            session.auth().verify(5000);
            return SftpClientFactory.instance().createSftpClient(session);
        } catch (Exception e) {
            throw new IOException("Failed to create SftpClient", e);
        }
    }

    /** 校验 SFTP 客户端是否可用 */
    public boolean isClientValid(SftpClient client) {
        try {
            if (client == null || !client.isOpen()) return false;
            client.stat("."); // 比 read(".") 更轻量，性能更高
            return true;
        } catch (IOException e) {
            return false;
        }
    }


    /**
     * 关闭资源
     */
    public void shutdown() {
        executor.shutdown();
        pool.forEach(c -> {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        });
        sshClient.stop();
    }

}
