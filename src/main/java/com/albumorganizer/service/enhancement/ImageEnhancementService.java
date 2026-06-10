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
        new NamedPrompt("Upscale & Sharpen",        "Upscale and sharpen details, improve edge clarity and fine textures"),
        new NamedPrompt("Denoise",                  "Reduce noise and film grain while preserving fine detail"),
        new NamedPrompt("Restore Old Photo",        "Restore old photo: fix faded colors, repair contrast, reduce dust and scratches"),
        new NamedPrompt("Enhance Portrait",         "Enhance portrait: smooth skin tones, sharpen eyes and hair, balance exposure"),
        new NamedPrompt("Increase Clarity",         "Increase overall clarity, sharpness and micro-contrast without over-sharpening"),
        new NamedPrompt("Fix Low Light",            "Fix low-light underexposed photo: lift shadows, reduce noise, restore natural colors"),
        new NamedPrompt("Vivid Colors",             "Boost color vibrancy and saturation while keeping skin tones natural"),
        new NamedPrompt("Black & White",            "Convert to high-quality black and white with rich tonal range and sharp detail")
    );

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
