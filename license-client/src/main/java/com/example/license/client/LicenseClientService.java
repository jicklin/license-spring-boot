package com.example.license.client;

import com.example.license.common.LicenseCodec;
import com.example.license.common.LicensePayload;
import com.example.license.common.MachineInfo;
import com.example.license.common.ServerInfoUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * License 客户端核心服务
 * 
 * 职责：
 * 1. 启动时向 License Server 注册节点
 * 2. 定时发送心跳保持在线
 * 3. Server 不可达时切换到降级模式（本地缓存 + 宽限期）
 * 4. 应用关闭时主动注销
 */
public class LicenseClientService {

    private static final Logger log = LoggerFactory.getLogger(LicenseClientService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final LicenseProperties properties;
    private final LocalCacheManager cacheManager;
    private final AntiTamperChecker tamperChecker;

    private PublicKey publicKey;
    private MachineInfo machineInfo;
    private ScheduledExecutorService scheduler;

    /** 连续心跳失败次数 */
    private volatile int heartbeatFailCount = 0;
    private static final int MAX_HEARTBEAT_FAIL_BEFORE_DEGRADE = 3;

    public LicenseClientService(LicenseProperties properties) {
        this.properties = properties;
        this.tamperChecker = new AntiTamperChecker(properties.getGracePeriodHours());

        // 用公钥内容派生 AES 密钥
        String encKey = properties.getPublicKey() != null ? properties.getPublicKey() : "default-license-key";
        this.cacheManager = new LocalCacheManager(properties.getCachePath(), encKey);
    }

    /**
     * 初始化并启动 License 校验
     */
    public void start() {
        try {
            // 1. 加载公钥
            loadPublicKey();

            // 2. 采集机器信息
            machineInfo = ServerInfoUtils.getCurrentMachineInfo();
            log.info("当前机器信息: hostname={}, mac={}", machineInfo.getHostname(), machineInfo.getMacAddress());

            // 3. 先尝试离线验证授权码格式（确保授权码是合法的）
            String licenseCode = properties.getCode();
            if (licenseCode == null || licenseCode.trim().isEmpty()) {
                LicenseContext.markInvalid("未配置授权码");
                log.error("License 授权码未配置，请设置 license.code");
                return;
            }

            // 验证授权码签名
            LicensePayload payload = null;
            if (publicKey != null) {
                try {
                    payload = LicenseCodec.decode(licenseCode.trim(), publicKey);
                    log.info("授权码验证通过: subject={}, 到期时间={}", payload.getSubject(),
                            payload.getExpiryTime() != null ? new java.util.Date(payload.getExpiryTime()) : "永久");
                } catch (Exception e) {
                    LicenseContext.markInvalid("授权码签名验证失败: " + e.getMessage());
                    log.error("授权码签名验证失败", e);
                    return;
                }
            }

            // 4. 尝试向 Server 注册
            boolean registered = tryRegister(licenseCode);

            if (!registered) {
                // 注册失败，尝试从缓存降级
                if (!tryDegradeFromCache()) {
                    if (payload != null) {
                        // 有合法的授权码但 Server 不可达且无缓存，首次降级
                        LicenseContext.markDegraded(payload, "License Server 不可达，使用授权码离线模式");
                        cacheManager.saveCache(payload, null, licenseCode);
                        tamperChecker.markOffline();
                        log.warn("License Server 不可达，已进入离线降级模式");
                    } else {
                        LicenseContext.markInvalid("无法连接 License Server，且无本地缓存");
                        log.error("无法连接 License Server，且无本地缓存，授权无效");
                    }
                }
            }

            // 5. 启动心跳定时任务
            startHeartbeatTask();

        } catch (Exception e) {
            LicenseContext.markInvalid("License 初始化失败: " + e.getMessage());
            log.error("License 初始化失败", e);
        }
    }

    /**
     * 尝试向 Server 注册
     */
    private boolean tryRegister(String licenseCode) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("licenseCode", licenseCode.trim());
            body.put("machineInfo", machineInfo);

            String response = httpPost(properties.getServerUrl() + "/api/node/register", body);
            Map<String, Object> result = objectMapper.readValue(response, Map.class);

            int code = (Integer) result.get("code");
            if (code == 200) {
                String nodeId = (String) result.get("data");
                LicensePayload payload = LicenseCodec.decode(licenseCode.trim(), publicKey);

                LicenseContext.markValid(payload, nodeId);
                tamperChecker.recordOnlineVerify();
                cacheManager.saveCache(payload, nodeId, licenseCode);
                heartbeatFailCount = 0;

                log.info("License 注册成功: nodeId={}", nodeId);
                return true;
            } else {
                String msg = (String) result.get("message");
                LicenseContext.markInvalid(msg);
                log.error("License 注册失败: {}", msg);
                return false;
            }
        } catch (Exception e) {
            log.warn("无法连接 License Server: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从本地缓存进入降级模式
     */
    private boolean tryDegradeFromCache() {
        LocalCacheManager.CacheData cache = cacheManager.loadCache();
        if (cache == null || cache.payload == null) {
            return false;
        }

        // 检查缓存中的有效期
        if (cache.payload.getExpiryTime() != null && System.currentTimeMillis() > cache.payload.getExpiryTime()) {
            log.error("本地缓存的授权已过期");
            LicenseContext.markInvalid("授权已过期");
            return false;
        }

        // 设置上次校验时间，用于防篡改检测
        tamperChecker.setLastVerifySystemTime(cache.lastVerifyTime);
        tamperChecker.markOffline();

        // 检查防篡改
        if (!tamperChecker.isDegradationValid()) {
            LicenseContext.markInvalid("检测到时间异常或降级已过期");
            return false;
        }

        long remainingHours = tamperChecker.getRemainingGraceHours();
        LicenseContext.markDegraded(cache.payload,
                "降级运行中，剩余宽限期 " + remainingHours + " 小时");
        LicenseContext.setNodeId(cache.nodeId);

        log.warn("已从本地缓存进入降级模式，剩余宽限期 {} 小时", remainingHours);
        return true;
    }

    /**
     * 启动心跳定时任务
     */
    private void startHeartbeatTask() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "license-heartbeat");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                doHeartbeat();
            } catch (Exception e) {
                log.error("心跳任务异常", e);
            }
        }, properties.getHeartbeatIntervalSeconds(), properties.getHeartbeatIntervalSeconds(), TimeUnit.SECONDS);

        log.info("License 心跳任务已启动，间隔 {} 秒", properties.getHeartbeatIntervalSeconds());
    }

    /**
     * 执行心跳
     */
    private void doHeartbeat() {
        String nodeId = LicenseContext.getNodeId();

        // 如果有 nodeId，发送心跳
        if (nodeId != null && !nodeId.isEmpty()) {
            try {
                Map<String, String> body = new HashMap<>();
                body.put("nodeId", nodeId);
                String response = httpPost(properties.getServerUrl() + "/api/node/heartbeat", body);
                Map<String, Object> result = objectMapper.readValue(response, Map.class);

                int code = (Integer) result.get("code");
                if (code == 200) {
                    // 心跳成功
                    tamperChecker.recordOnlineVerify();
                    heartbeatFailCount = 0;

                    if (LicenseContext.isDegraded()) {
                        // 从降级恢复到正常
                        LicenseContext.setDegraded(false);
                        LicenseContext.setMessage("授权有效（已恢复连接）");
                        cacheManager.saveCache(LicenseContext.getPayload(), nodeId, properties.getCode());
                        log.info("License Server 已恢复连接，退出降级模式");
                    }
                    return;
                } else if (code == 404) {
                    // 节点不存在，尝试重新注册
                    log.warn("节点不存在，尝试重新注册...");
                    if (tryRegister(properties.getCode())) {
                        return;
                    }
                }
            } catch (Exception e) {
                log.warn("心跳发送失败: {}", e.getMessage());
            }
        } else {
            // 没有 nodeId，尝试注册
            if (tryRegister(properties.getCode())) {
                return;
            }
        }

        // 心跳或注册失败
        heartbeatFailCount++;
        if (heartbeatFailCount >= MAX_HEARTBEAT_FAIL_BEFORE_DEGRADE) {
            tamperChecker.markOffline();

            if (tamperChecker.isDegradationValid()) {
                long remaining = tamperChecker.getRemainingGraceHours();
                LicenseContext.setDegraded(true);
                LicenseContext.setMessage("降级运行中，剩余宽限期 " + remaining + " 小时");
                log.warn("连续 {} 次通信失败，降级运行中，剩余宽限期 {} 小时", heartbeatFailCount, remaining);
            } else {
                LicenseContext.markInvalid("降级已过期，请恢复与 License Server 的连接");
                log.error("降级宽限期已到，授权无效");
            }
        }
    }

    /**
     * 应用关闭时主动注销
     */
    @PreDestroy
    public void shutdown() {
        // 停止心跳
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        // 主动注销
        String nodeId = LicenseContext.getNodeId();
        if (nodeId != null && !nodeId.isEmpty()) {
            try {
                Map<String, String> body = new HashMap<>();
                body.put("nodeId", nodeId);
                httpPost(properties.getServerUrl() + "/api/node/unregister", body);
                log.info("节点已主动注销: nodeId={}", nodeId);
            } catch (Exception e) {
                log.warn("节点注销失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 加载 RSA 公钥
     */
    private void loadPublicKey() throws Exception {
        String keyContent = properties.getPublicKey();

        // 优先从配置中读取，其次从文件读取
        if ((keyContent == null || keyContent.isEmpty()) && properties.getPublicKeyPath() != null) {
            keyContent = new String(Files.readAllBytes(Paths.get(properties.getPublicKeyPath())));
        }

        if (keyContent != null && !keyContent.isEmpty()) {
            this.publicKey = LicenseCodec.loadPublicKey(keyContent);
            log.info("RSA 公钥加载成功");
        } else {
            log.warn("未配置 RSA 公钥，将跳过本地授权码签名验证");
        }
    }

    /**
     * 发送 HTTP POST 请求
     */
    private String httpPost(String urlStr, Object body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            byte[] jsonBytes = objectMapper.writeValueAsBytes(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBytes);
            }

            int responseCode = conn.getResponseCode();
            java.io.InputStream is = (responseCode >= 200 && responseCode < 300)
                    ? conn.getInputStream() : conn.getErrorStream();

            if (is == null) {
                throw new RuntimeException("HTTP " + responseCode + " (no response body)");
            }

            byte[] responseBytes;
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                responseBytes = baos.toByteArray();
            }

            return new String(responseBytes, StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }
}
