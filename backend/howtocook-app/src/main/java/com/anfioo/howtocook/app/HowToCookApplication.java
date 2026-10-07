package com.anfioo.howtocook.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 主应用启动类。
 * <p>scanBasePackages 覆盖 howtocook-common 中的公共配置（Jackson / MyBatis-Plus / Redis 等）。</p>
 * <p>@EnableScheduling：回收站定时清理（RecycleBinCleanupJob，Review 修订 R2）。</p>
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.anfioo.howtocook")
public class HowToCookApplication {

    public static void main(String[] args) {
        SpringApplication.run(HowToCookApplication.class, args);
    }

}
