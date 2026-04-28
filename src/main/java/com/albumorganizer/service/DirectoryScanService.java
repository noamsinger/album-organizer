package com.albumorganizer.service;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.MediaType;
import com.albumorganizer.util.FileTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for scanning a single directory (non-recursive) on-demand.
 * Used when user selects a folder in the tree view.
 */
public class DirectoryScanService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryScanService.class);

    private final HashService hashService;
    private final MetadataService metadataService;

    public DirectoryScanService() {
        this.hashService = new HashService();
        this.metadataService = new MetadataService();
    }

    public DirectoryScanService(HashService hashService, MetadataService metadataService) {
        this.hashService = hashService;
        this.metadataService = metadataService;
    }

    /**
     * Scans a single directory (non-recursive) for media files.
     * Uses existing hashes from the fileIndex where available, only recalculating
     * when no hash is known for a file.
     *
     * @param directory the directory to scan
     * @param existingHashes map of absolute path -> SHA-1 hash from the fileIndex
     * @return list of media files found in this directory
     */
    public List<MediaFile> scanDirectory(Path directory, Map<Path, String> existingHashes) {
        return doScanDirectory(directory, existingHashes);
    }

    /**
     * Scans a single directory (non-recursive) for media files.
     * Performs full scan - no cache optimization currently.
     *
     * @param directory the directory to scan
     * @return list of media files found in this directory
     */
    public List<MediaFile> scanDirectory(Path directory) {
        return doScanDirectory(directory, null);
    }

    private List<MediaFile> doScanDirectory(Path directory, Map<Path, String> existingHashes) {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
            logger.warn("Invalid directory: {}", directory);
            return new ArrayList<>();
        }

        logger.debug("Scanning directory: {}", directory);
        List<MediaFile> mediaFiles = new ArrayList<>();

        try {
            // Get all files in this directory (non-recursive)
            List<Path> filesInDir = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) {
                        filesInDir.add(entry);
                    }
                }
            }

            // Process each file
            for (Path file : filesInDir) {
                String filename = file.getFileName().toString();

                // Check if it's a media file
                MediaType type = FileTypeDetector.getMediaType(file);
                if (type == null) {
                    continue; // Not a media file
                }

                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    Instant lastModified = attrs.lastModifiedTime().toInstant();
                    long sizeBytes = attrs.size();

                    // Use existing hash from fileIndex if available, otherwise calculate
                    String hash;
                    if (existingHashes != null && existingHashes.containsKey(file)) {
                        hash = existingHashes.get(file);
                        logger.trace("Reusing existing hash for: {}", filename);
                    } else {
                        hash = hashService.calculateHash(file);
                        logger.trace("Calculated hash for: {}", filename);
                    }

                    // Create MediaFile object with full metadata
                    MediaFile mediaFile = new MediaFile(filename, file, lastModified, type, sizeBytes);
                    mediaFile.setSha1Hash(hash);
                    mediaFile.setLocation(directory.toString());

                    // Extract metadata (resolution, date taken, duration)
                    try {
                        // First check if file is valid
                        boolean isValid = metadataService.isValidMediaFile(file);
                        mediaFile.setCorrupted(!isValid);

                        if (isValid) {
                            Dimension resolution = metadataService.getResolution(file);
                            mediaFile.setResolution(resolution);

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
                            }
                        } else {
                            logger.warn("File is corrupted or invalid: {}", filename);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to extract metadata for: {}", filename, e);
                        mediaFile.setCorrupted(true);
                    }

                    mediaFiles.add(mediaFile);

                } catch (IOException e) {
                    logger.warn("Error reading file attributes: {}", file, e);
                }
            }

            logger.debug("Found {} media files in: {}", mediaFiles.size(), directory);

        } catch (IOException e) {
            logger.error("Error scanning directory: {}", directory, e);
        }

        return mediaFiles;
    }

    /**
     * Gets media file count in a directory without full scan.
     * Does quick count of media files.
     *
     * @param directory the directory to check
     * @return count of media files
     */
    public int getMediaFileCount(Path directory) {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
            return 0;
        }

        try {
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry) && FileTypeDetector.getMediaType(entry) != null) {
                        count++;
                    }
                }
            }
            return count;

        } catch (IOException e) {
            logger.warn("Error counting files in: {}", directory, e);
            return 0;
        }
    }
}
