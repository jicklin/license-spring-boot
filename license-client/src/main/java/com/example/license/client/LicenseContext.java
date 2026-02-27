package com.example.license.client;

import com.example.license.common.LicensePayload;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * License 授权状态上下文（全局静态）
 */
public class LicenseContext {

    private static final AtomicBoolean valid = new AtomicBoolean(false);
    private static final AtomicBoolean degraded = new AtomicBoolean(false);
    private static final AtomicReference<LicensePayload> payload = new AtomicReference<>();
    private static final AtomicReference<String> nodeId = new AtomicReference<>();
    private static final AtomicReference<String> message = new AtomicReference<>("未授权");

    private LicenseContext() {
    }

    /** 授权是否有效（包括降级模式） */
    public static boolean isValid() {
        return valid.get();
    }

    /** 是否处于降级模式 */
    public static boolean isDegraded() {
        return degraded.get();
    }

    /** 获取授权载荷 */
    public static LicensePayload getPayload() {
        return payload.get();
    }

    /** 获取节点 ID */
    public static String getNodeId() {
        return nodeId.get();
    }

    /** 获取当前状态描述 */
    public static String getMessage() {
        return message.get();
    }

    // ===== 内部设置方法 =====

    static void setValid(boolean v) {
        valid.set(v);
    }

    static void setDegraded(boolean d) {
        degraded.set(d);
    }

    static void setPayload(LicensePayload p) {
        payload.set(p);
    }

    static void setNodeId(String id) {
        nodeId.set(id);
    }

    static void setMessage(String msg) {
        message.set(msg);
    }

    /**
     * 标记为已授权（正常模式）
     */
    static void markValid(LicensePayload p, String nId) {
        payload.set(p);
        nodeId.set(nId);
        valid.set(true);
        degraded.set(false);
        message.set("授权有效");
    }

    /**
     * 标记为降级模式
     */
    static void markDegraded(LicensePayload p, String msg) {
        payload.set(p);
        valid.set(true);
        degraded.set(true);
        message.set(msg);
    }

    /**
     * 标记为无效
     */
    static void markInvalid(String msg) {
        valid.set(false);
        degraded.set(false);
        message.set(msg);
    }
}
