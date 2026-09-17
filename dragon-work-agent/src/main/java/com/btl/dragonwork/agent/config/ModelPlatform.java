package com.btl.dragonwork.agent.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModelPlatform {
    DASHSCOPE("dashscope");

    private final String code;

    public static ModelPlatform of(String platform) {
        if (platform == null || platform.isBlank()) {
            throw new IllegalArgumentException("platform is blank");
        }
        for (ModelPlatform value : values()) {
            if (value.code.equalsIgnoreCase(platform.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported platform: " + platform);
    }
}
