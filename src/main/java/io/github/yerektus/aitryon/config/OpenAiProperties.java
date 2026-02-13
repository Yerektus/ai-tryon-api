package io.github.yerektus.aitryon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {
    private String baseUrl;
    private String apiKey;
    private String model;
    private String imageEditModel;
    private String styleHintModel;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getImageEditModel() {
        return imageEditModel;
    }

    public void setImageEditModel(String imageEditModel) {
        this.imageEditModel = imageEditModel;
    }

    public String getStyleHintModel() {
        return styleHintModel;
    }

    public void setStyleHintModel(String styleHintModel) {
        this.styleHintModel = styleHintModel;
    }
}
