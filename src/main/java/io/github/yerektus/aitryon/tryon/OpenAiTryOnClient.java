package io.github.yerektus.aitryon.tryon;

import java.util.List;

public interface OpenAiTryOnClient {
    OpenAiTryOnResult generateInpaint(TryOnAnalyzeCommand command);

    List<TryOnStyleHint> suggestStyles(byte[] clothingImage, String clothingImageMime, String clothingName);
}
