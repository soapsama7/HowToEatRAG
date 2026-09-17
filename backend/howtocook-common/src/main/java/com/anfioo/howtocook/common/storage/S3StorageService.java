package com.anfioo.howtocook.common.storage;

import com.anfioo.howtocook.common.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.net.URI;

/**
 * RustFS（S3 兼容）存储实现：AWS SDK v2 同步客户端，path-style 访问。
 */
@Slf4j
@Service
public class S3StorageService implements StorageService {

    private final StorageProperties properties;
    private final S3Client s3Client;

    public S3StorageService(StorageProperties properties) {
        this.properties = properties;
        this.s3Client = S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /** 启动时确保 bucket 存在（不存在则创建） */
    @PostConstruct
    @Override
    public void ensureBucket() {
        String bucket = properties.getBucket();
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            log.info("存储 bucket 已存在: {}", bucket);
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("存储 bucket 不存在，已创建: {}", bucket);
        }
    }

    @Override
    public String putObject(InputStream inputStream, String objectKey) {
        byte[] bytes;
        try (inputStream) {
            bytes = inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("读取上传流失败: " + objectKey, e);
        }
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(objectKey)
                        .contentType("text/markdown; charset=utf-8")
                        .build(),
                RequestBody.fromBytes(bytes));
        return objectKey;
    }

    @Override
    public byte[] getObject(String objectKey) {
        ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
        return bytes.asByteArray();
    }

    @Override
    public void deleteObject(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
    }
}
