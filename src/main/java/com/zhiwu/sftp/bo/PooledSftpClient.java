package com.zhiwu.sftp.bo;

import org.apache.sshd.sftp.client.SftpClient;

import java.io.IOException;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/8/17 2:58
 */
public class PooledSftpClient {
    private SftpClient client;
    public long lastUsedTime; // 最后一次使用时间

    public PooledSftpClient(SftpClient client) {
        this.client = client;
        this.lastUsedTime = System.currentTimeMillis();
    }

    public void updateLastUsed() {
        this.lastUsedTime = System.currentTimeMillis();
    }

    public void close() throws IOException {
        client.close();
    }

    public SftpClient getClient() {
        return client;
    }
}
