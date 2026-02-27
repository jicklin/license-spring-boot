package com.example.license.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * License 客户端自动配置
 * 在 spring.factories 中注册，业务系统只需引入 license-client 依赖即可自动生效
 * 注意：此配置不可通过属性禁用，确保引入依赖后即强制启用 License 校验
 */
@Configuration
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LicenseAutoConfiguration.class);

    /**
     * 注册 License 客户端服务
     */
    @Bean(initMethod = "start")
    public LicenseClientService licenseClientService(LicenseProperties properties) {
        return new LicenseClientService(properties);
    }

    /**
     * 注册 License 校验 Filter
     */
    @Bean
    public FilterRegistrationBean<LicenseCheckFilter> licenseCheckFilter(LicenseProperties properties) {
        FilterRegistrationBean<LicenseCheckFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LicenseCheckFilter(properties.getExcludePaths()));
        registration.addUrlPatterns("/*");
        registration.setName("licenseCheckFilter");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 应用启动后校验 License 状态
     * 如果授权码未配置或无效，给出严重警告并在宽限期后阻止请求
     */
    @Bean
    public ApplicationRunner licenseStartupChecker() {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) {
                if (!LicenseContext.isValid()) {
                    log.error("╔══════════════════════════════════════════════╗");
                    log.error("║  ⚠️  License 授权验证失败！                   ║");
                    log.error("║  当前状态: {}",
                            String.format("%-35s║", LicenseContext.getMessage()));
                    log.error("║  所有 API 请求将被拦截返回 403              ║");
                    log.error("║  请检查 license.code 配置                   ║");
                    log.error("╚══════════════════════════════════════════════╝");
                } else {
                    log.info("License 授权验证通过 ✓");
                }
            }
        };
    }
}
