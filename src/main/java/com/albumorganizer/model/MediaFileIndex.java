package com.albumorganizer.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Lightweight index entry for media files.
 * Stores only essential data: filename, hash, and path.
 * Used for duplicate detection across all scanned files.
 */
public class MediaFileIndex {
    private final String filename;
    private final String sha1Hash;
    private final Path absolutePath;

    public MediaFileIndex(String filename, String sha1Hash, Path absolutePath) {
        this.filename = filename;
        this.sha1Hash = sha1Hash;
        this.absolutePath = absolutePath;
    }

    public String getFilename() {
        return filename;
    }

    public String getSha1Hash() {
        return sha1Hash;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaFileIndex that = (MediaFileIndex) o;
        return Objects.equals(absolutePath, that.absolutePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absolutePath);
    }

    @Override
    public String toString() {
        return "MediaFileIndex{" +
                "filename='" + filename + '\'' +
                ", sha1Hash='" + sha1Hash + '\'' +
                ", path=" + absolutePath +
                '}';
    }
}
