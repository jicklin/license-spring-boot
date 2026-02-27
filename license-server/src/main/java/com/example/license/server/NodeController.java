package com.example.license.server;

import com.example.license.common.MachineInfo;
import com.example.license.common.NodeInfo;
import com.example.license.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 节点管理 REST API
 */
@RestController
@RequestMapping("/api/node")
public class NodeController {

    private static final Logger log = LoggerFactory.getLogger(NodeController.class);

    private final NodeManagerService nodeManagerService;

    public NodeController(NodeManagerService nodeManagerService) {
        this.nodeManagerService = nodeManagerService;
    }

    /**
     * 节点注册
     * POST /api/node/register
     * Body: { "licenseCode": "xxx", "machineInfo": {...} }
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, Object> request) {
        try {
            String licenseCode = (String) request.get("licenseCode");
            if (licenseCode == null || licenseCode.isEmpty()) {
                return Result.fail(400, "授权码不能为空");
            }

            // 解析 machineInfo
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            MachineInfo machineInfo = mapper.convertValue(request.get("machineInfo"), MachineInfo.class);

            String nodeId = nodeManagerService.register(licenseCode, machineInfo);
            return Result.success("注册成功", nodeId);
        } catch (SecurityException e) {
            log.warn("节点注册被拒绝: {}", e.getMessage());
            return Result.fail(403, e.getMessage());
        } catch (Exception e) {
            log.error("节点注册失败", e);
            return Result.fail("注册失败: " + e.getMessage());
        }
    }

    /**
     * 心跳
     * POST /api/node/heartbeat
     * Body: { "nodeId": "xxx" }
     */
    @PostMapping("/heartbeat")
    public Result<Boolean> heartbeat(@RequestBody Map<String, String> request) {
        String nodeId = request.get("nodeId");
        if (nodeId == null || nodeId.isEmpty()) {
            return Result.fail(400, "nodeId 不能为空");
        }
        boolean success = nodeManagerService.heartbeat(nodeId);
        if (success) {
            return Result.success(true);
        } else {
            return Result.fail(404, "节点不存在，请重新注册");
        }
    }

    /**
     * 主动注销
     * POST /api/node/unregister
     * Body: { "nodeId": "xxx" }
     */
    @PostMapping("/unregister")
    public Result<String> unregister(@RequestBody Map<String, String> request) {
        String nodeId = request.get("nodeId");
        if (nodeId == null || nodeId.isEmpty()) {
            return Result.fail(400, "nodeId 不能为空");
        }
        nodeManagerService.unregister(nodeId);
        return Result.success("注销成功", nodeId);
    }

    /**
     * 查看所有在线节点
     * GET /api/node/online
     */
    @GetMapping("/online")
    public Result<?> getOnlineNodes() {
        return Result.success(nodeManagerService.getOnlineNodes());
    }

    /**
     * 获取请求统计数据
     * GET /api/node/stats
     */
    @GetMapping("/stats")
    public Result<?> getStats() {
        return Result.success(nodeManagerService.getStats());
    }
}
