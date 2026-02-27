package com.example.license.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理超时节点
 */
@Component
public class NodeCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(NodeCleanupTask.class);

    private final NodeManagerService nodeManagerService;

    public NodeCleanupTask(NodeManagerService nodeManagerService) {
        this.nodeManagerService = nodeManagerService;
    }

    /**
     * 每 60 秒检查一次超时节点
     */
    @Scheduled(fixedRate = 60000)
    public void cleanup() {
        try {
            nodeManagerService.cleanupTimeoutNodes();
        } catch (Exception e) {
            log.error("清理超时节点异常", e);
        }
    }
}
