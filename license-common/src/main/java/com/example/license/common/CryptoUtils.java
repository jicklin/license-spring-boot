package com.example.license.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM 加解密工具（用于本地缓存文件加密）
 * GCM 模式同时提供加密和完整性校验，无需额外 HMAC
 */
public class CryptoUtils {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12;   // bytes

    private CryptoUtils() {
    }

    /**
     * AES-GCM 加密
     *
     * @param plaintext 明文
     * @param key       密钥（会被 SHA-256 处理为 32 字节）
     * @return Base64 编码的密文（包含 IV）
     */
    public static String encrypt(String plaintext, String key) throws Exception {
        byte[] keyBytes = deriveKey(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        // 生成随机 IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // 拼接 IV + 密文
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(result);
    }

    /**
     * AES-GCM 解密
     *
     * @param ciphertext Base64 编码的密文（包含 IV）
     * @param key        密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String key) throws Exception {
        byte[] keyBytes = deriveKey(key);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

        byte[] decoded = Base64.getDecoder().decode(ciphertext);

        // 分离 IV 和密文
        byte[] iv = Arrays.copyOfRange(decoded, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(decoded, GCM_IV_LENGTH, decoded.length);

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    /**
     * 从公钥或任意字符串派生 AES 密钥（SHA-256 → 32 字节）
     */
    public static byte[] deriveKey(String source) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(source.getBytes(StandardCharsets.UTF_8));
    }
}
