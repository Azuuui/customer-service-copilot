package com.example.copilot.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DingTalkProperties.class)
public class IdentityConfiguration {
}
