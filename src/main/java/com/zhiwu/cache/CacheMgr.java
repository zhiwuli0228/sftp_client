package com.zhiwu.cache;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/12/22 23:44
 */
@Service
public class CacheMgr {

    ScheduledThreadPoolExecutor executors = new ScheduledThreadPoolExecutor(1);

    @PostConstruct
    public void init() {
        Cache cache = new Cache();
        executors.scheduleAtFixedRate(cache::reload ,10,10, TimeUnit.MINUTES);
    }

}
