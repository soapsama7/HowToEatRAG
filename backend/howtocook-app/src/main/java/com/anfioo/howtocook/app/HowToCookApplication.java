package com.anfioo.howtocook.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 主应用启动类。
 * <p>scanBasePackages 覆盖 howtocook-common 中的公共配置（Jackson / MyBatis-Plus / Redis 等）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.anfioo.howtocook")
public class HowToCookApplication {

    public static void main(String[] args) {
        SpringApplication.run(HowToCookApplication.class, args);
    }

}
