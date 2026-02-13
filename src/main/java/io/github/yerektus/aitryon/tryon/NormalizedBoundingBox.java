package io.github.yerektus.aitryon.tryon;

public record NormalizedBoundingBox(
        double x,
        double y,
        double width,
        double height
) {

    public boolean isValid() {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(width) || Double.isNaN(height)) {
            return false;
        }
        if (width <= 0 || height <= 0) {
            return false;
        }
        return x >= 0 && y >= 0 && x + width <= 1 && y + height <= 1;
    }
}
