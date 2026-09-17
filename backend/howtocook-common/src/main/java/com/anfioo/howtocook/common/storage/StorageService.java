package com.anfioo.howtocook.common.storage;

import java.io.InputStream;

/**
 * 存储服务抽象：S3 兼容（RustFS）上传 / 下载 / 删除。
 * <p>objectKey 一律由服务端生成（uuid.md），不使用用户输入，防路径穿越（Step 2.1 / Review 要点）。</p>
 */
public interface StorageService {

    /** 上传对象（读取整个流） */
    String putObject(InputStream inputStream, String objectKey);

    /** 下载对象字节 */
    byte[] getObject(String objectKey);

    /** 删除对象 */
    void deleteObject(String objectKey);

    /** 启动时确保 bucket 存在（不存在则创建） */
    void ensureBucket();

    /** 服务端生成对象键：uuid.md（文件名不参与路径，防路径穿越） */
    static String generateObjectKey() {
        return java.util.UUID.randomUUID() + ".md";
    }
}
