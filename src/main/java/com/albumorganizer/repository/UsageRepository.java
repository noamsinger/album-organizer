package com.albumorganizer.repository;

import com.albumorganizer.model.AppUsage;
import com.albumorganizer.service.enhancement.ImageEnhancementService;
import com.albumorganizer.service.enhancement.NamedPrompt;
import com.albumorganizer.util.AppDirs;
import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class UsageRepository {

    private static final Logger logger = LoggerFactory.getLogger(UsageRepository.class);

    private static final Path FILE = AppDirs.configDir().resolve("album-organizer-usage.json");

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(NamedPrompt.class, (JsonDeserializer<NamedPrompt>) (json, type, ctx) -> {
            JsonObject obj = json.getAsJsonObject();
            int id = obj.has("id") ? obj.get("id").getAsInt() : 0;
            String title = obj.has("title") ? obj.get("title").getAsString() : "";
            String prompt = obj.has("prompt") ? obj.get("prompt").getAsString() : "";
            if (title.isBlank() || prompt.isBlank()) return null;
            return new NamedPrompt(id, title, prompt);
        })
        .registerTypeAdapter(NamedPrompt.class, (JsonSerializer<NamedPrompt>) (src, type, ctx) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", src.id());
            obj.addProperty("title", src.title());
            obj.addProperty("prompt", src.prompt());
            return obj;
        })
        .create();

    public UsageRepository() {
        try {
            Files.createDirectories(FILE.getParent());
            migrateFromLegacy(FILE);
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
    }

    public AppUsage load() {
        if (!Files.exists(FILE)) return AppUsage.defaults();
        try {
            String json = Files.readString(FILE);
            AppUsage loaded = GSON.fromJson(json, AppUsage.class);
            if (loaded == null) return AppUsage.defaults();
            AppUsage migrated = migratePromptIds(loaded);
            return seedDefaultCheckedPrompts(migrated);
        } catch (Exception e) {
            logger.warn("Failed to read usage file, using defaults: {}", e.getMessage());
            return AppUsage.defaults();
        }
    }

    /**
     * Assigns IDs to any prompt that has id=0 (pre-ID migration).
     * Prompts matching a default by title get the default's negative ID.
     * All others get a random positive ID.
     */
    private static AppUsage migratePromptIds(AppUsage usage) {
        if (usage.savedPrompts() == null || usage.savedPrompts().isEmpty()) return usage;
        boolean needsMigration = usage.savedPrompts().stream().anyMatch(p -> p.id() == 0);
        if (!needsMigration) return usage;

        Map<String, Integer> titleToDefaultId = ImageEnhancementService.DEFAULT_PROMPTS.stream()
            .collect(Collectors.toMap(NamedPrompt::title, NamedPrompt::id));

        List<NamedPrompt> migrated = new ArrayList<>();
        for (NamedPrompt p : usage.savedPrompts()) {
            if (p.id() != 0) {
                migrated.add(p);
            } else if (titleToDefaultId.containsKey(p.title())) {
                migrated.add(new NamedPrompt(titleToDefaultId.get(p.title()), p.title(), p.prompt()));
            } else {
                migrated.add(new NamedPrompt(randomPositiveId(), p.title(), p.prompt()));
            }
        }
        return new AppUsage(
            usage.fontSizeFactor(), usage.lastSelectedFolder(),
            usage.thumbnailView(), usage.showArchivesInTree(),
            migrated, usage.checkedPromptTitles(),
            usage.comfyUiCheckpoint(), usage.comfyUiCheckpoints(),
            usage.windowWidth(), usage.windowHeight(),
            usage.windowX(), usage.windowY(),
            usage.lastScanDateEpochMilli(),
            usage.sortField(), usage.sortAscending()
        );
    }

    private static AppUsage seedDefaultCheckedPrompts(AppUsage usage) {
        if (usage.checkedPromptTitles() != null && !usage.checkedPromptTitles().isEmpty()) {
            return usage;
        }
        List<String> seeded = new ArrayList<>();
        seeded.add("Adult Content Filter");
        return new AppUsage(
            usage.fontSizeFactor(), usage.lastSelectedFolder(),
            usage.thumbnailView(), usage.showArchivesInTree(),
            usage.savedPrompts(), seeded,
            usage.comfyUiCheckpoint(), usage.comfyUiCheckpoints(),
            usage.windowWidth(), usage.windowHeight(),
            usage.windowX(), usage.windowY(),
            usage.lastScanDateEpochMilli(),
            usage.sortField(), usage.sortAscending()
        );
    }

    public void save(AppUsage usage) {
        try {
            String json = GSON.toJson(usage);
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            logger.debug("Saved usage to {}", FILE);
        } catch (IOException e) {
            logger.error("Failed to save usage", e);
        }
    }

    private static void migrateFromLegacy(Path newFile) {
        if (Files.exists(newFile)) return;
        Path oldFile = Paths.get(System.getProperty("user.home"),
            ".config", "album-organizer", "album-organizer-usage.json");
        if (!Files.exists(oldFile) || oldFile.equals(newFile)) return;
        try {
            Files.copy(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Migrated usage from {} to {}", oldFile, newFile);
        } catch (IOException e) {
            logger.warn("Could not migrate usage file: {}", e.getMessage());
        }
    }

    public static int randomPositiveId() {
        return ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
    }
}
