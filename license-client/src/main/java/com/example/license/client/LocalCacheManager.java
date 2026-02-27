package com.example.license.client;

import com.example.license.common.CryptoUtils;
import com.example.license.common.LicensePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地缓存管理器
 * 将授权信息加密缓存到本地文件，用于 License Server 不可达时的降级运行
 *
 * 缓存文件内容（AES-GCM 加密后存储）：
 * {
 *   "payload": { ... },         // 授权载荷
 *   "nodeId": "xxx",            // 节点ID
 *   "lastVerifyTime": 1234567,  // 上次成功校验的系统时间
 *   "licenseCode": "xxx"        // 授权码（用于重新注册）
 * }
 */
public class LocalCacheManager {

    private static final Logger log = LoggerFactory.getLogger(LocalCacheManager.class);

    private final String cachePath;
    private final String encryptionKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param cachePath     缓存文件路径
     * @param encryptionKey 加密密钥（通常从公钥派生）
     */
    public LocalCacheManager(String cachePath, String encryptionKey) {
        this.cachePath = cachePath;
        this.encryptionKey = encryptionKey;
    }

    /**
     * 保存缓存
     */
    public void saveCache(LicensePayload payload, String nodeId, String licenseCode) {
        try {
            Map<String, Object> cache = new HashMap<>();
            cache.put("payload", payload);
            cache.put("nodeId", nodeId);
            cache.put("lastVerifyTime", System.currentTimeMillis());
            cache.put("licenseCode", licenseCode);

            String json = objectMapper.writeValueAsString(cache);
            String encrypted = CryptoUtils.encrypt(json, encryptionKey);

            // 确保父目录存在
            File file = new File(cachePath);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }

            Files.write(Paths.get(cachePath), encrypted.getBytes(StandardCharsets.UTF_8));
            log.debug("License 缓存已保存: {}", cachePath);
        } catch (Exception e) {
            log.error("保存 License 缓存失败", e);
        }
    }

    /**
     * 读取缓存
     *
     * @return 缓存数据，读取失败返回 null
     */
    public CacheData loadCache() {
        try {
            File file = new File(cachePath);
            if (!file.exists()) {
                log.debug("License 缓存文件不存在: {}", cachePath);
                return null;
            }

            String encrypted = new String(Files.readAllBytes(Paths.get(cachePath)), StandardCharsets.UTF_8);
            String json = CryptoUtils.decrypt(encrypted, encryptionKey);
            Map<String, Object> cache = objectMapper.readValue(json, Map.class);

            CacheData data = new CacheData();
            data.payload = objectMapper.convertValue(cache.get("payload"), LicensePayload.class);
            data.nodeId = (String) cache.get("nodeId");
            data.lastVerifyTime = ((Number) cache.get("lastVerifyTime")).longValue();
            data.licenseCode = (String) cache.get("licenseCode");

            log.debug("License 缓存加载成功");
            return data;
        } catch (Exception e) {
            log.error("读取 License 缓存失败（文件可能已被篡改）", e);
            return null;
        }
    }

    /**
     * 删除缓存
     */
    public void deleteCache() {
        try {
            Files.deleteIfExists(Paths.get(cachePath));
            log.debug("License 缓存已删除");
        } catch (Exception e) {
            log.warn("删除 License 缓存失败", e);
        }
    }

    /**
     * 缓存数据结构
     */
    public static class CacheData {
        public LicensePayload payload;
        public String nodeId;
        public long lastVerifyTime;
        public String licenseCode;
    }
}
