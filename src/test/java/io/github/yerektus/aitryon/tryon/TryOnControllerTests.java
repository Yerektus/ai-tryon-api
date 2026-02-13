package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.security.AuthenticatedUser;
import io.github.yerektus.aitryon.tryon.dto.TryOnAnalyzeResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnOutputResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnStyleHintResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnStyleHintsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TryOnControllerTests {

    private TryOnService tryOnService;
    private TryOnController controller;

    @BeforeEach
    void setUp() {
        tryOnService = mock(TryOnService.class);
        controller = new TryOnController(tryOnService);
    }

    @Test
    void analyzePassesRequestToService() throws IOException {
        final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "u@example.com");
        final MockMultipartFile personImage = new MockMultipartFile(
                "personImage",
                "person.jpg",
                "image/jpeg",
                "person-bytes".getBytes(StandardCharsets.UTF_8)
        );
        final MockMultipartFile clothingImage = new MockMultipartFile(
                "clothingImage",
                "cloth.jpg",
                "image/jpeg",
                "cloth-bytes".getBytes(StandardCharsets.UTF_8)
        );

        when(tryOnService.analyze(eq(user.userId()), any(TryOnAnalyzeCommand.class)))
                .thenReturn(new TryOnAnalyzeResponse(
                        UUID.randomUUID(),
                        "Zm9v",
                        "image/png",
                        1,
                        5,
                        List.of(new TryOnOutputResponse("inpaint", "Zm9v", "image/png"))
                ));

        controller.analyze(
                user,
                personImage,
                clothingImage,
                "Jacket",
                "m",
                180,
                75,
                "male",
                30
        );

        final ArgumentCaptor<TryOnAnalyzeCommand> captor = ArgumentCaptor.forClass(TryOnAnalyzeCommand.class);
        verify(tryOnService).analyze(eq(user.userId()), captor.capture());
        assertThat(captor.getValue().clothingName()).isEqualTo("Jacket");
        assertThat(captor.getValue().clothingSize()).isEqualTo("m");
    }

    @Test
    void styleHintsDelegatesToService() throws IOException {
        final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "u@example.com");
        final MockMultipartFile clothingImage = new MockMultipartFile(
                "clothingImage",
                "cloth.jpg",
                "image/jpeg",
                "cloth-bytes".getBytes(StandardCharsets.UTF_8)
        );

        when(tryOnService.styleHints(eq(user.userId()), any(), eq("image/jpeg"), eq("Пальто")))
                .thenReturn(new TryOnStyleHintsResponse(List.of(
                        new TryOnStyleHintResponse("Casual", "Базовый образ"),
                        new TryOnStyleHintResponse("Office", "Деловой контекст"),
                        new TryOnStyleHintResponse("Minimal", "Чистые формы")
                )));

        final TryOnStyleHintsResponse response = controller.styleHints(user, clothingImage, "Пальто");

        assertThat(response.hints()).hasSize(3);
        verify(tryOnService).styleHints(eq(user.userId()), any(), eq("image/jpeg"), eq("Пальто"));
    }
}
