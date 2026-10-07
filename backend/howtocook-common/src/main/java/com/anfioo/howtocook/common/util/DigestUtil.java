package com.anfioo.howtocook.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 摘要工具：SHA-256（上传内容查重用，Review 修订 R3）。
 */
public final class DigestUtil {

    private DigestUtil() {
    }

    /** 计算 SHA-256 并返回 hex 小写字符串（64 字符） */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 为 JDK 必带算法，不会发生
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
