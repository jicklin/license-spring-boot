package com.example.license.common;

import java.io.Serializable;
import java.util.List;

/**
 * 授权码载荷 - 包含授权信息（不含机器指纹）
 */
public class LicensePayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权主体 */
    private String subject;

    /** 签发时间（毫秒时间戳） */
    private Long issuedTime;

    /** 到期时间（毫秒时间戳） */
    private Long expiryTime;

    /** 最大授权机器数 */
    private Integer maxMachineCount;

    /** 授权模块列表 */
    private List<String> modules;

    /** 描述 */
    private String description;

    public LicensePayload() {
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getIssuedTime() {
        return issuedTime;
    }

    public void setIssuedTime(Long issuedTime) {
        this.issuedTime = issuedTime;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public Integer getMaxMachineCount() {
        return maxMachineCount;
    }

    public void setMaxMachineCount(Integer maxMachineCount) {
        this.maxMachineCount = maxMachineCount;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
