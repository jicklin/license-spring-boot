package com.example.license.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 授权码编解码工具
 * 格式: Base64(JSON载荷) + "." + Base64(RSA签名)
 */
public class LicenseCodec {

    private static final Logger log = LoggerFactory.getLogger(LicenseCodec.class);
    private static final String SIGN_ALGORITHM = "SHA256withRSA";
    private static final String SEPARATOR = ".";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private LicenseCodec() {
    }

    /**
     * 编码授权码：签名 + 拼接
     *
     * @param payload    授权载荷
     * @param privateKey RSA 私钥
     * @return 授权码字符串
     */
    public static String encode(LicensePayload payload, PrivateKey privateKey) throws Exception {
        // 序列化为 JSON
        String json = objectMapper.writeValueAsString(payload);
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

        // RSA 签名
        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(jsonBytes);
        byte[] signBytes = signature.sign();

        // 拼接: Base64(JSON) + "." + Base64(签名)
        String payloadPart = Base64.getUrlEncoder().withoutPadding().encodeToString(jsonBytes);
        String signPart = Base64.getUrlEncoder().withoutPadding().encodeToString(signBytes);

        return payloadPart + SEPARATOR + signPart;
    }

    /**
     * 解码授权码：验签 + 解析
     *
     * @param licenseCode 授权码字符串
     * @param publicKey   RSA 公钥
     * @return 授权载荷（验签失败返回 null）
     */
    public static LicensePayload decode(String licenseCode, PublicKey publicKey) throws Exception {
        // 分割
        int dotIndex = licenseCode.indexOf(SEPARATOR);
        if (dotIndex < 0) {
            throw new IllegalArgumentException("授权码格式无效");
        }
        String payloadPart = licenseCode.substring(0, dotIndex);
        String signPart = licenseCode.substring(dotIndex + 1);

        // Base64 解码
        byte[] jsonBytes = Base64.getUrlDecoder().decode(payloadPart);
        byte[] signBytes = Base64.getUrlDecoder().decode(signPart);

        // RSA 验签
        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(jsonBytes);
        if (!signature.verify(signBytes)) {
            throw new SecurityException("授权码签名验证失败，可能已被篡改");
        }

        // 反序列化
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        return objectMapper.readValue(json, LicensePayload.class);
    }

    /**
     * 从 PEM 格式的 Base64 字符串加载私钥
     */
    public static PrivateKey loadPrivateKey(String base64Key) throws Exception {
        String cleaned = base64Key
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    /**
     * 从 PEM 格式的 Base64 字符串加载公钥
     */
    public static PublicKey loadPublicKey(String base64Key) throws Exception {
        String cleaned = base64Key
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(cleaned);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
