package com.anfioo.howtocook.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RustFS（S3 兼容）存储配置，对应 application.yml 的 howtocook.storage 段。
 */
@Data
@ConfigurationProperties(prefix = "howtocook.storage")
public class StorageProperties {

    /** S3 endpoint，如 http://localhost:9000 */
    private String endpoint;

    /** Access Key */
    private String accessKey;

    /** Secret Key */
    private String secretKey;

    /** 桶名 */
    private String bucket;
}
