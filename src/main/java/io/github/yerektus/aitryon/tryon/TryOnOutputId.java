package io.github.yerektus.aitryon.tryon;

public enum TryOnOutputId {
    INPAINT("inpaint");

    private final String apiValue;

    TryOnOutputId(String apiValue) {
        this.apiValue = apiValue;
    }

    public String apiValue() {
        return apiValue;
    }
}
