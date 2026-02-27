package com.example.license.server;

import com.example.license.common.LicenseCodec;
import com.example.license.common.LicensePayload;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 授权码签发与管理服务
 * 支持签发、查询、删除授权码，数据持久化到 JSON 文件
 */
@Service
public class LicenseCodeService {

    private static final Logger log = LoggerFactory.getLogger(LicenseCodeService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${license.private-key-path:}")
    private String privateKeyPath;

    @Value("${license.public-key-path:}")
    private String publicKeyPath;

    @Value("${license.license-persist-path:./data/licenses.json}")
    private String licensePersistPath;

    private PrivateKey privateKey;
    private String publicKeyContent;

    /** 已签发的授权码记录列表 */
    private final CopyOnWriteArrayList<LicenseRecord> records = new CopyOnWriteArrayList<>();

    @PostConstruct
    public void init() throws Exception {
        if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
            String keyContent = new String(Files.readAllBytes(Paths.get(privateKeyPath)));
            this.privateKey = LicenseCodec.loadPrivateKey(keyContent);
            log.info("RSA 私钥加载成功: {}", privateKeyPath);
        }
        if (publicKeyPath != null && !publicKeyPath.isEmpty()) {
            this.publicKeyContent = new String(Files.readAllBytes(Paths.get(publicKeyPath)));
            log.info("RSA 公钥加载成功: {}", publicKeyPath);
        }

        // 从文件加载已签发的授权码记录
        loadRecords();
    }

    /**
     * 签发授权码
     */
    public LicenseRecord generateLicenseCode(LicensePayload payload) throws Exception {
        if (privateKey == null) {
            throw new IllegalStateException("私钥未配置，无法签发授权码");
        }

        // 校验参数
        if (payload.getSubject() == null || payload.getSubject().isEmpty()) {
            throw new IllegalArgumentException("授权主体(subject)不能为空");
        }
        if (payload.getExpiryTime() == null) {
            throw new IllegalArgumentException("到期时间(expiryTime)不能为空");
        }
        if (payload.getMaxMachineCount() == null || payload.getMaxMachineCount() <= 0) {
            throw new IllegalArgumentException("最大机器数(maxMachineCount)必须大于0");
        }

        // 设置签发时间
        if (payload.getIssuedTime() == null) {
            payload.setIssuedTime(System.currentTimeMillis());
        }

        // 签发
        String code = LicenseCodec.encode(payload, privateKey);

        // 创建记录
        LicenseRecord record = new LicenseRecord();
        record.setId(UUID.randomUUID().toString().replace("-", ""));
        record.setSubject(payload.getSubject());
        record.setLicenseCode(code);
        record.setPayload(payload);
        record.setCreateTime(System.currentTimeMillis());

        records.add(record);
        persistRecords();

        log.info("授权码签发成功: id={}, subject={}, expiryTime={}, maxMachineCount={}",
                record.getId(), payload.getSubject(), payload.getExpiryTime(), payload.getMaxMachineCount());
        return record;
    }

    /**
     * 获取所有授权码记录
     */
    public List<LicenseRecord> listRecords() {
        return new ArrayList<>(records);
    }

    /**
     * 删除授权码记录
     */
    public boolean deleteRecord(String id) {
        boolean removed = records.removeIf(r -> r.getId().equals(id));
        if (removed) {
            persistRecords();
            log.info("授权码记录已删除: id={}", id);
        }
        return removed;
    }

    /**
     * 获取公钥内容（用于客户端配置）
     */
    public String getPublicKeyContent() {
        return publicKeyContent;
    }

    // ===================== 持久化相关 =====================

    private void loadRecords() {
        try {
            File file = new File(licensePersistPath);
            if (!file.exists()) {
                log.info("授权码持久化文件不存在，跳过加载: {}", licensePersistPath);
                return;
            }

            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<LicenseRecord> loaded = objectMapper.readValue(json, new TypeReference<List<LicenseRecord>>() {});
            records.addAll(loaded);
            log.info("授权码记录加载完成: {} 条", loaded.size());
        } catch (Exception e) {
            log.error("加载授权码记录失败: {}", e.getMessage(), e);
        }
    }

    private void persistRecords() {
        try {
            Path filePath = Paths.get(licensePersistPath);
            Path parentDir = filePath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new ArrayList<>(records));
            Files.write(tempFile, json.getBytes(StandardCharsets.UTF_8));
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.debug("授权码记录已持久化: {} 条", records.size());
        } catch (Exception e) {
            log.error("持久化授权码记录失败: {}", e.getMessage(), e);
        }
    }

    // ===================== 记录数据结构 =====================

    /**
     * 授权码记录
     */
    public static class LicenseRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String subject;
        private String licenseCode;
        private LicensePayload payload;
        private Long createTime;

        public LicenseRecord() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getLicenseCode() { return licenseCode; }
        public void setLicenseCode(String licenseCode) { this.licenseCode = licenseCode; }
        public LicensePayload getPayload() { return payload; }
        public void setPayload(LicensePayload payload) { this.payload = payload; }
        public Long getCreateTime() { return createTime; }
        public void setCreateTime(Long createTime) { this.createTime = createTime; }
    }
}
