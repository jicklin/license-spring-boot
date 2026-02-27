package com.example.license.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 服务器硬件信息采集工具
 * 支持 Linux / Mac / Windows
 */
public class ServerInfoUtils {

    private static final Logger log = LoggerFactory.getLogger(ServerInfoUtils.class);

    private ServerInfoUtils() {
    }

    /**
     * 获取当前机器的完整指纹信息
     */
    public static MachineInfo getCurrentMachineInfo() {
        MachineInfo info = new MachineInfo();
        info.setIpAddress(getIpAddressList());
        info.setMacAddress(getMacAddressList());
        info.setMachineId(getMachineId());
        info.setSystemUuid(getSystemUuid());
        info.setHostname(getHostname());
        return info;
    }

    /**
     * 获取主机名
     */
    public static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("获取主机名失败", e);
            return null;
        }
    }

    /**
     * 获取所有有效的 IP 地址列表（排除回环地址和虚拟接口）
     */
    public static List<String> getIpAddressList() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        // 只取 IPv4 地址
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取IP地址列表失败", e);
        }
        return result;
    }

    /**
     * 获取所有有效的 MAC 地址列表
     */
    public static List<String> getMacAddressList() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                        if (i < mac.length - 1) {
                            sb.append('-');
                        }
                    }
                    String macStr = sb.toString();
                    if (!result.contains(macStr)) {
                        result.add(macStr);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取MAC地址列表失败", e);
        }
        return result;
    }

    /**
     * 获取 Linux machine-id (/etc/machine-id)
     * Mac 下读取 IOPlatformUUID
     */
    public static String getMachineId() {
        // 1. 尝试读取 /etc/machine-id (Linux)
        try {
            String path = "/etc/machine-id";
            if (Files.exists(Paths.get(path))) {
                String id = new String(Files.readAllBytes(Paths.get(path))).trim();
                if (!id.isEmpty()) {
                    return id;
                }
            }
        } catch (Exception e) {
            log.debug("读取 /etc/machine-id 失败", e);
        }

        // 2. 尝试 Mac: ioreg 获取 IOPlatformUUID
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            try {
                String[] cmd = {"/bin/sh", "-c", "ioreg -rd1 -c IOPlatformExpertDevice | grep IOPlatformUUID"};
                String output = executeCommand(cmd);
                if (output != null) {
                    // 格式: "IOPlatformUUID" = "XXXXXXXX-XXXX-XXXX-XXXX-XXXXXXXXXXXX"
                    int start = output.lastIndexOf('"', output.length() - 2);
                    if (start > 0) {
                        return output.substring(start + 1, output.length() - 1).trim();
                    }
                }
            } catch (Exception e) {
                log.debug("获取 Mac IOPlatformUUID 失败", e);
            }
        }

        return null;
    }

    /**
     * 获取系统 UUID
     * Linux: dmidecode -s system-uuid
     * Mac: system_profiler SPHardwareDataType
     */
    public static String getSystemUuid() {
        String os = System.getProperty("os.name", "").toLowerCase();

        if (os.contains("linux")) {
            try {
                String[] cmd = {"/bin/sh", "-c", "dmidecode -s system-uuid 2>/dev/null || cat /sys/class/dmi/id/product_uuid 2>/dev/null"};
                String output = executeCommand(cmd);
                if (output != null && !output.isEmpty()) {
                    return output.trim();
                }
            } catch (Exception e) {
                log.debug("获取 Linux system-uuid 失败", e);
            }
        } else if (os.contains("mac")) {
            try {
                String[] cmd = {"/bin/sh", "-c", "system_profiler SPHardwareDataType | grep 'Hardware UUID'"};
                String output = executeCommand(cmd);
                if (output != null && output.contains(":")) {
                    return output.substring(output.indexOf(':') + 1).trim();
                }
            } catch (Exception e) {
                log.debug("获取 Mac system-uuid 失败", e);
            }
        }

        return null;
    }

    /**
     * 执行系统命令并返回输出
     */
    private static String executeCommand(String[] cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(line);
                }
                process.waitFor();
                return sb.toString().trim();
            }
        } catch (Exception e) {
            log.debug("执行命令失败: {}", String.join(" ", cmd), e);
            return null;
        }
    }
}
