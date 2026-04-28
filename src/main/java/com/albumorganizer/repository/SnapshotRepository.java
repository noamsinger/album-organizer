package com.albumorganizer.repository;

import com.albumorganizer.model.FileIndexEntry;
import com.albumorganizer.model.MediaFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Repository for saving and loading compressed snapshot of the file index.
 */
public class SnapshotRepository {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotRepository.class);

    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".album-organizer";
    private static final String CACHE_FILE = CONFIG_DIR + File.separator + "cache.json.gz";

    private final Gson gson;

    public SnapshotRepository() {
        this.gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .create();
        ensureConfigDirectoryExists();
    }

    private void ensureConfigDirectoryExists() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                logger.info("Created config directory: {}", configDir);
            }
        } catch (IOException e) {
            logger.error("Failed to create config directory", e);
        }
    }

    /**
     * Saves the file index snapshot to compressed JSON file.
     *
     * @param fileIndex map of hash to list of file index entries
     */
    public void saveSnapshot(Map<String, List<FileIndexEntry>> fileIndex) {
        try {
            Snapshot snapshot = new Snapshot();
            snapshot.timestamp = Instant.now();
            snapshot.fileIndex = fileIndex;

            Path cacheFile = Paths.get(CACHE_FILE);

            try (FileOutputStream fos = new FileOutputStream(cacheFile.toFile());
                 GZIPOutputStream gzos = new GZIPOutputStream(fos);
                 OutputStreamWriter writer = new OutputStreamWriter(gzos)) {

                gson.toJson(snapshot, writer);
                writer.flush();
                gzos.finish(); // Ensure GZIP stream is completed
            }

            logger.info("Saved snapshot with {} hash entries to {}", fileIndex.size(), CACHE_FILE);
        } catch (IOException e) {
            logger.error("Failed to save snapshot", e);
        }
    }

    /**
     * Loads the file index snapshot from compressed JSON file.
     *
     * @return map of hash to list of file index entries, or empty map if no cache exists
     */
    public Map<String, List<FileIndexEntry>> loadSnapshot() {
        Path cacheFile = Paths.get(CACHE_FILE);

        if (!Files.exists(cacheFile)) {
            logger.info("No snapshot file found at {}", CACHE_FILE);
            return new HashMap<>();
        }

        try (FileInputStream fis = new FileInputStream(cacheFile.toFile());
             GZIPInputStream gzis = new GZIPInputStream(fis);
             InputStreamReader reader = new InputStreamReader(gzis)) {

            Snapshot snapshot = gson.fromJson(reader, Snapshot.class);

            if (snapshot == null || snapshot.fileIndex == null) {
                logger.warn("Invalid snapshot format");
                return new HashMap<>();
            }

            logger.info("Loaded snapshot from {} with {} hash entries (timestamp: {})",
                CACHE_FILE, snapshot.fileIndex.size(), snapshot.timestamp);

            return snapshot.fileIndex;
        } catch (Exception e) {
            logger.error("Failed to load snapshot (file may be corrupted), will start fresh", e);
            // Try to delete corrupted cache file
            try {
                Files.deleteIfExists(cacheFile);
                logger.info("Deleted corrupted cache file");
            } catch (IOException deleteEx) {
                logger.warn("Could not delete corrupted cache file", deleteEx);
            }
            return new HashMap<>();
        }
    }

    /**
     * Inner class representing the snapshot structure.
     */
    private static class Snapshot {
        Instant timestamp;
        Map<String, List<FileIndexEntry>> fileIndex;
    }

    /**
     * Custom TypeAdapter for java.time.Instant to handle serialization.
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            String value = in.nextString();
            return value == null ? null : Instant.parse(value);
        }
    }

    /**
     * Custom TypeAdapter for java.nio.file.Path to handle serialization.
     */
    private static class PathTypeAdapter extends TypeAdapter<Path> {
        @Override
        public void write(JsonWriter out, Path value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Path read(JsonReader in) throws IOException {
            String value = in.nextString();
            return value == null ? null : Paths.get(value);
        }
    }
}
