package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.domain.UserGender;
import io.github.yerektus.aitryon.security.AuthenticatedUser;
import io.github.yerektus.aitryon.tryon.dto.TryOnAnalyzeResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnHistoryItemResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnResultBinary;
import io.github.yerektus.aitryon.tryon.dto.TryOnStyleHintsResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/try-on")
public class TryOnController {

    private final TryOnService tryOnService;

    public TryOnController(TryOnService tryOnService) {
        this.tryOnService = tryOnService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TryOnAnalyzeResponse analyze(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestPart("personImage") MultipartFile personImage,
                                        @RequestPart("clothingImage") MultipartFile clothingImage,
                                        @RequestParam("clothingName") String clothingName,
                                        @RequestParam("clothingSize") String clothingSize,
                                        @RequestParam("heightCm") int heightCm,
                                        @RequestParam("weightKg") int weightKg,
                                        @RequestParam("gender") String gender,
                                        @RequestParam("ageYears") int ageYears) throws IOException {

        final TryOnAnalyzeCommand command = new TryOnAnalyzeCommand(
                personImage.getBytes(),
                normalizeInputImageMime(personImage.getContentType()),
                clothingImage.getBytes(),
                normalizeInputImageMime(clothingImage.getContentType()),
                clothingName,
                clothingSize,
                heightCm,
                weightKg,
                parseGender(gender),
                ageYears
        );

        return tryOnService.analyze(user.userId(), command);
    }

    @PostMapping(value = "/style-hints", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TryOnStyleHintsResponse styleHints(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestPart("clothingImage") MultipartFile clothingImage,
                                              @RequestParam(name = "clothingName", required = false) String clothingName) throws IOException {
        return tryOnService.styleHints(
                user.userId(),
                clothingImage.getBytes(),
                normalizeInputImageMime(clothingImage.getContentType()),
                clothingName
        );
    }

    @GetMapping("/history")
    public List<TryOnHistoryItemResponse> history(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return tryOnService.history(user.userId(), limit);
    }

    @GetMapping("/jobs/{jobId}/result")
    public ResponseEntity<byte[]> result(@AuthenticationPrincipal AuthenticatedUser user,
                                         @PathVariable UUID jobId) {
        final TryOnResultBinary binary = tryOnService.result(user.userId(), jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, normalizeResultMime(binary.mimeType()))
                .body(binary.bytes());
    }

    private String normalizeInputImageMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        return mime;
    }

    private String normalizeResultMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        return mime;
    }

    private UserGender parseGender(String value) {
        if (value == null) {
            return null;
        }

        final String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "male" -> UserGender.male;
            case "female" -> UserGender.female;
            default -> null;
        };
    }
}
