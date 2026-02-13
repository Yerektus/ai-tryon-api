package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.common.ExternalServiceException;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

class InpaintMaskBuilder {

    static final NormalizedBoundingBox FALLBACK_TORSO_BBOX = new NormalizedBoundingBox(0.25, 0.24, 0.5, 0.52);
    static final NormalizedBoundingBox FALLBACK_PERSON_BBOX = new NormalizedBoundingBox(0.18, 0.06, 0.64, 0.9);

    byte[] buildMaskPng(byte[] baseImageBytes, NormalizedBoundingBox region) {
        try {
            final BufferedImage source = decodeImage(baseImageBytes);

            final int width = source.getWidth();
            final int height = source.getHeight();

            final NormalizedBoundingBox safeRegion = region != null && region.isValid()
                    ? region
                    : FALLBACK_TORSO_BBOX;

            final int left = clampToRange((int) Math.round(safeRegion.x() * width), 0, width - 1);
            final int top = clampToRange((int) Math.round(safeRegion.y() * height), 0, height - 1);
            final int boxWidth = clampToRange((int) Math.round(safeRegion.width() * width), 1, width - left);
            final int boxHeight = clampToRange((int) Math.round(safeRegion.height() * height), 1, height - top);

            final BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = mask.createGraphics();
            try {
                // OpenAI image edits keep opaque pixels and edit transparent pixels.
                graphics.setColor(new Color(0, 0, 0, 255));
                graphics.fillRect(0, 0, width, height);
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(new Color(0, 0, 0, 0));
                graphics.fillRect(left, top, boxWidth, boxHeight);
            } finally {
                graphics.dispose();
            }

            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(mask, "png", buffer);
            return buffer.toByteArray();
        } catch (IOException ex) {
            throw new ExternalServiceException("Unable to build inpaint mask");
        }
    }

    NormalizedBoundingBox detectForegroundBoundingBox(byte[] baseImageBytes) {
        try {
            final BufferedImage source = decodeImage(baseImageBytes);
            final int width = source.getWidth();
            final int height = source.getHeight();

            final int[] background = estimateBackgroundColor(source);
            final int threshold = 52;

            int minX = width;
            int minY = height;
            int maxX = -1;
            int maxY = -1;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int argb = source.getRGB(x, y);
                    final int alpha = (argb >>> 24) & 0xFF;
                    if (alpha < 10) {
                        continue;
                    }

                    final int r = (argb >>> 16) & 0xFF;
                    final int g = (argb >>> 8) & 0xFF;
                    final int b = argb & 0xFF;
                    final int distance = Math.abs(r - background[0]) + Math.abs(g - background[1]) + Math.abs(b - background[2]);

                    if (distance <= threshold) {
                        continue;
                    }

                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }

            if (maxX < minX || maxY < minY) {
                return FALLBACK_PERSON_BBOX;
            }

            final int padX = Math.max(2, width / 80);
            final int padY = Math.max(2, height / 80);
            minX = clampToRange(minX - padX, 0, width - 1);
            minY = clampToRange(minY - padY, 0, height - 1);
            maxX = clampToRange(maxX + padX, 0, width - 1);
            maxY = clampToRange(maxY + padY, 0, height - 1);

            final double nx = minX / (double) width;
            final double ny = minY / (double) height;
            final double nWidth = (maxX - minX + 1) / (double) width;
            final double nHeight = (maxY - minY + 1) / (double) height;
            final NormalizedBoundingBox detected = new NormalizedBoundingBox(nx, ny, nWidth, nHeight);
            return detected.isValid() ? detected : FALLBACK_PERSON_BBOX;
        } catch (IOException ex) {
            return FALLBACK_PERSON_BBOX;
        }
    }

    private BufferedImage decodeImage(byte[] bytes) throws IOException {
        final BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new ExternalServiceException("Unable to decode image for inpaint mask");
        }
        return source;
    }

    private int[] estimateBackgroundColor(BufferedImage source) {
        final int width = source.getWidth();
        final int height = source.getHeight();

        final int[][] points = new int[][]{
                {0, 0},
                {width - 1, 0},
                {0, height - 1},
                {width - 1, height - 1},
                {width / 2, 0},
                {width / 2, height - 1}
        };

        int sumR = 0;
        int sumG = 0;
        int sumB = 0;
        int count = 0;

        for (int[] point : points) {
            final int rgb = source.getRGB(clampToRange(point[0], 0, width - 1), clampToRange(point[1], 0, height - 1));
            sumR += (rgb >>> 16) & 0xFF;
            sumG += (rgb >>> 8) & 0xFF;
            sumB += rgb & 0xFF;
            count++;
        }

        return new int[]{
                sumR / Math.max(1, count),
                sumG / Math.max(1, count),
                sumB / Math.max(1, count)
        };
    }

    private int clampToRange(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
