package com.example.license.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 防时间篡改检查器
 * 
 * 使用两种机制防止用户通过修改系统时间来绕过 License 时间限制：
 * 1. System.nanoTime() - JVM 单调递增计时器，不受系统时钟影响，用于计算真实离线时长
 * 2. System.currentTimeMillis() - 系统时钟，用于检测时钟回拨
 * 
 * 注意：nanoTime 在 JVM 重启后会重置，所以只能用于同一个 JVM 生命周期内的计时
 * JVM 重启后需要重新向 Server 注册，此时 Server 会重新校验有效期
 */
public class AntiTamperChecker {

    private static final Logger log = LoggerFactory.getLogger(AntiTamperChecker.class);

    /** 上次成功校验时的系统时间（毫秒） */
    private volatile long lastVerifySystemTime;

    /** 上次成功校验时的单调时间（纳秒） */
    private volatile long lastVerifyNanoTime;

    /** 离线起始的单调时间（纳秒），-1 表示未离线 */
    private volatile long offlineStartNanoTime = -1;

    /** 宽限期（纳秒） */
    private final long gracePeriodNanos;

    public AntiTamperChecker(int gracePeriodHours) {
        this.gracePeriodNanos = gracePeriodHours * 3600L * 1_000_000_000L;
        this.lastVerifySystemTime = System.currentTimeMillis();
        this.lastVerifyNanoTime = System.nanoTime();
    }

    /**
     * 记录一次成功的在线校验
     * 每次与 Server 通信成功后调用
     */
    public void recordOnlineVerify() {
        this.lastVerifySystemTime = System.currentTimeMillis();
        this.lastVerifyNanoTime = System.nanoTime();
        this.offlineStartNanoTime = -1; // 重置离线计时
    }

    /**
     * 标记进入离线状态
     */
    public void markOffline() {
        if (offlineStartNanoTime < 0) {
            offlineStartNanoTime = System.nanoTime();
            log.warn("进入离线模式，宽限期 {} 小时", gracePeriodNanos / 3600_000_000_000L);
        }
    }

    /**
     * 检查降级是否仍有效
     *
     * @return true=降级有效，false=降级过期需重新连接 Server
     */
    public boolean isDegradationValid() {
        // 1. 检查系统时钟是否被回拨
        long currentSystemTime = System.currentTimeMillis();
        if (currentSystemTime < lastVerifySystemTime) {
            long rollbackMs = lastVerifySystemTime - currentSystemTime;
            log.error("检测到系统时间回拨 {} 毫秒！拒绝降级运行", rollbackMs);
            return false;
        }

        // 2. 用 nanoTime 计算真实离线时长（无法被修改系统时间影响）
        if (offlineStartNanoTime < 0) {
            // 还未标记离线，说明刚开始离线
            return true;
        }

        long offlineDurationNanos = System.nanoTime() - offlineStartNanoTime;
        if (offlineDurationNanos > gracePeriodNanos) {
            long hoursOffline = offlineDurationNanos / 3600_000_000_000L;
            log.error("离线时长已超过宽限期: 已离线 {} 小时", hoursOffline);
            return false;
        }

        // 3. 额外校验：如果系统时间跳跃幅度远大于 nanoTime 计算的时长，可能是时间被往前调了
        long nanoElapsedMs = (System.nanoTime() - lastVerifyNanoTime) / 1_000_000;
        long systemElapsedMs = currentSystemTime - lastVerifySystemTime;
        // 允许 5 分钟的误差（NTP 校时等正常场景）
        if (systemElapsedMs - nanoElapsedMs > 300_000) {
            log.warn("系统时间与单调时间存在较大偏差（差值: {} 毫秒），可能存在时间篡改",
                    systemElapsedMs - nanoElapsedMs);
            // 这里只是警告，不直接拒绝，因为可能是 NTP 校时导致的
        }

        return true;
    }

    /**
     * 获取离线剩余时间（小时）
     */
    public long getRemainingGraceHours() {
        if (offlineStartNanoTime < 0) {
            return gracePeriodNanos / 3600_000_000_000L;
        }
        long elapsed = System.nanoTime() - offlineStartNanoTime;
        long remaining = gracePeriodNanos - elapsed;
        return Math.max(0, remaining / 3600_000_000_000L);
    }

    public long getLastVerifySystemTime() {
        return lastVerifySystemTime;
    }

    public long getLastVerifyNanoTime() {
        return lastVerifyNanoTime;
    }

    public void setLastVerifySystemTime(long time) {
        this.lastVerifySystemTime = time;
    }
}
