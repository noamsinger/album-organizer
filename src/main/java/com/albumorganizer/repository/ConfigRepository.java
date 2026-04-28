package com.albumorganizer.repository;

import com.albumorganizer.model.AlbumOrganizerSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Repository for application configuration.
 * Uses INI file for all settings including album folders, target folder, and organize settings.
 * Java Preferences API is only used for window position/size.
 */
public class ConfigRepository {

    private static final Logger logger = LoggerFactory.getLogger(ConfigRepository.class);

    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".album-organizer";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "album-organizer-config.ini";

    private static final String LAST_SCAN_DATE_KEY = "lastScanDate";
    private static final String WINDOW_WIDTH_KEY = "windowWidth";
    private static final String WINDOW_HEIGHT_KEY = "windowHeight";
    private static final String WINDOW_X_KEY = "windowX";
    private static final String WINDOW_Y_KEY = "windowY";

    private static final double DEFAULT_WIDTH = 1200.0;
    private static final double DEFAULT_HEIGHT = 800.0;

    private final Preferences prefs;

    public ConfigRepository() {
        this.prefs = Preferences.userNodeForPackage(ConfigRepository.class);
        ensureConfigDirectoryExists();
    }

    /**
     * Ensures the config directory exists.
     */
    private void ensureConfigDirectoryExists() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                logger.info("Created config directory: {}", CONFIG_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create config directory: {}", CONFIG_DIR, e);
        }
    }

    /**
     * Reads all configuration from the INI file.
     *
     * @return map of section -> key -> value
     */
    private Map<String, Map<String, String>> readIniFile() {
        Map<String, Map<String, String>> config = new HashMap<>();
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            return config;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            String currentSection = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // Skip comments and empty lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Section header
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentSection = line.substring(1, line.length() - 1);
                    config.putIfAbsent(currentSection, new LinkedHashMap<>());
                    continue;
                }

                // Key-value pair
                if (currentSection != null && line.contains("=")) {
                    int equalIndex = line.indexOf('=');
                    String key = line.substring(0, equalIndex).trim();
                    String value = line.substring(equalIndex + 1).trim();
                    config.get(currentSection).put(key, value);
                } else if (currentSection != null) {
                    // Line without '=' in certain sections (like AlbumFolders) is a folder path
                    if (currentSection.equals("AlbumFolders") || currentSection.equals("AnchoredFolders")) {
                        // Store as numbered keys
                        int index = config.get(currentSection).size();
                        config.get(currentSection).put("folder" + index, line);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read config file: {}", CONFIG_FILE, e);
        }

        return config;
    }

    /**
     * Writes all configuration to the INI file.
     */
    private void writeIniFile(Map<String, Map<String, String>> config) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
            writer.write("# Album Organizer Configuration File");
            writer.newLine();
            writer.write("# This file stores all application settings");
            writer.newLine();
            writer.newLine();

            // Write each section
            for (Map.Entry<String, Map<String, String>> section : config.entrySet()) {
                writer.write("[" + section.getKey() + "]");
                writer.newLine();

                // Special handling for AlbumFolders section
                if (section.getKey().equals("AlbumFolders")) {
                    for (Map.Entry<String, String> entry : section.getValue().entrySet()) {
                        if (entry.getKey().startsWith("folder")) {
                            writer.write(entry.getValue());
                            writer.newLine();
                        }
                    }
                } else {
                    // Regular key=value format
                    for (Map.Entry<String, String> entry : section.getValue().entrySet()) {
                        writer.write(entry.getKey() + "=" + entry.getValue());
                        writer.newLine();
                    }
                }
                writer.newLine();
            }

            logger.debug("Wrote configuration to INI file");
        } catch (IOException e) {
            logger.error("Failed to write config file: {}", CONFIG_FILE, e);
        }
    }

    /**
     * Gets the list of album folders from the INI file.
     *
     * @return list of album folder paths (empty list if file doesn't exist)
     */
    public List<Path> getBaseFolders() {
        List<Path> folders = new ArrayList<>();
        Map<String, Map<String, String>> config = readIniFile();

        Map<String, String> albumSection = config.get("AlbumFolders");
        if (albumSection == null) {
            albumSection = config.get("AnchoredFolders"); // backward compatibility
        }

        if (albumSection != null) {
            for (Map.Entry<String, String> entry : albumSection.entrySet()) {
                if (entry.getKey().startsWith("folder")) {
                    Path folderPath = Paths.get(entry.getValue());
                    if (Files.exists(folderPath)) {
                        folders.add(folderPath);
                    } else {
                        logger.warn("Album folder does not exist: {}", entry.getValue());
                    }
                }
            }
        }

        logger.debug("Loaded {} album folders from config file", folders.size());
        return folders;
    }

    /**
     * Sets the list of album folders to the INI file.
     *
     * @param folders list of album folder paths
     */
    public void setBaseFolders(List<Path> folders) {
        Map<String, Map<String, String>> config = readIniFile();

        // Update AlbumFolders section
        Map<String, String> albumSection = new LinkedHashMap<>();
        if (folders != null) {
            for (int i = 0; i < folders.size(); i++) {
                albumSection.put("folder" + i, folders.get(i).toString());
            }
        }
        config.put("AlbumFolders", albumSection);

        writeIniFile(config);
        logger.debug("Saved {} album folders to config file", folders != null ? folders.size() : 0);
    }

    /**
     * Gets the last scan date.
     *
     * @return last scan date, or null if never scanned
     */
    public Instant getLastScanDate() {
        long epochMilli = prefs.getLong(LAST_SCAN_DATE_KEY, -1);
        if (epochMilli == -1) {
            return null;
        }
        return Instant.ofEpochMilli(epochMilli);
    }

    /**
     * Sets the last scan date.
     *
     * @param instant the scan date
     */
    public void setLastScanDate(Instant instant) {
        if (instant == null) {
            prefs.remove(LAST_SCAN_DATE_KEY);
        } else {
            prefs.putLong(LAST_SCAN_DATE_KEY, instant.toEpochMilli());
        }
        flush();
    }

    /**
     * Gets the window width.
     *
     * @return window width
     */
    public double getWindowWidth() {
        return prefs.getDouble(WINDOW_WIDTH_KEY, DEFAULT_WIDTH);
    }

    /**
     * Sets the window width.
     *
     * @param width window width
     */
    public void setWindowWidth(double width) {
        prefs.putDouble(WINDOW_WIDTH_KEY, width);
        flush();
    }

    /**
     * Gets the window height.
     *
     * @return window height
     */
    public double getWindowHeight() {
        return prefs.getDouble(WINDOW_HEIGHT_KEY, DEFAULT_HEIGHT);
    }

    /**
     * Sets the window height.
     *
     * @param height window height
     */
    public void setWindowHeight(double height) {
        prefs.putDouble(WINDOW_HEIGHT_KEY, height);
        flush();
    }

    /**
     * Gets the window X position.
     *
     * @return window X position, or -1 if not set
     */
    public double getWindowX() {
        return prefs.getDouble(WINDOW_X_KEY, -1);
    }

    /**
     * Sets the window X position.
     *
     * @param x window X position
     */
    public void setWindowX(double x) {
        prefs.putDouble(WINDOW_X_KEY, x);
        flush();
    }

    /**
     * Gets the window Y position.
     *
     * @return window Y position, or -1 if not set
     */
    public double getWindowY() {
        return prefs.getDouble(WINDOW_Y_KEY, -1);
    }

    /**
     * Sets the window Y position.
     *
     * @param y window Y position
     */
    public void setWindowY(double y) {
        prefs.putDouble(WINDOW_Y_KEY, y);
        flush();
    }

    /**
     * Gets the organize settings from INI file.
     *
     * @return organize settings with defaults if not set
     */
    public AlbumOrganizerSettings getOrganizeSettings() {
        AlbumOrganizerSettings settings = new AlbumOrganizerSettings();
        Map<String, Map<String, String>> config = readIniFile();
        Map<String, String> organizeSection = config.get("Organize");
        Map<String, String> settingsSection = config.get("Settings");

        if (organizeSection != null) {
            try {
                if (organizeSection.containsKey("mode")) {
                    settings.setMode(AlbumOrganizerSettings.OrganizeMode.valueOf(organizeSection.get("mode")));
                }
                if (organizeSection.containsKey("createYearFolder")) {
                    settings.setCreateYearFolder(Boolean.parseBoolean(organizeSection.get("createYearFolder")));
                }
                if (organizeSection.containsKey("createMonthFolder")) {
                    settings.setCreateMonthFolder(Boolean.parseBoolean(organizeSection.get("createMonthFolder")));
                }
                if (organizeSection.containsKey("createDayFolder")) {
                    settings.setCreateDayFolder(Boolean.parseBoolean(organizeSection.get("createDayFolder")));
                }
                if (organizeSection.containsKey("splitLowRes")) {
                    settings.setSplitLowRes(Boolean.parseBoolean(organizeSection.get("splitLowRes")));
                }
                if (organizeSection.containsKey("splitMedRes")) {
                    settings.setSplitMedRes(Boolean.parseBoolean(organizeSection.get("splitMedRes")));
                }
                if (organizeSection.containsKey("lowResThresholdPixels")) {
                    settings.setLowResThresholdPixels(Integer.parseInt(organizeSection.get("lowResThresholdPixels")));
                }
                if (organizeSection.containsKey("hiResThresholdPixels")) {
                    settings.setHiResThresholdPixels(Integer.parseInt(organizeSection.get("hiResThresholdPixels")));
                }
            } catch (Exception e) {
                logger.warn("Failed to parse organize settings, using defaults", e);
            }
        }

        // Load targetFolder and fontSizeFactor from Settings section
        if (settingsSection != null) {
            try {
                if (settingsSection.containsKey("targetFolder")) {
                    settings.setTargetFolder(Paths.get(settingsSection.get("targetFolder")));
                }
                if (settingsSection.containsKey("fontSizeFactor")) {
                    settings.setFontSizeFactor(Integer.parseInt(settingsSection.get("fontSizeFactor")));
                }
            } catch (Exception e) {
                logger.warn("Failed to parse target folder or font size factor, using defaults", e);
            }
        }

        return settings;
    }

    /**
     * Sets the organize settings in INI file.
     *
     * @param settings organize settings to save
     */
    public void setOrganizeSettings(AlbumOrganizerSettings settings) {
        Map<String, Map<String, String>> config = readIniFile();
        Map<String, String> organizeSection = new LinkedHashMap<>();

        organizeSection.put("mode", settings.getMode().name());
        organizeSection.put("createYearFolder", String.valueOf(settings.isCreateYearFolder()));
        organizeSection.put("createMonthFolder", String.valueOf(settings.isCreateMonthFolder()));
        organizeSection.put("createDayFolder", String.valueOf(settings.isCreateDayFolder()));
        organizeSection.put("splitLowRes", String.valueOf(settings.isSplitLowRes()));
        organizeSection.put("splitMedRes", String.valueOf(settings.isSplitMedRes()));
        organizeSection.put("lowResThresholdPixels", String.valueOf(settings.getLowResThresholdPixels()));
        organizeSection.put("hiResThresholdPixels", String.valueOf(settings.getHiResThresholdPixels()));

        config.put("Organize", organizeSection);

        // Save targetFolder and fontSizeFactor to Settings section
        Map<String, String> settingsSection = config.computeIfAbsent("Settings", k -> new LinkedHashMap<>());
        if (settings.getTargetFolder() != null) {
            settingsSection.put("targetFolder", settings.getTargetFolder().toString());
        } else {
            settingsSection.remove("targetFolder");
        }
        settingsSection.put("fontSizeFactor", String.valueOf(settings.getFontSizeFactor()));

        writeIniFile(config);
        logger.debug("Saved organize settings to config file");
    }

    /**
     * Clears all stored preferences.
     */
    public void clearAll() {
        try {
            prefs.clear();
            flush();
            logger.info("Cleared all preferences");
        } catch (BackingStoreException e) {
            logger.error("Failed to clear preferences", e);
        }
    }

    /**
     * Flushes preferences to persistent storage.
     */
    private void flush() {
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            logger.error("Failed to flush preferences", e);
        }
    }
}
