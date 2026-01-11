package com.zhiwu.cache;

import java.util.function.Consumer;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/12/22 23:59
 */
public class Cache {

    private Consumer<?> consumer;

    public void reload() {
        consumer.accept(null);
    }
}
