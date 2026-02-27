package com.example.license.demo;

import com.example.license.client.LicenseContext;
import com.example.license.common.Result;
import com.example.license.common.ServerInfoUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 示例业务接口
 */
@RestController
public class BusinessController {

    /**
     * 示例业务接口 - 如果 License 无效会被 Filter 拦截返回 403
     */
    @GetMapping("/api/hello")
    public Result<String> hello() {
        return Result.success("Hello! License 校验通过，业务接口正常运行");
    }

    /**
     * 查看当前 License 状态
     */
    @GetMapping("/api/license/status")
    public Result<Map<String, Object>> licenseStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("valid", LicenseContext.isValid());
        status.put("degraded", LicenseContext.isDegraded());
        status.put("message", LicenseContext.getMessage());
        status.put("nodeId", LicenseContext.getNodeId());

        if (LicenseContext.getPayload() != null) {
            status.put("subject", LicenseContext.getPayload().getSubject());
            status.put("expiryTime", LicenseContext.getPayload().getExpiryTime());
            status.put("modules", LicenseContext.getPayload().getModules());
            status.put("maxMachineCount", LicenseContext.getPayload().getMaxMachineCount());
        }

        return Result.success(status);
    }

    /**
     * 获取当前机器信息（用于提交给管理员）
     */
    @GetMapping("/api/machine/info")
    public Result<?> machineInfo() {
        return Result.success(ServerInfoUtils.getCurrentMachineInfo());
    }
}
