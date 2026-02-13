package io.github.yerektus.aitryon.tryon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yerektus.aitryon.common.BadRequestException;
import io.github.yerektus.aitryon.common.ExternalServiceException;
import io.github.yerektus.aitryon.config.OpenAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class OpenAiTryOnClientImpl implements OpenAiTryOnClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiTryOnClientImpl.class);
    private static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_IMAGE_EDIT_MODEL = "gpt-image-1";
    private static final String DALL_E_2_IMAGE_EDIT_MODEL = "dall-e-2";
    private static final String DEFAULT_STYLE_HINT_MODEL = "gpt-4.1-mini";
    private static final String DEFAULT_IMAGE_MIME = MediaType.IMAGE_PNG_VALUE;
    private static final String MODERATION_BLOCK_USER_MESSAGE =
            "Запрос отклонен модерацией OpenAI. Используйте нейтральные фотографии без откровенного контента.";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;
    private final InpaintMaskBuilder inpaintMaskBuilder;

    public OpenAiTryOnClientImpl(HttpClient httpClient, ObjectMapper objectMapper, OpenAiProperties openAiProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.inpaintMaskBuilder = new InpaintMaskBuilder();
    }

    @Override
    public OpenAiTryOnResult generateInpaint(TryOnAnalyzeCommand command) {
        ensureConfigured();

        final byte[] personImagePng = toPng(command.personImage());
        final byte[] clothingImagePng = toPng(command.clothingImage());
        final GarmentRegion garmentRegion = detectGarmentRegion(command.clothingName());
        final NormalizedBoundingBox maskRegion = resolveMaskRegion(personImagePng, garmentRegion);
        final byte[] maskBytes = inpaintMaskBuilder.buildMaskPng(personImagePng, maskRegion);
        final JsonNode body = sendImageEditRequest(
                buildInpaintPrompt(command, garmentRegion, maskRegion),
                personImagePng,
                MediaType.IMAGE_PNG_VALUE,
                clothingImagePng,
                MediaType.IMAGE_PNG_VALUE,
                maskBytes
        );

        final String imageBase64 = extractImageEditBase64(body);
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new ExternalServiceException("OpenAI image edit response does not contain image");
        }

        return decodeBase64Image(imageBase64);
    }

    @Override
    public List<TryOnStyleHint> suggestStyles(byte[] clothingImage, String clothingImageMime, String clothingName) {
        ensureConfigured();

        final Map<String, Object> root = new HashMap<>();
        root.put("model", resolveStyleHintModel());
        root.put("max_output_tokens", 500);

        final List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of(
                "type", "input_text",
                "text", buildStyleHintPrompt(clothingName)
        ));
        content.add(Map.of(
                "type", "input_image",
                "image_url", buildDataUri(clothingImageMime, clothingImage)
        ));

        root.put("input", List.of(Map.of(
                "role", "user",
                "content", content
        )));

        final JsonNode body = sendJsonRequest("/responses", root);
        final String outputText = extractOutputText(body);
        final List<TryOnStyleHint> parsed = parseStyleHints(outputText);

        return ensureThreeHints(parsed, clothingName);
    }

    private void ensureConfigured() {
        if (openAiProperties.getApiKey() == null || openAiProperties.getApiKey().isBlank()) {
            throw new BadRequestException("OpenAI не настроен на сервере. Установите OPENAI_API_KEY в backend окружении.");
        }
    }

    private JsonNode sendJsonRequest(String path, Object payload) {
        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveOpenAiBaseUrl() + path))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(payload), StandardCharsets.UTF_8))
                    .build();

            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            final String requestId = extractRequestId(response);
            log.info("OpenAI request: endpoint=/responses status={} request_id={}", response.statusCode(), requestId);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException(
                        withRequestId(normalizeOpenAiError(extractApiError(response.body())), requestId)
                );
            }

            return objectMapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("OpenAI request interrupted");
        } catch (IOException ex) {
            throw new ExternalServiceException("OpenAI response parsing failed");
        }
    }

    private JsonNode sendImageEditRequest(String prompt,
                                          byte[] baseImage,
                                          String baseImageMime,
                                          byte[] referenceImage,
                                          String referenceImageMime,
                                          byte[] maskPngBytes) {
        final String boundary = "----AitryonBoundary" + UUID.randomUUID();
        final byte[] bodyBytes = buildImageEditMultipartBody(
                boundary,
                prompt,
                baseImage,
                baseImageMime,
                referenceImage,
                referenceImageMime,
                maskPngBytes
        );

        try {
            final HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveOpenAiBaseUrl() + "/images/edits"))
                    .header("Authorization", "Bearer " + openAiProperties.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
                    .build();

            final HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            final String requestId = extractRequestId(response);
            log.info("OpenAI request: endpoint=/images/edits status={} request_id={}", response.statusCode(), requestId);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalServiceException(
                        withRequestId(normalizeOpenAiError(extractApiError(response.body())), requestId)
                );
            }

            final String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("");
            return parseImageEditResponse(response.body(), contentType);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("OpenAI image edit request interrupted");
        } catch (ExternalServiceException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ExternalServiceException("OpenAI image edit parsing failed");
        }
    }

    private byte[] buildImageEditMultipartBody(String boundary,
                                               String prompt,
                                               byte[] baseImage,
                                               String baseImageMime,
                                               byte[] referenceImage,
                                               String referenceImageMime,
                                               byte[] maskPngBytes) {
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final String model = resolveImageEditModel();
            final boolean isDallE2 = isDallE2Model(model);
            final String imageFieldName = isDallE2 ? "image" : "image[]";

            writeTextPart(output, boundary, "model", model);
            writeTextPart(output, boundary, "prompt", prompt);
            if (isDallE2) {
                writeTextPart(output, boundary, "response_format", "b64_json");
            }
            writeFilePart(
                    output,
                    boundary,
                    imageFieldName,
                    "person-image.png",
                    safeMime(baseImageMime),
                    baseImage
            );
            if (!isDallE2 && referenceImage != null && referenceImage.length > 0) {
                writeFilePart(
                        output,
                        boundary,
                        imageFieldName,
                        "clothing-image.png",
                        safeMime(referenceImageMime),
                        referenceImage
                );
            }
            writeFilePart(
                    output,
                    boundary,
                    "mask",
                    "person-mask.png",
                    MediaType.IMAGE_PNG_VALUE,
                    maskPngBytes
            );

            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to build multipart request", ex);
        }
    }

    private void writeTextPart(ByteArrayOutputStream output, String boundary, String name, String value) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFilePart(ByteArrayOutputStream output,
                               String boundary,
                               String name,
                               String filename,
                               String contentType,
                               byte[] bytes) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private OpenAiTryOnResult decodeBase64Image(String imageBase64) {
        try {
            final byte[] imageBytes = Base64.getDecoder().decode(imageBase64);
            if (imageBytes.length == 0) {
                throw new ExternalServiceException("OpenAI returned empty image");
            }
            return new OpenAiTryOnResult(imageBytes, DEFAULT_IMAGE_MIME);
        } catch (IllegalArgumentException ex) {
            throw new ExternalServiceException("OpenAI returned invalid image payload");
        }
    }

    private JsonNode parseImageEditResponse(byte[] responseBodyBytes, String contentType) throws IOException {
        if (responseBodyBytes == null || responseBodyBytes.length == 0) {
            throw new ExternalServiceException("OpenAI image edit response is empty");
        }

        if (isJsonContentType(contentType) || looksLikeJsonPayload(responseBodyBytes)) {
            return objectMapper.readTree(responseBodyBytes);
        }

        if (isImageContentType(contentType) || looksLikeImagePayload(responseBodyBytes)) {
            return objectMapper.createObjectNode()
                    .put("image_base64", Base64.getEncoder().encodeToString(responseBodyBytes));
        }

        final String bodyText = new String(responseBodyBytes, StandardCharsets.UTF_8);
        final String snippet = bodyText.length() > 300 ? bodyText.substring(0, 300) + "..." : bodyText;
        throw new ExternalServiceException("OpenAI image edit unexpected response: " + snippet);
    }

    private byte[] toPng(byte[] sourceBytes) {
        try {
            final BufferedImage source = ImageIO.read(new ByteArrayInputStream(sourceBytes));
            if (source == null) {
                throw new ExternalServiceException("Unable to decode person image");
            }

            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final boolean written = ImageIO.write(source, "png", output);
            if (!written) {
                throw new ExternalServiceException("Unable to encode person image as PNG");
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ExternalServiceException("Unable to prepare person image");
        }
    }

    private String buildInpaintPrompt(TryOnAnalyzeCommand command,
                                      GarmentRegion garmentRegion,
                                      NormalizedBoundingBox maskRegion) {
        final String gender = command.gender() == null ? "unknown" : command.gender().name();
        return """
                Highest priority: create an almost identical copy of the original person photo.
                Edit only the transparent mask area and keep all opaque pixels exactly unchanged.
                Replace only the target garment with the garment from the second input image.
                Preserve identity, face, hair, skin, body shape, pose, camera angle, lighting, shadows, background and all non-target details.
                Garment metadata:
                - clothing_name: %s
                - clothing_size: %s
                - gender: %s
                - age_years: %d
                - height_cm: %d
                - weight_kg: %d
                - target_region: %s
                - mask_bbox: x=%.3f, y=%.3f, width=%.3f, height=%.3f
                %s
                Keep garment color and texture from the reference image.
                Photorealistic result, no text, no watermark, no extra objects, no restyling, no global recolor.
                """.formatted(
                command.clothingName(),
                command.clothingSize(),
                gender,
                command.ageYears(),
                command.heightCm(),
                command.weightKg(),
                garmentRegion.regionKey(),
                maskRegion.x(),
                maskRegion.y(),
                maskRegion.width(),
                maskRegion.height(),
                garmentRegion.regionConstraint()
        );
    }

    private NormalizedBoundingBox resolveMaskRegion(byte[] personImagePng, GarmentRegion garmentRegion) {
        final NormalizedBoundingBox personBounds = inpaintMaskBuilder.detectForegroundBoundingBox(personImagePng);

        final double expandedX = clamp01(personBounds.x() - personBounds.width() * garmentRegion.horizontalPadding());
        final double expandedRight = clamp01(
                personBounds.x() + personBounds.width() + personBounds.width() * garmentRegion.horizontalPadding()
        );

        final double regionTop = clamp01(personBounds.y() + personBounds.height() * garmentRegion.relativeTop());
        final double regionBottom = clamp01(
                personBounds.y() + personBounds.height() * (garmentRegion.relativeTop() + garmentRegion.relativeHeight())
        );

        final double regionWidth = Math.max(0.02, expandedRight - expandedX);
        final double regionHeight = Math.max(0.02, regionBottom - regionTop);
        final NormalizedBoundingBox resolved = new NormalizedBoundingBox(expandedX, regionTop, regionWidth, regionHeight);
        return resolved.isValid() ? resolved : InpaintMaskBuilder.FALLBACK_TORSO_BBOX;
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private GarmentRegion detectGarmentRegion(String clothingName) {
        final String normalized = clothingName == null ? "" : clothingName.toLowerCase(Locale.ROOT);

        if (containsAny(normalized,
                "кросс", "кед", "ботин", "туф", "сапог", "сланц", "обув",
                "sneaker", "shoe", "boot", "loafer", "heel", "footwear")) {
            return new GarmentRegion(
                    "footwear",
                    0.81,
                    0.19,
                    0.1,
                    "Change only footwear. Keep all tops, bottoms and outerwear unchanged."
            );
        }

        if (containsAny(normalized,
                "плать", "комбинез", "overall", "jumpsuit", "onesie", "dress", "robe")) {
            return new GarmentRegion(
                    "full_body",
                    0.08,
                    0.86,
                    0.08,
                    "Change only this full-body garment. Keep shoes, face and background unchanged."
            );
        }

        if (containsAny(normalized,
                "брюк", "джинс", "юбк", "шорт", "леггин", "штаны", "трико", "низ",
                "pants", "jeans", "shorts", "skirt", "trousers", "jogger", "bottom")) {
            return new GarmentRegion(
                    "lower_body",
                    0.43,
                    0.50,
                    0.08,
                    "Change only lower-body clothing. Keep tops and footwear unchanged."
            );
        }

        return new GarmentRegion(
                "upper_body",
                0.08,
                0.40,
                0.08,
                "Change only upper-body clothing. Keep lower-body clothing and footwear unchanged."
        );
    }

    private boolean containsAny(String value, String... tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String buildStyleHintPrompt(String clothingName) {
        final String safeName = clothingName == null || clothingName.isBlank() ? "unknown garment" : clothingName.trim();
        return """
                Ты стилист. По фото вещи предложи 3 стиля образа.
                Верни СТРОГО JSON без markdown:
                {"hints":[{"style":"...","reason":"..."},{"style":"...","reason":"..."},{"style":"...","reason":"..."}]}
                Ограничения:
                - style: 1-2 слова
                - reason: коротко, до 90 символов, на русском
                - ничего кроме JSON
                Название вещи: %s
                """.formatted(safeName);
    }

    private String extractImageEditBase64(JsonNode body) {
        final String direct = extractGeneratedImageBase64(body);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }

        final JsonNode data = body.path("data");
        if (!data.isArray()) {
            return null;
        }

        for (JsonNode item : data) {
            final String base64 = firstNonBlank(
                    item.path("b64_json").asText(null),
                    item.path("image_base64").asText(null)
            );
            if (base64 != null) {
                return base64;
            }
        }
        return null;
    }

    private String extractGeneratedImageBase64(JsonNode body) {
        final JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                final String itemType = item.path("type").asText("");
                if ("image_generation_call".equals(itemType)) {
                    final String result = item.path("result").asText(null);
                    if (result != null && !result.isBlank()) {
                        return result;
                    }
                }

                final String fromContent = extractImageFromContent(item.path("content"));
                if (fromContent != null && !fromContent.isBlank()) {
                    return fromContent;
                }
            }
        }

        final String directData = firstNonBlank(
                body.path("image_base64").asText(null),
                body.path("b64_json").asText(null),
                body.path("result").asText(null)
        );
        if (directData != null) {
            return directData;
        }

        final JsonNode data = body.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                final String base64 = firstNonBlank(
                        item.path("b64_json").asText(null),
                        item.path("image_base64").asText(null),
                        item.path("result").asText(null)
                );
                if (base64 != null) {
                    return base64;
                }
            }
        }

        return null;
    }

    private String extractImageFromContent(JsonNode content) {
        if (!content.isArray()) {
            return null;
        }

        for (JsonNode part : content) {
            final String type = part.path("type").asText("");
            if ("output_image".equals(type) || "image".equals(type) || "image_generation_call".equals(type)) {
                final String base64 = firstNonBlank(
                        part.path("image_base64").asText(null),
                        part.path("b64_json").asText(null),
                        part.path("result").asText(null)
                );
                if (base64 != null) {
                    return base64;
                }
            }
        }

        return null;
    }

    private String extractOutputText(JsonNode body) {
        final JsonNode output = body.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                final String fromContent = extractTextFromContent(item.path("content"));
                if (fromContent != null && !fromContent.isBlank()) {
                    return fromContent;
                }
            }
        }

        return firstNonBlank(
                body.path("output_text").asText(null),
                body.path("text").asText(null),
                body.path("result").asText(null)
        );
    }

    private String extractTextFromContent(JsonNode content) {
        if (!content.isArray()) {
            return null;
        }

        final StringBuilder builder = new StringBuilder();
        for (JsonNode part : content) {
            final String type = part.path("type").asText("");
            if (!"output_text".equals(type) && !"text".equals(type)) {
                continue;
            }

            final String text = firstNonBlank(
                    part.path("text").asText(null),
                    part.path("text").path("value").asText(null),
                    part.path("output_text").asText(null)
            );
            if (text == null || text.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(text);
        }

        return builder.isEmpty() ? null : builder.toString();
    }

    private List<TryOnStyleHint> parseStyleHints(String outputText) {
        if (outputText == null || outputText.isBlank()) {
            return List.of();
        }

        try {
            final JsonNode node = parseJsonFromText(outputText);
            if (node == null) {
                return List.of();
            }

            final JsonNode hintsNode = node.isArray() ? node : node.path("hints");
            if (!hintsNode.isArray()) {
                return List.of();
            }

            final List<TryOnStyleHint> parsed = new ArrayList<>();
            for (JsonNode item : hintsNode) {
                final String style = trimToNull(item.path("style").asText(null));
                final String reason = trimToNull(item.path("reason").asText(null));
                if (style == null || reason == null) {
                    continue;
                }
                parsed.add(new TryOnStyleHint(style, reason));
            }
            return parsed;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<TryOnStyleHint> ensureThreeHints(List<TryOnStyleHint> parsed, String clothingName) {
        final List<TryOnStyleHint> hints = new ArrayList<>();
        if (parsed != null) {
            for (TryOnStyleHint hint : parsed) {
                if (hint == null) {
                    continue;
                }
                final String style = trimToNull(hint.style());
                final String reason = trimToNull(hint.reason());
                if (style == null || reason == null) {
                    continue;
                }
                hints.add(new TryOnStyleHint(style, reason));
                if (hints.size() == 3) {
                    break;
                }
            }
        }

        if (hints.size() == 3) {
            return hints;
        }

        final String safeName = clothingName == null || clothingName.isBlank()
                ? "эта вещь"
                : clothingName.trim();

        final List<TryOnStyleHint> fallback = List.of(
                new TryOnStyleHint("Casual", "Базовый повседневный образ, хорошо сочетается с " + safeName + "."),
                new TryOnStyleHint("Streetwear", "Добавит акцент и фактуру, подойдет для городских сочетаний."),
                new TryOnStyleHint("Minimal", "Чистый силуэт и спокойные цвета подчеркнут форму вещи.")
        );

        for (TryOnStyleHint hint : fallback) {
            if (hints.size() == 3) {
                break;
            }
            final boolean alreadyExists = hints.stream()
                    .anyMatch(existing -> existing.style().equalsIgnoreCase(hint.style()));
            if (!alreadyExists) {
                hints.add(hint);
            }
        }

        return hints;
    }

    private JsonNode parseJsonFromText(String text) {
        if (text == null) {
            return null;
        }

        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        final String withoutFence = stripCodeFence(trimmed);
        try {
            return objectMapper.readTree(withoutFence);
        } catch (JsonProcessingException ignored) {
            final int start = withoutFence.indexOf('{');
            final int end = withoutFence.lastIndexOf('}');
            if (start >= 0 && end > start) {
                final String sub = withoutFence.substring(start, end + 1);
                try {
                    return objectMapper.readTree(sub);
                } catch (JsonProcessingException ignoredSub) {
                    return null;
                }
            }
            return null;
        }
    }

    private String stripCodeFence(String text) {
        String candidate = text;
        if (candidate.startsWith("```")) {
            candidate = candidate.substring(3);
            if (candidate.startsWith("json")) {
                candidate = candidate.substring(4);
            }
            final int closing = candidate.lastIndexOf("```");
            if (closing >= 0) {
                candidate = candidate.substring(0, closing);
            }
        }
        return candidate.trim();
    }

    private String buildDataUri(String mimeType, byte[] bytes) {
        return "data:" + safeMime(mimeType) + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        final String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("application/json") || normalized.contains("+json");
    }

    private boolean isImageContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private boolean looksLikeJsonPayload(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int index = 0;

        if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            index = 3;
        }

        while (index < bytes.length && Character.isWhitespace((char) bytes[index])) {
            index++;
        }
        if (index >= bytes.length) {
            return false;
        }

        final char first = (char) bytes[index];
        return first == '{' || first == '[';
    }

    private boolean looksLikeImagePayload(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return false;
        }

        final boolean isPng =
                (bytes[0] & 0xFF) == 0x89 &&
                        (bytes[1] & 0xFF) == 0x50 &&
                        (bytes[2] & 0xFF) == 0x4E &&
                        (bytes[3] & 0xFF) == 0x47;
        final boolean isJpeg =
                (bytes[0] & 0xFF) == 0xFF &&
                        (bytes[1] & 0xFF) == 0xD8 &&
                        (bytes[2] & 0xFF) == 0xFF;
        final boolean isWebp =
                bytes.length >= 12 &&
                        bytes[0] == 'R' &&
                        bytes[1] == 'I' &&
                        bytes[2] == 'F' &&
                        bytes[3] == 'F' &&
                        bytes[8] == 'W' &&
                        bytes[9] == 'E' &&
                        bytes[10] == 'B' &&
                        bytes[11] == 'P';

        return isPng || isJpeg || isWebp;
    }

    private String safeMime(String mimeType) {
        return (mimeType == null || mimeType.isBlank()) ? MediaType.IMAGE_JPEG_VALUE : mimeType;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        final String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeOpenAiError(String message) {
        if (message == null || message.isBlank()) {
            return "OpenAI request failed";
        }
        if (isModerationLike(message)) {
            return MODERATION_BLOCK_USER_MESSAGE;
        }
        return message;
    }

    private boolean isModerationLike(String message) {
        final String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("moderation")
                || normalized.contains("content_policy")
                || normalized.contains("safety")
                || normalized.contains("policy violation")
                || normalized.contains("blocked")
                || normalized.contains("violation");
    }

    private String extractApiError(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            final JsonNode node = objectMapper.readTree(body);
            if (node.has("error") && node.get("error").has("message")) {
                return node.get("error").get("message").asText();
            }
            if (node.has("message")) {
                return node.get("message").asText();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractApiError(byte[] bodyBytes) {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return null;
        }
        return extractApiError(new String(bodyBytes, StandardCharsets.UTF_8));
    }

    private String withRequestId(String message, String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return message;
        }
        final String safeMessage = (message == null || message.isBlank()) ? "OpenAI request failed" : message;
        return safeMessage + " [request_id=" + requestId + "]";
    }

    private String extractRequestId(HttpResponse<?> response) {
        if (response == null) {
            return null;
        }
        return response.headers()
                .firstValue("x-request-id")
                .or(() -> response.headers().firstValue("request-id"))
                .orElse(null);
    }

    private String resolveOpenAiBaseUrl() {
        final String configured = openAiProperties.getBaseUrl();
        final String base = (configured == null || configured.isBlank()) ? DEFAULT_OPENAI_BASE_URL : configured;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String resolveImageEditModel() {
        final String imageEditModel = openAiProperties.getImageEditModel();
        if (imageEditModel != null && !imageEditModel.isBlank()) {
            return imageEditModel.trim();
        }

        final String model = openAiProperties.getModel();
        if (model != null && !model.isBlank() && isImageEditCapableModel(model)) {
            return model.trim();
        }

        return DEFAULT_IMAGE_EDIT_MODEL;
    }

    private boolean isDallE2Model(String model) {
        if (model == null) {
            return false;
        }
        return DALL_E_2_IMAGE_EDIT_MODEL.equalsIgnoreCase(model.trim());
    }

    private boolean isImageEditCapableModel(String model) {
        if (model == null) {
            return false;
        }
        final String normalized = model.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("gpt-image-") || DALL_E_2_IMAGE_EDIT_MODEL.equalsIgnoreCase(normalized);
    }

    private String resolveStyleHintModel() {
        final String configured = openAiProperties.getStyleHintModel();
        return (configured == null || configured.isBlank()) ? DEFAULT_STYLE_HINT_MODEL : configured;
    }

    private record GarmentRegion(
            String regionKey,
            double relativeTop,
            double relativeHeight,
            double horizontalPadding,
            String regionConstraint
    ) {
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize OpenAI payload", ex);
        }
    }
}
