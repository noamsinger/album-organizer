package com.albumorganizer.service;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Quick scan strategy - fast scan that skips hash calculation.
 * Only reads filename, modification time, and basic file attributes.
 * Files are indexed with empty/null hashes for speed.
 */
public class QuickScanStrategy {

    private static final Logger logger = LoggerFactory.getLogger(QuickScanStrategy.class);

    private final HashService hashService;
    private final MetadataService metadataService;
    private final ScannerService scannerService;
    private Consumer<MediaFile> fileDiscoveryCallback = null;

    public QuickScanStrategy(HashService hashService, MetadataService metadataService, ScannerService scannerService) {
        this.hashService = hashService;
        this.metadataService = metadataService;
        this.scannerService = scannerService;
    }

    public void setFileDiscoveryCallback(Consumer<MediaFile> callback) {
        this.fileDiscoveryCallback = callback;
    }

    /**
     * Performs a quick scan of the specified base folders.
     * Skips hash calculation for speed - hashes are left null/empty.
     * Only reads filename, modification time, size, and basic metadata.
     *
     * @param baseFolders list of base folders to scan
     * @return scan result with changed files
     */
    public ScanResult scan(List<Path> baseFolders) {
        logger.info("Starting quick scan of {} base folders", baseFolders.size());
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
                scanDirectoryTree(baseFolder, result);
            } catch (Exception e) {
                logger.error("Error scanning base folder: {}", baseFolder, e);
                result.addError(baseFolder, e);
            }
        }

        result.setScanDuration(Duration.between(startTime, Instant.now()));
        logger.info("Quick scan completed: {} files scanned in {}",
                    result.getTotalScanned(),
                    result.getScanDuration());
        return result;
    }

    /**
     * Recursively scans directory tree.
     */
    private void scanDirectoryTree(Path directory, ScanResult result) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                 .filter(FileTypeDetector::isMediaFile)
                 .forEach(file -> {
                     // Check for cancellation
                     if (scannerService.isCancelled()) {
                         logger.info("Scan cancelled");
                         return;
                     }

                     try {
                         MediaFile mediaFile = scanFile(file);
                         if (mediaFile != null) {
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
            logger.error("Error walking directory tree: {}", directory, e);
            result.addError(directory, e);
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

        // QUICK SCAN: Skip hash calculation for speed
        // Hash will be null - use deep scan if you need hashes
        mediaFile.setSha1Hash(null);
        logger.debug("Quick scan: skipping hash for {}", file.getFileName());

        // Extract metadata (resolution, etc.)
        try {
            // Validate if file is a real media file
            boolean isValid = metadataService.isValidMediaFile(file);
            mediaFile.setCorrupted(!isValid);

            if (isValid) {
                Dimension resolution = metadataService.getResolution(file);
                mediaFile.setResolution(resolution);

                // Extract date taken and video duration
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
