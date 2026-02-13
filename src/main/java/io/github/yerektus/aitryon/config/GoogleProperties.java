package io.github.yerektus.aitryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.google")
public class GoogleProperties {
    private List<String> allowedClientIds = new ArrayList<>();

    public List<String> getAllowedClientIds() {
        return allowedClientIds;
    }

    public void setAllowedClientIds(List<String> allowedClientIds) {
        this.allowedClientIds = allowedClientIds;
    }
}
