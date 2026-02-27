package com.example.license.common;

import java.io.Serializable;

/**
 * 节点注册信息（客户端 → 服务端）
 */
public class NodeInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 节点ID（由 Server 分配） */
    private String nodeId;

    /** 授权码 */
    private String licenseCode;

    /** 机器指纹 */
    private MachineInfo machineInfo;

    /** 注册时间（毫秒时间戳） */
    private Long registerTime;

    /** 最后心跳时间（毫秒时间戳） */
    private Long lastHeartbeatTime;

    public NodeInfo() {
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getLicenseCode() {
        return licenseCode;
    }

    public void setLicenseCode(String licenseCode) {
        this.licenseCode = licenseCode;
    }

    public MachineInfo getMachineInfo() {
        return machineInfo;
    }

    public void setMachineInfo(MachineInfo machineInfo) {
        this.machineInfo = machineInfo;
    }

    public Long getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(Long registerTime) {
        this.registerTime = registerTime;
    }

    public Long getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public void setLastHeartbeatTime(Long lastHeartbeatTime) {
        this.lastHeartbeatTime = lastHeartbeatTime;
    }
}
