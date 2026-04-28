package com.albumorganizer.service;

import com.albumorganizer.model.FileIndexEntry;
import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.MediaType;
import com.albumorganizer.model.ScanResult;
import com.albumorganizer.util.Constants;
import com.albumorganizer.util.FileTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Full scan with hash strategy - performs complete recursive scan of all files with hash calculation.
 * Only recalculates hashes when: no existing hash, file is new, or file modification date changed.
 */
public class FullScanWithHashStrategy {

    private static final Logger logger = LoggerFactory.getLogger(FullScanWithHashStrategy.class);
    private static final String NO_HASH_KEY = "__NO_HASH__"; // Must match MainController constant

    private final HashService hashService;
    private final MetadataService metadataService;
    private final ScannerService scannerService;
    private Consumer<MediaFile> fileDiscoveryCallback = null;
    private Map<Path, FileHashInfo> existingFileHashes = new HashMap<>();

    /**
     * Container for existing file hash and modification time.
     */
    private static class FileHashInfo {
        final String hash;
        final Instant lastModified;

        FileHashInfo(String hash, Instant lastModified) {
            this.hash = hash;
            this.lastModified = lastModified;
        }
    }

    public FullScanWithHashStrategy(HashService hashService, MetadataService metadataService, ScannerService scannerService) {
        this.hashService = hashService;
        this.metadataService = metadataService;
        this.scannerService = scannerService;
    }

    public void setFileDiscoveryCallback(Consumer<MediaFile> callback) {
        this.fileDiscoveryCallback = callback;
    }

    /**
     * Sets the existing file index for smart hash recalculation.
     * Only files without hash, new files, or modified files will have hashes recalculated.
     *
     * @param fileIndex map of hash to list of file index entries
     */
    public void setExistingFileIndex(Map<String, List<FileIndexEntry>> fileIndex) {
        existingFileHashes.clear();
        if (fileIndex == null) {
            return;
        }

        // Build a map of file path -> (hash, lastModified) for quick lookup
        fileIndex.forEach((hash, entries) -> {
            // Skip NO_HASH_KEY entries - they need to be hashed
            if (!NO_HASH_KEY.equals(hash)) {
                for (FileIndexEntry entry : entries) {
                    Path fullPath = entry.getAbsolutePath();
                    existingFileHashes.put(fullPath, new FileHashInfo(hash, entry.getLastModified()));
                }
            }
        });

        logger.info("Loaded {} existing file hashes for smart rescanning", existingFileHashes.size());
    }

    /**
     * Performs a full scan with hash of the specified base folders.
     *
     * @param baseFolders list of base folders to scan
     * @return scan result with all files found
     */
    public ScanResult scan(List<Path> baseFolders) {
        logger.info("Starting full scan with hash of {} base folders", baseFolders.size());
        Instant startTime = Instant.now();
        ScanResult result = new ScanResult();

        for (Path baseFolder : baseFolders) {
            // Check for cancellation
            if (scannerService.isCancelled()) {
                logger.info("Scan cancelled");
                break;
            }

            if (!Files.exists(baseFolder) || !Files.isDirectory(baseFolder)) {
                logger.warn("Base folder does not exist or is not a directory: {}", baseFolder);
                continue;
            }

            try {
                scanDirectory(baseFolder, result);
            } catch (Exception e) {
                logger.error("Error scanning base folder: {}", baseFolder, e);
                result.addError(baseFolder, e);
            }
        }

        result.setScanDuration(Duration.between(startTime, Instant.now()));
        logger.info("Full scan with hash completed: {} files found in {}",
                    result.getAllFiles().size(), result.getScanDuration());
        return result;
    }

