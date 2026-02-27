package com.example.license.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License 客户端配置属性
 */
@ConfigurationProperties(prefix = "license")
public class LicenseProperties {

    /** 授权码 */
    private String code;

    /** License Server 地址 */
    private String serverUrl = "http://localhost:8100";

    /** RSA 公钥内容（PEM 格式，用于离线验证授权码） */
    private String publicKey;

    /** RSA 公钥文件路径（与 publicKey 二选一） */
    private String publicKeyPath;

    /** 心跳间隔（秒），默认 120 秒 */
    private int heartbeatIntervalSeconds = 120;

    /** 离线宽限期（小时），Server 不可达后仍可运行的时长，默认 72 小时 */
    private int gracePeriodHours = 72;

    /** 本地缓存文件路径 */
    private String cachePath = "./.license-cache";

    /** 不需要校验的路径（逗号分隔） */
    private String excludePaths = "/actuator/**,/error";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
    }

    public int getGracePeriodHours() {
        return gracePeriodHours;
    }

    public void setGracePeriodHours(int gracePeriodHours) {
        this.gracePeriodHours = gracePeriodHours;
    }

    public String getCachePath() {
        return cachePath;
    }

    public void setCachePath(String cachePath) {
        this.cachePath = cachePath;
    }

    public String getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(String excludePaths) {
        this.excludePaths = excludePaths;
    }
}
