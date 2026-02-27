package com.example.license.common;

import java.io.Serializable;
import java.util.List;

/**
 * 机器指纹信息
 */
public class MachineInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** IP 地址列表 */
    private List<String> ipAddress;

    /** MAC 地址列表 */
    private List<String> macAddress;

    /** Linux machine-id (/etc/machine-id) */
    private String machineId;

    /** 系统 UUID (dmidecode -s system-uuid) */
    private String systemUuid;

    /** 主机名 */
    private String hostname;

    public MachineInfo() {
    }

    public List<String> getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(List<String> ipAddress) {
        this.ipAddress = ipAddress;
    }

    public List<String> getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(List<String> macAddress) {
        this.macAddress = macAddress;
    }

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public String getSystemUuid() {
        return systemUuid;
    }

    public void setSystemUuid(String systemUuid) {
        this.systemUuid = systemUuid;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    @Override
    public String toString() {
        return "MachineInfo{" +
                "ipAddress=" + ipAddress +
                ", macAddress=" + macAddress +
                ", machineId='" + machineId + '\'' +
                ", systemUuid='" + systemUuid + '\'' +
                ", hostname='" + hostname + '\'' +
                '}';
    }
}
