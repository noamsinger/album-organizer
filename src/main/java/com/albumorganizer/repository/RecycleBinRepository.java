package com.albumorganizer.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Persists the mapping of files in the recycle-bin folder to their original paths.
 * Backed by ~/.album-organizer/recycle-bin-index.json.
 */
public class RecycleBinRepository {

    private static final Logger logger = LoggerFactory.getLogger(RecycleBinRepository.class);
    private static final Path INDEX_FILE = Paths.get(
        System.getProperty("user.home"), ".album-organizer", "recycle-bin-index.json");

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
            parseJson(json);
        } catch (IOException e) {
            logger.error("Failed to load recycle-bin index", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(INDEX_FILE.getParent());
            StringBuilder sb = new StringBuilder("{\n");
            Iterator<Map.Entry<String, String>> it = index.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> e = it.next();
                sb.append("  ").append(jsonString(e.getKey()))
                  .append(": ").append(jsonString(e.getValue()));
                if (it.hasNext()) sb.append(",");
                sb.append("\n");
            }
            sb.append("}");
            Files.writeString(INDEX_FILE, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to save recycle-bin index", e);
        }
    }

    private void parseJson(String json) {
        index.clear();
        // Simple hand-rolled parser: expects flat {"key": "value", ...}
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
        // Split on "," that are not inside strings — use line-based approach (one entry per line)
        for (String line : json.split("\n")) {
            line = line.trim();
            if (line.endsWith(",")) line = line.substring(0, line.length() - 1).trim();
            int colon = line.indexOf("\":");
            if (colon < 0) continue;
            String key = unquote(line.substring(0, colon + 1).trim());
            String val = unquote(line.substring(colon + 2).trim());
            if (key != null && val != null) index.put(key, val);
        }
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String unquote(String s) {
        if (s == null || s.length() < 2) return null;
        if (s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return null;
    }
}
