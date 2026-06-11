package com.albumorganizer.service.enhancement;

import com.albumorganizer.repository.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ImageEnhancementService {

    private static final Logger logger = LoggerFactory.getLogger(ImageEnhancementService.class);

    public static final List<NamedPrompt> DEFAULT_PROMPTS = List.of(
        new NamedPrompt("Adult Content Filter",
            "The output image must be completely free of explicit, sexual, nude, or adult content. " +
            "If the source image contains nudity, sexual acts, exposed intimate body parts, or suggestive adult material, " +
            "those elements must be replaced, covered, or removed so the result is appropriate for all audiences. " +
            "Do not generate, enhance, or preserve any form of adult-only imagery. " +
            "Apply this filter as a hard constraint regardless of any other instructions."),
        new NamedPrompt("Upscale & Sharpen",
            "Increase the resolution and sharpness of the image. Enhance edge definition, recover fine textures and detail, " +
            "and improve overall crispness without introducing halos, ringing, or artificial-looking over-sharpening."),
        new NamedPrompt("Denoise",
            "Remove digital noise, sensor grain, and compression artifacts from the image. " +
            "Smooth out noisy areas while preserving genuine edges, fine detail, and texture so the image looks clean but not over-processed."),
        new NamedPrompt("Restore Old Photo",
            "Restore a damaged or aged photograph to a clean, vibrant state. " +
            "Repair faded, washed-out, or discolored areas; correct low contrast; remove visible dust, scratches, spots, and creases; " +
            "and recover shadow and highlight detail, making the image look as it would have when originally taken."),
        new NamedPrompt("Enhance Portrait",
            "Improve the quality of a portrait photograph. Even out skin tones, soften blemishes naturally without losing skin texture, " +
            "sharpen eyes, eyelashes, and hair for better definition, and balance the overall exposure and color so the subject looks their best."),
        new NamedPrompt("Increase Clarity",
            "Increase the perceived sharpness and local contrast of the image to make it look crisper and more detailed. " +
            "Enhance mid-tone contrast and micro-detail without over-sharpening smooth areas or clipping highlights and shadows."),
        new NamedPrompt("Fix Low Light",
            "Correct an underexposed or low-light photograph. Brighten the image naturally, lift blocked-up shadows to reveal detail, " +
            "reduce noise introduced by high ISO or long exposure, and restore accurate, balanced colors without making the result look overlit or artificial."),
        new NamedPrompt("Vivid Colors",
            "Increase the color richness and vibrancy of the image. Boost saturation and color depth to make the image look lively and impactful, " +
            "while keeping skin tones natural and avoiding oversaturation in already vivid areas."),
        new NamedPrompt("Make Black & White",
            "Convert the image to a high-quality black-and-white photograph. " +
            "Produce a rich tonal range with deep blacks, bright highlights, and well-defined mid-tones. " +
            "Preserve fine detail and texture, and apply tonal mapping that gives the image a classic, timeless look.")
    );

    /** Returns true if the given title matches a built-in default prompt. */
    public static boolean isDefaultPrompt(String title) {
        return DEFAULT_PROMPTS.stream().anyMatch(p -> p.title().equals(title));
    }

    /**
     * Returns the saved prompts, prepending any default prompts whose titles are not
     * already present. This ensures users always see the defaults even if they had
     * saved prompts before defaults were introduced.
     */
    public static List<NamedPrompt> seedPrompts(List<NamedPrompt> savedPrompts) {
        List<NamedPrompt> result = new ArrayList<>(savedPrompts == null ? List.of() : savedPrompts);
        java.util.Set<String> existingTitles = new java.util.HashSet<>();
        for (NamedPrompt p : result) existingTitles.add(p.title());
        int insertAt = 0;
        for (NamedPrompt def : DEFAULT_PROMPTS) {
            if (!existingTitles.contains(def.title())) {
                result.add(insertAt++, def);
            }
        }
        return result;
    }

    private final List<EnhancementProvider> providers = new ArrayList<>();
    private final ConfigRepository configRepository;

    public ImageEnhancementService(ConfigRepository configRepository) {
        this.configRepository = configRepository;
        reloadProviders();
    }

    public void reloadProviders() {
        providers.clear();
        EnhancementConfig cfg = configRepository.getEnhancementConfig();

        if (cfg.stabilityAiEnabled() && !cfg.stabilityAiKey().isBlank()) {
            providers.add(new StabilityAIProvider(cfg.stabilityAiKey()));
        }
        if (cfg.openAiEnabled() && !cfg.openAiKey().isBlank()) {
            providers.add(new OpenAIDalleProvider(cfg.openAiKey()));
        }
        if (cfg.geminiEnabled() && !cfg.geminiKey().isBlank()) {
            providers.add(new GeminiProvider(cfg.geminiKey()));
        }
        if (cfg.grokEnabled() && !cfg.grokKey().isBlank()) {
            providers.add(new GrokProvider(cfg.grokKey()));
        }
        if (cfg.sdLocalEnabled()) {
            providers.add(new StableDiffusionLocalProvider(cfg.sdLocalUrl()));
        }
        if (cfg.comfyUiEnabled()) {
            providers.add(new ComfyUIProvider(cfg.comfyUiUrl(), cfg.comfyUiCheckpoint()));
        }
        if (cfg.invokeAiEnabled()) {
            providers.add(new InvokeAIProvider(cfg.invokeAiUrl()));
        }
        if (cfg.realEsrganEnabled()) {
            providers.add(new RealEsrganProvider(cfg.realEsrganModelPath()));
        }
    }

    public List<EnhancementProvider> getConfiguredProviders() {
        return providers.stream().filter(EnhancementProvider::isConfigured).toList();
    }

    /**
     * Resolves the output path for an enhanced image.
     * Pattern: {base}_AI-{provider}-{epoch}.jpg — always JPEG.
     */
    public static Path resolveOutputPath(Path inputPath, String providerName) {
        String filename = inputPath.getFileName().toString();
        Path parent = inputPath.getParent();
        int dot = filename.lastIndexOf('.');
        String base = dot >= 0 ? filename.substring(0, dot) : filename;

        // Sanitise provider name for use in a filename
        String providerSlug = providerName.replaceAll("[^a-zA-Z0-9_-]", "_");
        long epoch = System.currentTimeMillis() / 1000;
        String candidate = String.format("%s_AI-%s-%d.jpg", base, providerSlug, epoch);
        return parent.resolve(candidate);
    }

    /** Overload kept for back-compat — uses "Unknown" as provider. */
    public static Path resolveOutputPath(Path inputPath) {
        return resolveOutputPath(inputPath, "Unknown");
    }
}
