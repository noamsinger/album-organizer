package com.albumorganizer.model;

import java.awt.Dimension;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a scanned image or video file with metadata.
 */
public class MediaFile {
    private String filename;
    private Path absolutePath;
    private Path relativePath;
    private Instant lastModified;
    private MediaType type;
    private long sizeBytes;
    private Dimension resolution;
    private String sha1Hash;
    private String location;
    private Map<String, Object> metadata;
    private Long durationSeconds;  // Video duration in seconds
    private Instant dateTaken;     // Original date/time photo was taken (from EXIF)
    private boolean dateEstimated; // True if dateTaken was estimated from filename
    private boolean corrupted;     // True if file has invalid/corrupted content

    public MediaFile() {
        this.metadata = new HashMap<>();
    }

    public MediaFile(String filename, Path absolutePath, Instant lastModified, MediaType type, long sizeBytes) {
        this.filename = filename;
        this.absolutePath = absolutePath;
        this.lastModified = lastModified;
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.metadata = new HashMap<>();
    }

    // Getters and Setters
    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(Path absolutePath) {
        this.absolutePath = absolutePath;
    }

    public Path getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(Path relativePath) {
        this.relativePath = relativePath;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public MediaType getType() {
        return type;
    }

    public void setType(MediaType type) {
        this.type = type;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public Dimension getResolution() {
        return resolution;
    }

    public void setResolution(Dimension resolution) {
        this.resolution = resolution;
    }

    public String getSha1Hash() {
        return sha1Hash;
    }

    public void setSha1Hash(String sha1Hash) {
        this.sha1Hash = sha1Hash;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getDateTaken() {
        return dateTaken;
    }

    public void setDateTaken(Instant dateTaken) {
        this.dateTaken = dateTaken;
    }

    public boolean isDateEstimated() {
        return dateEstimated;
    }

    public void setDateEstimated(boolean dateEstimated) {
        this.dateEstimated = dateEstimated;
    }

    public boolean isCorrupted() {
        return corrupted;
    }

    public void setCorrupted(boolean corrupted) {
        this.corrupted = corrupted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaFile mediaFile = (MediaFile) o;
        return Objects.equals(absolutePath, mediaFile.absolutePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(absolutePath);
    }

    @Override
    public String toString() {
        return "MediaFile{" +
                "filename='" + filename + '\'' +
                ", type=" + type +
                ", sizeBytes=" + sizeBytes +
                ", sha1Hash='" + sha1Hash + '\'' +
                '}';
    }
}
