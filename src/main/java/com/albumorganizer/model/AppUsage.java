package com.albumorganizer.model;

import com.albumorganizer.service.enhancement.EnhancementConfig;
import com.albumorganizer.service.enhancement.NamedPrompt;

import java.util.List;

/**
 * Persisted via album-organizer-usage.json.
 * Written immediately whenever the user interacts (zoom, view, prompts, window state, etc.).
 */
public record AppUsage(
    // UI state
    int fontSizeFactor,
    String lastSelectedFolder,
    boolean thumbnailView,
    boolean showArchivesInTree,

    // Enhancement usage state
    List<NamedPrompt> savedPrompts,
    List<String> checkedPromptTitles,
    String comfyUiCheckpoint,
    List<String> comfyUiCheckpoints,

    // Window state
    double windowWidth,
    double windowHeight,
    double windowX,
    double windowY,
    long lastScanDateEpochMilli,

    // Sort state: column key ("name","dateTaken","date","type","duration","resolution")
    String sortField,
    boolean sortAscending
) {
    public static AppUsage defaults() {
        return new AppUsage(
            0, null, false, true,
            List.of(), List.of("Adult Content Filter"), "", List.of(),
            1200.0, 800.0, -1, -1, -1,
            "name", true
        );
    }

    /** Build an AppUsage from in-memory objects + window state. */
    public static AppUsage from(AlbumOrganizerSettings s, EnhancementConfig e,
                                double windowWidth, double windowHeight,
                                double windowX, double windowY,
                                long lastScanDateEpochMilli,
                                String sortField, boolean sortAscending) {
        return new AppUsage(
            s.getFontSizeFactor(),
            s.getLastSelectedFolder(),
            s.isThumbnailView(),
            s.isShowArchivesInTree(),
            e.savedPrompts() != null ? e.savedPrompts() : List.of(),
            e.checkedPromptTitles() != null ? e.checkedPromptTitles() : List.of(),
            e.comfyUiCheckpoint() != null ? e.comfyUiCheckpoint() : "",
            e.comfyUiCheckpoints() != null ? e.comfyUiCheckpoints() : List.of(),
            windowWidth, windowHeight, windowX, windowY, lastScanDateEpochMilli,
            sortField != null ? sortField : "name", sortAscending
        );
    }

    /** Apply UI state fields onto an existing AlbumOrganizerSettings instance. */
    public void applyTo(AlbumOrganizerSettings s) {
        s.setFontSizeFactor(fontSizeFactor);
        s.setLastSelectedFolder(lastSelectedFolder);
        s.setThumbnailView(thumbnailView);
        s.setShowArchivesInTree(showArchivesInTree);
    }
}
