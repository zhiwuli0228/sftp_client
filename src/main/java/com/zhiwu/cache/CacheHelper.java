package com.zhiwu.cache;

import java.util.function.Consumer;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/12/22 23:44
 */
public class CacheHelper extends Cache {

    private Consumer<?> consumer;

    public CacheHelper(Consumer<?> consumer) {
        this.consumer = consumer;
    }

    public void startReload() {
        consumer.accept(null);
    }
}
