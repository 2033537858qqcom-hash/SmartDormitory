package com.dormitory.common;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝官方 EasySDK 插件配置类
 * 默认对接支付宝沙箱开放平台 (openapi-sandbox.dl.alipaydev.com)，支持个人开发者免企业资质测试
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "alipay")
public class AlipayConfig {
    private static final Logger log = LoggerFactory.getLogger(AlipayConfig.class);

    private boolean enabled = false;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String gatewayHost = "openapi-sandbox.dl.alipaydev.com";
    private String protocol = "https";
    private String signType = "RSA2";
    private String notifyUrl;
    private String returnUrl;

    @PostConstruct
    public void init() {
        if (!enabled || appId == null || appId.isBlank() || privateKey == null || privateKey.isBlank()) {
            log.info("支付宝支付插件当前处于【演示模拟通道 (DEMO)】；若需体验沙箱真实扫码付，请在 application.yml 中配置 alipay 参数。");
            return;
        }
        try {
            Config config = new Config();
            config.protocol = protocol;
            config.gatewayHost = gatewayHost;
            config.signType = signType;
            config.appId = appId.trim();
            config.merchantPrivateKey = privateKey.trim();
            config.alipayPublicKey = alipayPublicKey != null ? alipayPublicKey.trim() : "";
            config.notifyUrl = notifyUrl;
            Factory.setOptions(config);
            log.info("支付宝 EasySDK 沙箱支付插件加载成功！网关：{}，AppId：{}", gatewayHost, appId);
        } catch (Exception e) {
            log.warn("支付宝 EasySDK 初始化异常，系统将自动降级为演示模拟通道: {}", e.getMessage());
            this.enabled = false;
        }
    }
}
