package com.albumorganizer.model;

import com.albumorganizer.service.enhancement.EnhancementConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persisted via album-organizer-settings.json.
 * Only written when the user clicks Save in the Settings dialog.
 */
public record AppSettings(
    // Organize
    String organizeMode,
    boolean createYearFolder,
    boolean createMonthFolder,
    boolean createDayFolder,
    boolean splitLowRes,
    boolean splitMedRes,
    int lowResThresholdPixels,
    int hiResThresholdPixels,

    // Folders
    String targetFolder,
    String aiGeneratedFolder,
    String recycleBinFolder,
    List<String> albumFolders,

    // Enhancement providers
    boolean stabilityAiEnabled,
    String stabilityAiKey,
    boolean openAiEnabled,
    String openAiKey,
    boolean geminiEnabled,
    String geminiKey,
    double geminiTemperature,
    boolean grokEnabled,
    String grokKey,
    String grokModel,
    boolean sdLocalEnabled,
    String sdLocalUrl,
    boolean realEsrganEnabled,
    String realEsrganModelPath,
    boolean comfyUiEnabled,
    String comfyUiUrl,
    boolean invokeAiEnabled,
    String invokeAiUrl,

    // Cloud video providers
    boolean topazEnabled,
    String topazKey,
    boolean runwayEnabled,
    String runwayKey,
    boolean pikaEnabled,
    String pikaKey,
    boolean klingEnabled,
    String klingKey,

    // Local video providers
    boolean ffmpegEsrganEnabled,
    String ffmpegPath,
    String esrganBinaryPath,
    boolean video2xEnabled,
    String video2xPath,

    boolean debugProtocol
) {
    public static AppSettings defaults() {
        return new AppSettings(
            "COPY",
            true, true, true,
            true, true,
            300000, 1000000,
            null, null, null,
            List.of(),
            false, "",
            false, "",
            false, "", 1.0,
            false, "", "grok-imagine-image-quality",
            false, "http://localhost:7860",
            false, "",
            false, "http://localhost:8188",
            false, "http://localhost:9090",
            false, "",
            false, "",
            false, "",
            false, "",
            false, "", "",
            false, "video2x",
            false
        );
    }

    /** Build an AppSettings from in-memory objects. albumFolders comes from the caller. */
    public static AppSettings from(AlbumOrganizerSettings s, EnhancementConfig e, List<Path> albumFolders) {
        return new AppSettings(
            s.getMode().name(),
            s.isCreateYearFolder(), s.isCreateMonthFolder(), s.isCreateDayFolder(),
            s.isSplitLowRes(), s.isSplitMedRes(),
            s.getLowResThresholdPixels(), s.getHiResThresholdPixels(),
            s.getTargetFolder() != null ? s.getTargetFolder().toString() : null,
            s.getAiGeneratedFolder() != null ? s.getAiGeneratedFolder().toString() : null,
            s.getRecycleBinFolder() != null ? s.getRecycleBinFolder().toString() : null,
            albumFolders.stream().map(Path::toString).collect(Collectors.toList()),
            e.stabilityAiEnabled(), e.stabilityAiKey(),
            e.openAiEnabled(), e.openAiKey(),
            e.geminiEnabled(), e.geminiKey(), e.geminiTemperature(),
            e.grokEnabled(), e.grokKey(), e.grokModel(),
            e.sdLocalEnabled(), e.sdLocalUrl(),
            e.realEsrganEnabled(), e.realEsrganModelPath(),
            e.comfyUiEnabled(), nullToEmpty(e.comfyUiUrl(), "http://localhost:8188"),
            e.invokeAiEnabled(), nullToEmpty(e.invokeAiUrl(), "http://localhost:9090"),
            e.topazEnabled(), e.topazKey(),
            e.runwayEnabled(), e.runwayKey(),
            e.pikaEnabled(), e.pikaKey(),
            e.klingEnabled(), e.klingKey(),
            e.ffmpegEsrganEnabled(), e.ffmpegPath(), e.esrganBinaryPath(),
            e.video2xEnabled(), e.video2xPath(),
            e.debugProtocol()
        );
    }

    /** Produce the in-memory organize settings from this record. */
    public AlbumOrganizerSettings toOrganizeSettings() {
        AlbumOrganizerSettings s = new AlbumOrganizerSettings();
        try { s.setMode(AlbumOrganizerSettings.OrganizeMode.valueOf(organizeMode)); }
        catch (Exception ignored) {}
        s.setCreateYearFolder(createYearFolder);
        s.setCreateMonthFolder(createMonthFolder);
        s.setCreateDayFolder(createDayFolder);
        s.setSplitLowRes(splitLowRes);
        s.setSplitMedRes(splitMedRes);
        s.setLowResThresholdPixels(lowResThresholdPixels);
        s.setHiResThresholdPixels(hiResThresholdPixels);
        s.setTargetFolder(targetFolder != null ? Paths.get(targetFolder) : null);
        s.setAiGeneratedFolder(aiGeneratedFolder != null ? Paths.get(aiGeneratedFolder) : null);
        s.setRecycleBinFolder(recycleBinFolder != null ? Paths.get(recycleBinFolder) : null);
        return s;
    }

    /** Produce the in-memory EnhancementConfig (provider/key part only; prompts/usage come from AppUsage). */
    public EnhancementConfig toEnhancementConfig(AppUsage usage) {
        return new EnhancementConfig(
            stabilityAiEnabled, nullToEmpty(stabilityAiKey),
            openAiEnabled, nullToEmpty(openAiKey),
            geminiEnabled, nullToEmpty(geminiKey), geminiTemperature,
            grokEnabled, nullToEmpty(grokKey), nullToEmpty(grokModel, "grok-imagine-image-quality"),
            sdLocalEnabled, nullToEmpty(sdLocalUrl, "http://localhost:7860"),
            realEsrganEnabled, nullToEmpty(realEsrganModelPath),
            comfyUiEnabled, nullToEmpty(comfyUiUrl, "http://localhost:8188"),
            nullToEmpty(usage.comfyUiCheckpoint()),
            usage.comfyUiCheckpoints() != null ? usage.comfyUiCheckpoints() : List.of(),
            invokeAiEnabled, nullToEmpty(invokeAiUrl, "http://localhost:9090"),
            topazEnabled, nullToEmpty(topazKey),
            runwayEnabled, nullToEmpty(runwayKey),
            pikaEnabled, nullToEmpty(pikaKey),
            klingEnabled, nullToEmpty(klingKey),
            ffmpegEsrganEnabled, nullToEmpty(ffmpegPath), nullToEmpty(esrganBinaryPath),
            video2xEnabled, nullToEmpty(video2xPath, "video2x"),
            usage.savedPrompts() != null ? usage.savedPrompts() : List.of(),
            usage.checkedPromptTitles() != null ? usage.checkedPromptTitles() : List.of(),
            debugProtocol
        );
    }

    /** Return a copy of this record with only geminiTemperature and grokModel updated. */
    public AppSettings withProviderParams(double newGeminiTemperature, String newGrokModel) {
        return new AppSettings(
            organizeMode, createYearFolder, createMonthFolder, createDayFolder,
            splitLowRes, splitMedRes, lowResThresholdPixels, hiResThresholdPixels,
            targetFolder, aiGeneratedFolder, recycleBinFolder, albumFolders,
            stabilityAiEnabled, stabilityAiKey,
            openAiEnabled, openAiKey,
            geminiEnabled, geminiKey, newGeminiTemperature,
            grokEnabled, grokKey, newGrokModel,
            sdLocalEnabled, sdLocalUrl,
            realEsrganEnabled, realEsrganModelPath,
            comfyUiEnabled, comfyUiUrl,
            invokeAiEnabled, invokeAiUrl,
            topazEnabled, topazKey,
            runwayEnabled, runwayKey,
            pikaEnabled, pikaKey,
            klingEnabled, klingKey,
            ffmpegEsrganEnabled, ffmpegPath, esrganBinaryPath,
            video2xEnabled, video2xPath,
            debugProtocol
        );
    }

    /** Produce album folder paths from the stored strings. */
    public List<Path> toAlbumFolderPaths() {
        if (albumFolders == null) return List.of();
        return albumFolders.stream()
            .filter(f -> f != null && !f.isBlank())
            .map(Paths::get)
            .collect(Collectors.toList());
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String nullToEmpty(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s : fallback;
    }
}
