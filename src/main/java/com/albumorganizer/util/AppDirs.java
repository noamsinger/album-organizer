package com.albumorganizer.util;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves OS-appropriate directories for logs, caches, config, and reports.
 *
 * Mac:     logs    → ~/Library/Logs/album-organizer/
 *          reports → ~/Library/Logs/album-organizer/reports/
 *          cache   → ~/Library/Caches/album-organizer/
 * Windows: logs    → %APPDATA%\album-organizer\logs\
 *          reports → %APPDATA%\album-organizer\reports\
 *          cache   → %LOCALAPPDATA%\album-organizer\thumbnails\
 * Linux:   logs    → ~/.local/share/album-organizer/logs/
 *          reports → ~/.local/share/album-organizer/reports/
 *          cache   → ~/.cache/album-organizer/thumbnails/
 */
public final class AppDirs {

    private static final String APP = "album-organizer";
    private static final String HOME = System.getProperty("user.home");
    private static final String OS   = System.getProperty("os.name", "").toLowerCase();

    private AppDirs() {}

    public static Path logsDir() {
        if (isMac()) {
            return Paths.get(HOME, "Library", "Logs", APP);
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null) return Paths.get(appData, APP, "logs");
            return Paths.get(HOME, "AppData", "Roaming", APP, "logs");
        } else {
            return Paths.get(HOME, ".local", "share", APP, "logs");
        }
    }

    public static Path reportsDir() {
        if (isMac()) {
            return logsDir().resolve("reports");
        } else if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null) return Paths.get(appData, APP, "reports");
            return Paths.get(HOME, "AppData", "Roaming", APP, "reports");
        } else {
            return Paths.get(HOME, ".local", "share", APP, "reports");
        }
    }

    public static Path thumbnailsDir() {
        if (isMac()) {
            return Paths.get(HOME, "Library", "Caches", APP, "thumbnails");
        } else if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) return Paths.get(localAppData, APP, "thumbnails");
            return Paths.get(HOME, "AppData", "Local", APP, "thumbnails");
        } else {
            return Paths.get(HOME, ".cache", APP, "thumbnails");
        }
    }

    private static boolean isMac()     { return OS.contains("mac"); }
    private static boolean isWindows() { return OS.contains("win"); }
}
