package io.github.yerektus.aitryon.tryon;

import io.github.yerektus.aitryon.domain.UserGender;

public record TryOnAnalyzeCommand(
        byte[] personImage,
        String personImageMime,
        byte[] clothingImage,
        String clothingImageMime,
        String clothingName,
        String clothingSize,
        int heightCm,
        int weightKg,
        UserGender gender,
        int ageYears
) {
}
