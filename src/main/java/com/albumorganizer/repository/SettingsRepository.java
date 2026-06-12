package com.albumorganizer.repository;

import com.albumorganizer.model.AppSettings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class SettingsRepository {

    private static final Logger logger = LoggerFactory.getLogger(SettingsRepository.class);

    private static final Path FILE = Paths.get(
        System.getProperty("user.home"), ".config", "album-organizer", "album-organizer-settings.json");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public SettingsRepository() {
        try {
            Files.createDirectories(FILE.getParent());
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
    }

    public AppSettings load() {
        if (!Files.exists(FILE)) return AppSettings.defaults();
        try {
            String json = Files.readString(FILE);
            AppSettings loaded = GSON.fromJson(json, AppSettings.class);
            return loaded != null ? loaded : AppSettings.defaults();
        } catch (Exception e) {
            logger.warn("Failed to read settings file, using defaults: {}", e.getMessage());
            return AppSettings.defaults();
        }
    }

    public void save(AppSettings settings) {
        try {
            String json = GSON.toJson(settings);
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.debug("Saved settings to {}", FILE);
        } catch (IOException e) {
            logger.error("Failed to save settings", e);
        }
    }
}
