package com.zhiwu.sftp.thread;

import com.zhiwu.sftp.bo.PooledSftpClient;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/8/17 2:48
 */
public class SftpClientPool {
    private final BlockingQueue<PooledSftpClient> pool;
    private final int maxSize;
    private volatile String host;
    private volatile int port;
    private volatile String username;
    private volatile String password;

    private final SshClient sshClient;

    private final ExecutorService executor;

    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
    private final long maxIdleMillis = TimeUnit.MINUTES.toMillis(30);

    public SftpClientPool(int poolSize, String host, int port, String username, String password) {
        this.maxSize = poolSize;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.pool = new LinkedBlockingQueue<>(poolSize);

        this.sshClient = SshClient.setUpDefaultClient();
        this.sshClient.start();

        this.executor = Executors.newFixedThreadPool(poolSize);
        // 预热连接
        for (int i = 0; i < poolSize; i++) {
            pool.offer(new PooledSftpClient(createClient()));
        }
        cleaner.execute(this::startCleaner);
    }

    public SftpClient borrowClient() {
        try {
            PooledSftpClient pooled = pool.poll(5, TimeUnit.SECONDS);
            if (pooled == null || !isClientValid(pooled.getClient())) {
                closeClient(pooled != null ? pooled.getClient() : null);
                pooled = new PooledSftpClient(createClient());
            }
            pooled.updateLastUsed();
            return pooled.getClient();
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public void returnClient(SftpClient client) {
        if (client != null && isClientValid(client)) {
            pool.offer(new PooledSftpClient(client));
        } else {
            closeClient(client);
        }
    }

    /**
     * 检查连接是否可用
     */
    public boolean isAvailable() {
        try {
            SftpClient client = borrowClient();
            boolean ok = isClientValid(client);
            returnClient(client);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 当密码或 IP 变更时调用
     */
    public synchronized void reInit(String newHost, int newPort, String newUser, String newPass) {
        this.host = newHost;
        this.port = newPort;
        this.username = newUser;
        this.password = newPass;

        // 关闭旧连接
        pool.forEach(client -> closeClient(client.getClient()));
        pool.clear();

        // 重建
        for (int i = 0; i < maxSize; i++) {
            pool.offer(new PooledSftpClient(createClient()));
        }
    }

    /**
     * 提交自定义 SFTP 操作
     */
    public <T> CompletableFuture<T> submitTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
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
     * 校验 SFTP 客户端是否可用
     */
    private boolean isClientValid(SftpClient client) {
        try {
            if (client == null || !client.isOpen()) return false;
            client.stat("."); // 比 read(".") 更轻量，性能更高
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 创建新连接
     */
    private SftpClient createClient() {
        try {
            ClientSession session = sshClient.connect(username, host, port)
                    .verify(Duration.ofSeconds(5))
                    .getSession();
            session.addPasswordIdentity(password);
            session.auth().verify(Duration.ofSeconds(5));
            return SftpClientFactory.instance().createSftpClient(session);
        } catch (IOException e) {
            throw new RuntimeException("SFTP连接失败: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭客户端
     */
    private void closeClient(SftpClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (IOException ignore) {
            }
        }
    }

    /**
     * 销毁整个连接池
     */
    public void shutdown() {
        pool.forEach(client -> closeClient(client.getClient()));
        sshClient.stop();
    }

    private void startCleaner() {
        cleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            pool.removeIf(pooled -> {
                if (now - pooled.lastUsedTime > maxIdleMillis) {
                    closeClient(pooled.getClient());
                    return true;
                }
                return false;
            });
        }, maxIdleMillis, maxIdleMillis, TimeUnit.MILLISECONDS);
    }
}
