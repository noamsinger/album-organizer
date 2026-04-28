package com.albumorganizer.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single file in the index.
 * The directory Path is shared among all files in the same directory,
 * significantly reducing memory usage.
 */
public class FileIndexEntry {
    private final Path directory;  // Shared reference for all files in same directory
    private final String filename;
    private final Instant lastModified;

    public FileIndexEntry(Path directory, String filename, Instant lastModified) {
        this.directory = directory;
        this.filename = filename;
        this.lastModified = lastModified;
    }

    public Path getDirectory() {
        return directory;
    }

    public String getFilename() {
        return filename;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    /**
     * Gets the absolute path by combining directory and filename.
     * Computed on-demand to save memory.
     */
    public Path getAbsolutePath() {
        return directory.resolve(filename);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileIndexEntry that = (FileIndexEntry) o;
        return Objects.equals(directory, that.directory) &&
               Objects.equals(filename, that.filename) &&
               Objects.equals(lastModified, that.lastModified);
    }

    @Override
    public int hashCode() {
        return Objects.hash(directory, filename, lastModified);
    }

    @Override
    public String toString() {
        return "FileIndexEntry{" +
                "directory=" + directory +
                ", filename='" + filename + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }
}
