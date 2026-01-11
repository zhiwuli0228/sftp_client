package com.zhiwu.cache;

import jakarta.annotation.PostConstruct;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * 功能：
 *
 * @author zhiwu
 * @Data 2025/12/22 23:44
 */
@Service
public class CacheServiceImpl {

    private static final Log LOGGER = LogFactory.getLog(CacheServiceImpl.class);

    private CacheHelper cacheHelper;

    @Autowired
    private CacheMapper mapper;


    @PostConstruct
    public void start() {
        cacheHelper = new CacheHelper(param -> this.init());
    }

    private void init() {
        new CountDownLatch(20);
        mapper.queryAll();
        new Thread(() -> {
            LOGGER.info("test1");

            cacheHelper.reload();
            LOGGER.info("finish do sth");
        }).start();
    }
}
