package com.xuelvquan.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 开启定时任务：采集调度CrawlScheduler依赖此注解
public class TravelApplication {

    public static void main(String[] args) {
        enableWindowsTrustStore();
        SpringApplication.run(TravelApplication.class, args);
    }

    /**
     * Windows下让JVM使用系统根证书库校验外部HTTPS（采集器需要访问公开站点）。
     *
     * 背景：JDK自带的cacerts在部分Windows环境缺少中间证书，访问12306等站点会报
     * PKIX path building failed；改用系统根证书库可正常完成TLS握手。已显式配置过
     * trustStore的环境不做改动。
     */
    private static void enableWindowsTrustStore() {
        String os = System.getProperty("os.name", "").toLowerCase();
        boolean configured = System.getProperty("javax.net.ssl.trustStore") != null
                || System.getProperty("javax.net.ssl.trustStoreType") != null;
        if (os.contains("win") && !configured) {
            System.setProperty("javax.net.ssl.trustStoreType", "WINDOWS-ROOT");
        }
    }
}
