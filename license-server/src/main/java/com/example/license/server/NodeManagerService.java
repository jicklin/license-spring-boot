package com.example.license.server;

import com.example.license.common.LicenseCodec;
import com.example.license.common.LicensePayload;
import com.example.license.common.MachineInfo;
import com.example.license.common.NodeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 节点管理服务
 * 管理节点注册、心跳、释放
 * 节点数据会持久化到 JSON 文件，服务重启后自动恢复
 */
@Service
public class NodeManagerService {

    private static final Logger log = LoggerFactory.getLogger(NodeManagerService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** 请求统计计数器 */
    private final AtomicLong registerCount = new AtomicLong(0);
    private final AtomicLong heartbeatCount = new AtomicLong(0);
    private final AtomicLong unregisterCount = new AtomicLong(0);

    /**
     * 在线节点表: nodeId → NodeInfo
     */
    private final ConcurrentHashMap<String, NodeInfo> onlineNodes = new ConcurrentHashMap<>();

    /**
     * 授权码对应的在线节点数: licenseCode → count
     */
    private final ConcurrentHashMap<String, List<String>> licenseNodeMap = new ConcurrentHashMap<>();

    @Value("${license.public-key-path:}")
    private String publicKeyPath;

    @Value("${license.node-timeout-seconds:300}")
    private int nodeTimeoutSeconds;

    @Value("${license.node-persist-path:./data/nodes.json}")
    private String nodePersistPath;

    private PublicKey publicKey;

    @PostConstruct
    public void init() throws Exception {
        if (publicKeyPath != null && !publicKeyPath.isEmpty()) {
            String keyContent = new String(Files.readAllBytes(Paths.get(publicKeyPath)));
            this.publicKey = LicenseCodec.loadPublicKey(keyContent);
            log.info("RSA 公钥加载成功（用于验证授权码）");
        }

        // 从持久化文件恢复节点数据
        loadPersistedNodes();
    }

    /**
     * 节点注册
     *
     * @param licenseCode 授权码
     * @param machineInfo 机器指纹
     * @return 分配的节点ID
     */
    public synchronized String register(String licenseCode, MachineInfo machineInfo) throws Exception {
        // 1. 验证授权码
        LicensePayload payload = LicenseCodec.decode(licenseCode, publicKey);

        // 2. 检查有效期
        long now = System.currentTimeMillis();
        if (payload.getExpiryTime() != null && now > payload.getExpiryTime()) {
            throw new SecurityException("授权码已过期");
        }
        if (payload.getIssuedTime() != null && now < payload.getIssuedTime()) {
            throw new SecurityException("授权码尚未生效");
        }

        // 3. 检查是否已注册过（同一机器指纹）
        String existingNodeId = findExistingNode(licenseCode, machineInfo);
        if (existingNodeId != null) {
            // 刷新心跳时间并返回已有的 nodeId
            NodeInfo existing = onlineNodes.get(existingNodeId);
            existing.setLastHeartbeatTime(now);
            registerCount.incrementAndGet();
            persistNodes();
            log.info("节点重新注册（已存在）: nodeId={}, hostname={}", existingNodeId, machineInfo.getHostname());
            return existingNodeId;
        }

        // 4. 检查节点数量是否超限
        List<String> nodeIds = licenseNodeMap.computeIfAbsent(licenseCode, k -> new ArrayList<>());
        if (payload.getMaxMachineCount() != null && nodeIds.size() >= payload.getMaxMachineCount()) {
            throw new SecurityException("已达到最大授权机器数: " + payload.getMaxMachineCount()
                    + "，当前在线: " + nodeIds.size());
        }

        // 5. 注册新节点
        String nodeId = UUID.randomUUID().toString().replace("-", "");
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setNodeId(nodeId);
        nodeInfo.setLicenseCode(licenseCode);
        nodeInfo.setMachineInfo(machineInfo);
        nodeInfo.setRegisterTime(now);
        nodeInfo.setLastHeartbeatTime(now);

        onlineNodes.put(nodeId, nodeInfo);
        nodeIds.add(nodeId);

        // 统计 & 持久化
        registerCount.incrementAndGet();
        persistNodes();

        log.info("节点注册成功: nodeId={}, hostname={}, 当前在线数={}/{}",
                nodeId, machineInfo.getHostname(), nodeIds.size(), payload.getMaxMachineCount());
        return nodeId;
    }

    /**
     * 心跳
     */
    public boolean heartbeat(String nodeId) {
        NodeInfo nodeInfo = onlineNodes.get(nodeId);
        if (nodeInfo == null) {
            log.warn("心跳失败：节点不存在 nodeId={}", nodeId);
            return false;
        }
        nodeInfo.setLastHeartbeatTime(System.currentTimeMillis());
        heartbeatCount.incrementAndGet();
        // 心跳不做持久化，避免频繁写磁盘；节点恢复时会根据超时时间过滤
        return true;
    }

    /**
     * 主动注销
     */
    public synchronized void unregister(String nodeId) {
        NodeInfo nodeInfo = onlineNodes.remove(nodeId);
        if (nodeInfo != null) {
            List<String> nodeIds = licenseNodeMap.get(nodeInfo.getLicenseCode());
            if (nodeIds != null) {
                nodeIds.remove(nodeId);
            }
            // 统计 & 持久化
            unregisterCount.incrementAndGet();
            persistNodes();
            log.info("节点注销成功: nodeId={}, hostname={}",
                    nodeId, nodeInfo.getMachineInfo() != null ? nodeInfo.getMachineInfo().getHostname() : "unknown");
        }
    }

    /**
     * 清理超时节点
     */
    public synchronized void cleanupTimeoutNodes() {
        long now = System.currentTimeMillis();
        long timeoutMs = nodeTimeoutSeconds * 1000L;

        List<String> timeoutNodeIds = new ArrayList<>();
        for (Map.Entry<String, NodeInfo> entry : onlineNodes.entrySet()) {
            if (now - entry.getValue().getLastHeartbeatTime() > timeoutMs) {
                timeoutNodeIds.add(entry.getKey());
            }
        }

        for (String nodeId : timeoutNodeIds) {
            unregister(nodeId);
            log.info("超时节点已清理: nodeId={}", nodeId);
        }

        if (!timeoutNodeIds.isEmpty()) {
            log.info("本次清理超时节点 {} 个", timeoutNodeIds.size());
        }
    }

    /**
     * 获取所有在线节点
     */
    public List<NodeInfo> getOnlineNodes() {
        return new ArrayList<>(onlineNodes.values());
    }

    /**
     * 根据授权码获取在线节点数
     */
    public int getOnlineCount(String licenseCode) {
        List<String> nodeIds = licenseNodeMap.get(licenseCode);
        return nodeIds != null ? nodeIds.size() : 0;
    }

    /**
     * 获取请求统计数据
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("onlineNodeCount", onlineNodes.size());
        stats.put("registerCount", registerCount.get());
        stats.put("heartbeatCount", heartbeatCount.get());
        stats.put("unregisterCount", unregisterCount.get());
        stats.put("licenseCount", licenseNodeMap.size());
        return stats;
    }

    // ===================== 持久化相关 =====================

    /**
     * 从文件加载已持久化的节点数据
     * 加载后会过滤掉已超时的节点
     */
    private void loadPersistedNodes() {
        try {
            File file = new File(nodePersistPath);
            if (!file.exists()) {
                log.info("节点持久化文件不存在，跳过加载: {}", nodePersistPath);
                return;
            }

            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<NodeInfo> nodes = objectMapper.readValue(json, new TypeReference<List<NodeInfo>>() {});

            long now = System.currentTimeMillis();
            long timeoutMs = nodeTimeoutSeconds * 1000L;
            int loadedCount = 0;
            int skippedCount = 0;

            for (NodeInfo node : nodes) {
                // 过滤掉超时节点（重启期间可能已经超时）
                if (node.getLastHeartbeatTime() != null && (now - node.getLastHeartbeatTime()) > timeoutMs) {
                    skippedCount++;
                    log.debug("跳过超时节点: nodeId={}, hostname={}", node.getNodeId(),
                            node.getMachineInfo() != null ? node.getMachineInfo().getHostname() : "unknown");
                    continue;
                }

                onlineNodes.put(node.getNodeId(), node);
                licenseNodeMap.computeIfAbsent(node.getLicenseCode(), k -> new ArrayList<>())
                        .add(node.getNodeId());
                loadedCount++;
            }

            log.info("节点数据恢复完成: 恢复 {} 个节点，跳过 {} 个超时节点", loadedCount, skippedCount);

            // 如果有超时节点被跳过，重新持久化以清理文件
            if (skippedCount > 0) {
                persistNodes();
            }
        } catch (Exception e) {
            log.error("加载持久化节点数据失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 将当前在线节点持久化到文件
     * 使用先写临时文件再原子重命名的方式，防止写入中断导致文件损坏
     */
    private void persistNodes() {
        try {
            Path filePath = Paths.get(nodePersistPath);

            // 确保父目录存在
            Path parentDir = filePath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            // 先写临时文件
            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            List<NodeInfo> nodeList = new ArrayList<>(onlineNodes.values());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nodeList);
            Files.write(tempFile, json.getBytes(StandardCharsets.UTF_8));

            // 原子重命名
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.debug("节点数据已持久化: {} 个节点", nodeList.size());
        } catch (Exception e) {
            log.error("持久化节点数据失败: {}", e.getMessage(), e);
        }
    }

    // ===================== 私有工具方法 =====================

    /**
     * 查找同一授权码下已注册过的相同机器
     */
    private String findExistingNode(String licenseCode, MachineInfo machineInfo) {
        List<String> nodeIds = licenseNodeMap.get(licenseCode);
        if (nodeIds == null) {
            return null;
        }
        for (String nodeId : nodeIds) {
            NodeInfo node = onlineNodes.get(nodeId);
            if (node != null && isSameMachine(node.getMachineInfo(), machineInfo)) {
                return nodeId;
            }
        }
        return null;
    }

    /**
     * 判断是否为同一台机器（MAC 地址有交集即认为是同一台）
     */
    private boolean isSameMachine(MachineInfo a, MachineInfo b) {
        if (a == null || b == null) {
            return false;
        }
        // 优先用 machineId 比较
        if (a.getMachineId() != null && b.getMachineId() != null) {
            return a.getMachineId().equals(b.getMachineId());
        }
        // 其次用 MAC 地址比较
        if (a.getMacAddress() != null && b.getMacAddress() != null) {
            for (String mac : a.getMacAddress()) {
                if (b.getMacAddress().contains(mac)) {
                    return true;
                }
            }
        }
        return false;
    }
}