    /**
     * Recursively scans a directory and its subdirectories.
     *
     * @param directory the directory to scan
     * @param result    the scan result to populate
     */
    private void scanDirectory(Path directory, ScanResult result) {
        logger.debug("Scanning directory: {}", directory);

        // List to collect media files in this directory
        List<MediaFile> mediaFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(FileTypeDetector::isMediaFile)
                 .forEach(file -> {
                     // Check for cancellation
                     if (scannerService.isCancelled()) {
                         logger.info("Scan cancelled during directory scan");
                         return;
                     }

                     try {
                         MediaFile mediaFile = scanFile(file);
                         if (mediaFile != null) {
                             mediaFiles.add(mediaFile);
                             result.getNewFiles().add(mediaFile);
                             result.setTotalScanned(result.getTotalScanned() + 1);
                             if (fileDiscoveryCallback != null) {
                                 fileDiscoveryCallback.accept(mediaFile);
                             }
                         }
                     } catch (Exception e) {
                         logger.warn("Error scanning file: {}", file, e);
                         result.addError(file, e);
                     }
                 });
        } catch (IOException e) {
            logger.error("Error walking directory: {}", directory, e);
            result.addError(directory, e);
            return;
        }
    }

    /**
     * Scans a single file and creates a MediaFile object.
     *
     * @param file the file to scan
     * @return MediaFile object, or null if scan failed
     */
    private MediaFile scanFile(Path file) throws IOException {
        logger.debug("Scanning file: {}", file);

        // Get basic file attributes
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        Instant lastModified = attrs.lastModifiedTime().toInstant();
        long size = attrs.size();

        // Determine media type
        MediaType type = FileTypeDetector.getMediaType(file);
        if (type == null) {
            return null;
        }

        // Create MediaFile
        MediaFile mediaFile = new MediaFile(
            file.getFileName().toString(),
            file,
            lastModified,
            type,
            size
        );

        mediaFile.setLocation(file.getParent().toString());

        // Smart hash calculation: only calculate if needed
        FileHashInfo existingInfo = existingFileHashes.get(file);
        boolean needsHash = true;
        String existingHash = null;

        if (existingInfo != null) {
            // File exists in index - check if modification time changed
            if (existingInfo.lastModified.equals(lastModified)) {
                // File unchanged - reuse existing hash
                needsHash = false;
                existingHash = existingInfo.hash;
                logger.debug("Reusing existing hash for unchanged file: {}", file.getFileName());
            } else {
                logger.debug("File modified, recalculating hash: {}", file.getFileName());
            }
        } else {
            logger.debug("New file or no existing hash, calculating: {}", file.getFileName());
        }

        // Calculate or reuse hash
        if (needsHash) {
            try {
                String hash = hashService.calculateHash(file);
                mediaFile.setSha1Hash(hash);
            } catch (IOException e) {
                logger.warn("Failed to calculate hash for {}: {}", file, e.getMessage());
                throw e;
            }
        } else {
            // Reuse existing hash
            mediaFile.setSha1Hash(existingHash);
        }

        // Extract metadata (resolution, etc.)
        try {
            // Validate if file is a real media file
            boolean isValid = metadataService.isValidMediaFile(file);
            mediaFile.setCorrupted(!isValid);

            if (isValid) {
                Map<String, Object> metadata = metadataService.extractMetadata(file);
                mediaFile.setMetadata(metadata);

                // Try to get resolution
                Dimension resolution = metadataService.getResolution(file);
                mediaFile.setResolution(resolution);

                // Try to get date taken for images
                Instant dateTaken = metadataService.getDateTaken(file);
                if (dateTaken != null) {
                    mediaFile.setDateTaken(dateTaken);
                    mediaFile.setDateEstimated(false);
                } else {
                    // Try to estimate date from filename
                    Instant estimatedDate = com.albumorganizer.util.DateEstimator.estimateFromFilename(file.getFileName().toString());
                    if (estimatedDate != null) {
                        mediaFile.setDateTaken(estimatedDate);
                        mediaFile.setDateEstimated(true);
                    }
                }

                // Try to get duration for videos
                if (type == MediaType.VIDEO) {
                    Long duration = metadataService.getVideoDuration(file);
                    mediaFile.setDurationSeconds(duration);
                    logger.info("Set duration for {}: {} seconds", file.getFileName(), duration);
                }
            } else {
                logger.warn("File appears to be corrupted or invalid: {}", file);
            }
        } catch (Exception e) {
            logger.debug("Failed to extract metadata for {}: {}", file, e.getMessage());
            mediaFile.setCorrupted(true);
        }

        return mediaFile;
    }
}
