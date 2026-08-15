package com.example.copilot.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "copilot.auth.dingtalk")
public record DingTalkProperties(String clientId, String clientSecret, String corpId, String redirectUri) {}
