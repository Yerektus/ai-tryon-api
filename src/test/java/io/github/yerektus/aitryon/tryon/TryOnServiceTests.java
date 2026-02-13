package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.billing.CreditService;
import io.github.yerektus.aitryon.common.ExternalServiceException;
import io.github.yerektus.aitryon.domain.CreditLedgerReason;
import io.github.yerektus.aitryon.domain.TryOnJobEntity;
import io.github.yerektus.aitryon.domain.TryOnJobStatus;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.UserGender;
import io.github.yerektus.aitryon.domain.repo.TryOnJobRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import io.github.yerektus.aitryon.tryon.dto.TryOnAnalyzeResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnStyleHintsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TryOnServiceTests {

    @Mock
    private TryOnJobRepository tryOnJobRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OpenAiTryOnClient openAiTryOnClient;
    @Mock
    private CreditService creditService;

    private TryOnService tryOnService;

    @BeforeEach
    void setUp() {
        tryOnService = new TryOnService(tryOnJobRepository, userRepository, openAiTryOnClient, creditService);
        lenient().when(tryOnJobRepository.save(any(TryOnJobEntity.class))).thenAnswer(invocation -> {
            final TryOnJobEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(UUID.randomUUID());
            }
            return entity;
        });
    }

    @Test
    void analyzeReturnsSingleInpaintOutputAndChargesOneCredit() {
        final UUID userId = UUID.randomUUID();
        final UserEntity user = createUser(userId, 7);
        final TryOnAnalyzeCommand command = createCommand();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(openAiTryOnClient.generateInpaint(command))
                .thenReturn(new OpenAiTryOnResult("inpaint".getBytes(StandardCharsets.UTF_8), "image/png"));
        when(creditService.adjustCredits(eq(userId), eq(-1), eq(CreditLedgerReason.TRY_ON_CHARGE), eq(null), any(TryOnJobEntity.class)))
                .thenReturn(6);

        final TryOnAnalyzeResponse response = tryOnService.analyze(userId, command);

        assertThat(response.outputs()).hasSize(1);
        assertThat(response.outputs().get(0).id()).isEqualTo("inpaint");
        assertThat(response.resultImageBase64())
                .isEqualTo(Base64.getEncoder().encodeToString("inpaint".getBytes(StandardCharsets.UTF_8)));
        assertThat(response.resultMimeType()).isEqualTo("image/png");
        assertThat(response.creditsSpent()).isEqualTo(1);
        assertThat(response.remainingCredits()).isEqualTo(6);

        verify(creditService).adjustCredits(eq(userId), eq(-1), eq(CreditLedgerReason.TRY_ON_CHARGE), eq(null), any(TryOnJobEntity.class));
    }

    @Test
    void analyzeDoesNotChargeCreditsWhenGenerationFails() {
        final UUID userId = UUID.randomUUID();
        final UserEntity user = createUser(userId, 4);
        final TryOnAnalyzeCommand command = createCommand();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(openAiTryOnClient.generateInpaint(command))
                .thenThrow(new ExternalServiceException("OpenAI down"));

        assertThatThrownBy(() -> tryOnService.analyze(userId, command))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("OpenAI down");

        verify(creditService, never()).adjustCredits(eq(userId), eq(-1), eq(CreditLedgerReason.TRY_ON_CHARGE), eq(null), any(TryOnJobEntity.class));

        final ArgumentCaptor<TryOnJobEntity> captor = ArgumentCaptor.forClass(TryOnJobEntity.class);
        verify(tryOnJobRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(captor.getAllValues().size() - 1).getStatus())
                .isEqualTo(TryOnJobStatus.FAILED);
    }

    @Test
    void styleHintsMapsClientHints() {
        final UUID userId = UUID.randomUUID();
        final UserEntity user = createUser(userId, 2);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(openAiTryOnClient.suggestStyles(any(), eq("image/jpeg"), eq("Пиджак")))
                .thenReturn(List.of(
                        new TryOnStyleHint("Casual", "Базовый вариант"),
                        new TryOnStyleHint("Office", "Подходит под деловой образ"),
                        new TryOnStyleHint("Minimal", "Чистые линии")
                ));

        final TryOnStyleHintsResponse response = tryOnService.styleHints(
                userId,
                "img".getBytes(StandardCharsets.UTF_8),
                "image/jpeg",
                "Пиджак"
        );

        assertThat(response.hints()).hasSize(3);
        assertThat(response.hints().get(0).style()).isEqualTo("Casual");
        assertThat(response.hints().get(1).reason()).contains("деловой");
    }

    private UserEntity createUser(UUID id, int credits) {
        final UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail("u@example.com");
        user.setDisplayName("User");
        user.setUsername("user");
        user.setCreditsBalance(credits);
        return user;
    }

    private TryOnAnalyzeCommand createCommand() {
        return new TryOnAnalyzeCommand(
                "person".getBytes(StandardCharsets.UTF_8),
                "image/jpeg",
                "clothing".getBytes(StandardCharsets.UTF_8),
                "image/jpeg",
                "Jacket",
                "m",
                180,
                75,
                UserGender.male,
                28
        );
    }
}
