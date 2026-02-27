package com.example.license.server;

import com.example.license.common.LicensePayload;
import com.example.license.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 授权码管理 REST API
 */
@RestController
@RequestMapping("/api/license")
public class LicenseCodeController {

    private static final Logger log = LoggerFactory.getLogger(LicenseCodeController.class);

    private final LicenseCodeService licenseCodeService;
    private final NodeManagerService nodeManagerService;

    public LicenseCodeController(LicenseCodeService licenseCodeService, NodeManagerService nodeManagerService) {
        this.licenseCodeService = licenseCodeService;
        this.nodeManagerService = nodeManagerService;
    }

    /**
     * 签发授权码
     * POST /api/license/generate
     */
    @PostMapping("/generate")
    public Result<?> generate(@RequestBody LicensePayload payload) {
        try {
            LicenseCodeService.LicenseRecord record = licenseCodeService.generateLicenseCode(payload);
            return Result.success("授权码签发成功", record);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("授权码签发失败", e);
            return Result.fail("签发失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有授权码记录
     * GET /api/license/list
     */
    @GetMapping("/list")
    public Result<?> list() {
        return Result.success(licenseCodeService.listRecords());
    }

    /**
     * 删除授权码记录
     * DELETE /api/license/{id}
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable String id) {
        boolean deleted = licenseCodeService.deleteRecord(id);
        if (deleted) {
            return Result.success("删除成功", id);
        } else {
            return Result.fail(404, "记录不存在");
        }
    }

    /**
     * 获取公钥（客户端配置用）
     * GET /api/license/publicKey
     */
    @GetMapping("/publicKey")
    public Result<String> getPublicKey() {
        String content = licenseCodeService.getPublicKeyContent();
        if (content == null) {
            return Result.fail("公钥未配置");
        }
        return Result.success(content);
    }

    /**
     * 查看在线节点（管理用）
     * GET /api/license/nodes
     */
    @GetMapping("/nodes")
    public Result<?> getNodes() {
        return Result.success(nodeManagerService.getOnlineNodes());
    }
}
