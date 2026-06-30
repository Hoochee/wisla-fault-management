package ru.wisla.fm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "adapter")
public record AdapterProperties(String baseUrl, String publicUrl, String internalToken) {

    public String effectivePublicUrl() {
        String url = publicUrl != null && !publicUrl.isBlank() ? publicUrl : baseUrl;
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
