package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.billing.CreditService;
import io.github.yerektus.aitryon.common.BadRequestException;
import io.github.yerektus.aitryon.common.NotFoundException;
import io.github.yerektus.aitryon.common.PaymentRequiredException;
import io.github.yerektus.aitryon.domain.CreditLedgerReason;
import io.github.yerektus.aitryon.domain.TryOnJobEntity;
import io.github.yerektus.aitryon.domain.TryOnJobStatus;
import io.github.yerektus.aitryon.domain.UserEntity;
import io.github.yerektus.aitryon.domain.UserGender;
import io.github.yerektus.aitryon.domain.repo.TryOnJobRepository;
import io.github.yerektus.aitryon.domain.repo.UserRepository;
import io.github.yerektus.aitryon.tryon.dto.TryOnAnalyzeResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnHistoryItemResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnOutputResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnResultBinary;
import io.github.yerektus.aitryon.tryon.dto.TryOnStyleHintResponse;
import io.github.yerektus.aitryon.tryon.dto.TryOnStyleHintsResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
public class TryOnService {

    private final TryOnJobRepository tryOnJobRepository;
    private final UserRepository userRepository;
    private final OpenAiTryOnClient openAiTryOnClient;
    private final CreditService creditService;

    public TryOnService(TryOnJobRepository tryOnJobRepository,
                        UserRepository userRepository,
                        OpenAiTryOnClient openAiTryOnClient,
                        CreditService creditService) {
        this.tryOnJobRepository = tryOnJobRepository;
        this.userRepository = userRepository;
        this.openAiTryOnClient = openAiTryOnClient;
        this.creditService = creditService;
    }

    public TryOnAnalyzeResponse analyze(UUID userId, TryOnAnalyzeCommand command) {
        validate(command);

        final UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getCreditsBalance() < 1) {
            throw new PaymentRequiredException("Not enough credits");
        }

        final TryOnJobEntity job = new TryOnJobEntity();
        job.setUser(user);
        job.setPersonImage(command.personImage());
        job.setPersonImageMime(command.personImageMime());
        job.setClothingImage(command.clothingImage());
        job.setClothingImageMime(command.clothingImageMime());
        job.setClothingName(command.clothingName().trim());
        job.setClothingSize(command.clothingSize().trim());
        job.setHeightCm(command.heightCm());
        job.setWeightKg(command.weightKg());
        job.setGender(command.gender());
        job.setAgeYears(command.ageYears());
        job.setStatus(TryOnJobStatus.PROCESSING);
        job.setCreditsSpent(0);
        final TryOnJobEntity saved = tryOnJobRepository.save(job);

        try {
            final TryOnOutputImage inpaintOutput = toOutput(TryOnOutputId.INPAINT, openAiTryOnClient.generateInpaint(command));
            final List<TryOnOutputImage> generatedOutputs = List.of(inpaintOutput);

            saved.setResultImage(inpaintOutput.bytes());
            saved.setResultImageMime(inpaintOutput.mimeType());
            saved.setStatus(TryOnJobStatus.SUCCEEDED);
            saved.setErrorMessage(null);
            saved.setCreditsSpent(1);
            tryOnJobRepository.save(saved);

            final int remainingCredits = creditService.adjustCredits(
                    userId,
                    -1,
                    CreditLedgerReason.TRY_ON_CHARGE,
                    null,
                    saved
            );

            return new TryOnAnalyzeResponse(
                    saved.getId(),
                    Base64.getEncoder().encodeToString(inpaintOutput.bytes()),
                    inpaintOutput.mimeType(),
                    1,
                    remainingCredits,
                    generatedOutputs.stream()
                            .map(this::toResponseOutput)
                            .toList()
            );
        } catch (RuntimeException ex) {
            saved.setStatus(TryOnJobStatus.FAILED);
            saved.setErrorMessage(ex.getMessage());
            saved.setUpdatedAt(Instant.now());
            tryOnJobRepository.save(saved);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public TryOnStyleHintsResponse styleHints(UUID userId,
                                              byte[] clothingImage,
                                              String clothingImageMime,
                                              String clothingName) {
        userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (clothingImage == null || clothingImage.length == 0) {
            throw new BadRequestException("clothingImage is required");
        }

        final List<TryOnStyleHintResponse> hints = openAiTryOnClient
                .suggestStyles(clothingImage, clothingImageMime, clothingName)
                .stream()
                .map(hint -> new TryOnStyleHintResponse(hint.style(), hint.reason()))
                .toList();

        return new TryOnStyleHintsResponse(hints);
    }

    @Transactional(readOnly = true)
    public List<TryOnHistoryItemResponse> history(UUID userId, int limit) {
        final int safeLimit = Math.max(1, Math.min(100, limit));
        return tryOnJobRepository.findByUser_IdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(job -> new TryOnHistoryItemResponse(
                        job.getId(),
                        job.getStatus().name(),
                        job.getClothingName(),
                        job.getClothingSize(),
                        job.getCreditsSpent(),
                        job.getCreatedAt(),
                        job.getErrorMessage(),
                        job.getResultImage() != null && job.getResultImage().length > 0
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public TryOnResultBinary result(UUID userId, UUID jobId) {
        final TryOnJobEntity job = tryOnJobRepository.findByIdAndUser_Id(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Try-on job not found"));

        if (job.getStatus() != TryOnJobStatus.SUCCEEDED || job.getResultImage() == null || job.getResultImage().length == 0) {
            throw new NotFoundException("Result media not found");
        }

        return new TryOnResultBinary(job.getResultImage(), job.getResultImageMime());
    }

    private void validate(TryOnAnalyzeCommand command) {
        if (command.personImage() == null || command.personImage().length == 0) {
            throw new BadRequestException("personImage is required");
        }
        if (command.clothingImage() == null || command.clothingImage().length == 0) {
            throw new BadRequestException("clothingImage is required");
        }
        if (command.clothingName() == null || command.clothingName().isBlank()) {
            throw new BadRequestException("clothingName is required");
        }
        if (command.clothingSize() == null || command.clothingSize().isBlank()) {
            throw new BadRequestException("clothingSize is required");
        }
        if (command.heightCm() < 120 || command.heightCm() > 230) {
            throw new BadRequestException("heightCm must be between 120 and 230");
        }
        if (command.weightKg() < 35 || command.weightKg() > 250) {
            throw new BadRequestException("weightKg must be between 35 and 250");
        }
        if (command.ageYears() < 12 || command.ageYears() > 90) {
            throw new BadRequestException("ageYears must be between 12 and 90");
        }
        if (command.gender() == null || (command.gender() != UserGender.male && command.gender() != UserGender.female)) {
            throw new BadRequestException("gender must be male or female");
        }
    }

    private TryOnOutputImage toOutput(TryOnOutputId id, OpenAiTryOnResult result) {
        return new TryOnOutputImage(id, result.bytes(), result.mimeType());
    }

    private TryOnOutputResponse toResponseOutput(TryOnOutputImage image) {
        return new TryOnOutputResponse(
                image.id().apiValue(),
                Base64.getEncoder().encodeToString(image.bytes()),
                image.mimeType()
        );
    }
}
