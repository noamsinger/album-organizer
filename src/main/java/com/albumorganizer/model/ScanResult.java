package com.albumorganizer.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result object from scan operations containing lists of changes.
 */
public class ScanResult {
    private List<MediaFile> newFiles;
    private List<MediaFile> modifiedFiles;
    private List<MediaFile> unchangedFiles;
    private List<String> deletedFiles;
    private int totalScanned;
    private Duration scanDuration;
    private Map<Path, Exception> errors;

    public ScanResult() {
        this.newFiles = new ArrayList<>();
        this.modifiedFiles = new ArrayList<>();
        this.unchangedFiles = new ArrayList<>();
        this.deletedFiles = new ArrayList<>();
        this.errors = new HashMap<>();
        this.totalScanned = 0;
    }

    public List<MediaFile> getNewFiles() {
        return newFiles;
    }

    public void setNewFiles(List<MediaFile> newFiles) {
        this.newFiles = newFiles;
    }

    public List<MediaFile> getModifiedFiles() {
        return modifiedFiles;
    }

    public void setModifiedFiles(List<MediaFile> modifiedFiles) {
        this.modifiedFiles = modifiedFiles;
    }

    public List<MediaFile> getUnchangedFiles() {
        return unchangedFiles;
    }

    public void setUnchangedFiles(List<MediaFile> unchangedFiles) {
        this.unchangedFiles = unchangedFiles;
    }

    public List<String> getDeletedFiles() {
        return deletedFiles;
    }

    public void setDeletedFiles(List<String> deletedFiles) {
        this.deletedFiles = deletedFiles;
    }

    public int getTotalScanned() {
        return totalScanned;
    }

    public void setTotalScanned(int totalScanned) {
        this.totalScanned = totalScanned;
    }

    public Duration getScanDuration() {
        return scanDuration;
    }

    public void setScanDuration(Duration scanDuration) {
        this.scanDuration = scanDuration;
    }

    public Map<Path, Exception> getErrors() {
        return errors;
    }

    public void setErrors(Map<Path, Exception> errors) {
        this.errors = errors;
    }

    public void addError(Path path, Exception exception) {
        this.errors.put(path, exception);
    }

    public List<MediaFile> getAllFiles() {
        List<MediaFile> allFiles = new ArrayList<>();
        allFiles.addAll(newFiles);
        allFiles.addAll(modifiedFiles);
        allFiles.addAll(unchangedFiles);
        return allFiles;
    }

    @Override
    public String toString() {
        return "ScanResult{" +
                "newFiles=" + newFiles.size() +
                ", modifiedFiles=" + modifiedFiles.size() +
                ", unchangedFiles=" + unchangedFiles.size() +
                ", deletedFiles=" + deletedFiles.size() +
                ", totalScanned=" + totalScanned +
                ", errors=" + errors.size() +
                '}';
    }
}
