package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.common.ExternalServiceException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InpaintMaskBuilderTests {

    private final InpaintMaskBuilder builder = new InpaintMaskBuilder();

    @Test
    void buildsMaskUsingProvidedBoundingBox() throws IOException {
        final byte[] sourceImage = createImage(100, 200);

        final byte[] maskBytes = builder.buildMaskPng(sourceImage, new NormalizedBoundingBox(0.1, 0.2, 0.3, 0.4));
        final BufferedImage mask = ImageIO.read(new ByteArrayInputStream(maskBytes));

        assertThat(alpha(mask.getRGB(20, 80))).isZero();
        assertThat(alpha(mask.getRGB(5, 5))).isEqualTo(255);
    }

    @Test
    void usesFallbackBoundingBoxForInvalidInput() throws IOException {
        final byte[] sourceImage = createImage(100, 200);

        final byte[] maskBytes = builder.buildMaskPng(sourceImage, new NormalizedBoundingBox(1.2, 0.2, -1, 0.1));
        final BufferedImage mask = ImageIO.read(new ByteArrayInputStream(maskBytes));

        assertThat(alpha(mask.getRGB(50, 100))).isZero();
        assertThat(alpha(mask.getRGB(5, 5))).isEqualTo(255);
    }

    @Test
    void failsForUnsupportedImagePayload() {
        assertThatThrownBy(() -> builder.buildMaskPng("not-an-image".getBytes(StandardCharsets.UTF_8), null))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("decode image");
    }

    @Test
    void detectsForegroundBoundingBoxFromSubject() throws IOException {
        final byte[] sourceImage = createImageWithSubject(120, 200, 36, 20, 48, 152);

        final NormalizedBoundingBox bbox = builder.detectForegroundBoundingBox(sourceImage);

        assertThat(bbox.x()).isBetween(0.26, 0.36);
        assertThat(bbox.y()).isBetween(0.06, 0.14);
        assertThat(bbox.width()).isBetween(0.36, 0.52);
        assertThat(bbox.height()).isBetween(0.72, 0.84);
    }

    private byte[] createImage(int width, int height) throws IOException {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] createImageWithSubject(int width,
                                          int height,
                                          int subjectX,
                                          int subjectY,
                                          int subjectWidth,
                                          int subjectHeight) throws IOException {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(new Color(25, 25, 25));
            graphics.fillRect(subjectX, subjectY, subjectWidth, subjectHeight);
        } finally {
            graphics.dispose();
        }

        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }
}
