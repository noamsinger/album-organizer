package com.albumorganizer.repository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists the mapping of files in the recycle-bin folder to their original paths.
 * Backed by ~/.config/album-organizer/recycle-bin-index.json.
 */
public class RecycleBinRepository {

    private static final Logger logger = LoggerFactory.getLogger(RecycleBinRepository.class);
    private static final Path INDEX_FILE = Paths.get(
        System.getProperty("user.home"), ".config", "album-organizer", "recycle-bin-index.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, String>>() {}.getType();

    // bin absolute path -> original absolute path
    private final Map<String, String> index = new LinkedHashMap<>();

    public RecycleBinRepository() {
        load();
    }

    public void add(Path binPath, Path originalPath) {
        index.put(binPath.toAbsolutePath().toString(), originalPath.toAbsolutePath().toString());
        save();
    }

    public Path getOriginalPath(Path binPath) {
        String v = index.get(binPath.toAbsolutePath().toString());
        return v != null ? Paths.get(v) : null;
    }

    public void remove(Path binPath) {
        index.remove(binPath.toAbsolutePath().toString());
        save();
    }

    public Map<String, String> getAll() {
        return Collections.unmodifiableMap(index);
    }

    public boolean isInBin(Path path) {
        return index.containsKey(path.toAbsolutePath().toString());
    }

    private void load() {
        if (!Files.exists(INDEX_FILE)) return;
        try {
            String json = Files.readString(INDEX_FILE, StandardCharsets.UTF_8);
            Map<String, String> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded != null) index.putAll(loaded);
        } catch (IOException e) {
            logger.error("Failed to load recycle-bin index", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(INDEX_FILE.getParent());
            Path tmp = INDEX_FILE.resolveSibling(INDEX_FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(index), StandardCharsets.UTF_8);
            Files.move(tmp, INDEX_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            logger.error("Failed to save recycle-bin index", e);
        }
    }
}
