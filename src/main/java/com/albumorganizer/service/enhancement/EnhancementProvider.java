package com.albumorganizer.service.enhancement;

import java.io.IOException;

public interface EnhancementProvider {

    String getName();

    /** Short lowercase identifier used in output filenames, e.g. "gemini", "grok". */
    default String getShortId() {
        return getName().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    boolean isConfigured();

    boolean supportsPrompt();

    /** Whether this provider can enhance video files. Default: false (image only). */
    default boolean supportsVideo() { return false; }

    EnhancementResult enhance(EnhancementRequest request) throws IOException;

    /**
     * Fetches the current account balance for this provider's API key.
     * Returns a human-readable string (e.g. "$12.34") or null if not supported / unavailable.
     */
    default String fetchBalance() { return null; }

    /**
     * Returns a human-readable estimated cost per image enhancement for this provider,
     * e.g. "~$0.04 / image". Returns null if free / local / unknown.
     */
    default String estimatedCostPerImage() { return null; }
}
