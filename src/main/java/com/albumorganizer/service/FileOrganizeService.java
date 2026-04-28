package com.albumorganizer.service;

import com.albumorganizer.model.AlbumOrganizerSettings;
import com.albumorganizer.model.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Service for organizing media files into target folder structure.
 */
public class FileOrganizeService {

    private static final Logger logger = LoggerFactory.getLogger(FileOrganizeService.class);

    private final HashService hashService;

    public FileOrganizeService() {
        this.hashService = new HashService();
    }

    /**
     * Organizes a single media file according to settings.
     *
     * @param mediaFile the file to organize
     * @param settings the organization settings
     * @return result of the organization operation
     */
    public OrganizeResult organizeFile(MediaFile mediaFile, AlbumOrganizerSettings settings) {
        logger.info("Organizing file: {}", mediaFile.getFilename());

        try {
            // Build target path
            Path targetPath = buildTargetPath(mediaFile, settings);

            // Create parent directories if needed
            Files.createDirectories(targetPath.getParent());

            // Calculate source file hash (if not already available)
            String sourceHash = mediaFile.getSha1Hash();
            if (sourceHash == null) {
                sourceHash = hashService.calculateHash(mediaFile.getAbsolutePath());
            }

            // Check if a file with the same hash already exists in the target directory
            Path duplicateFile = findFileWithHash(targetPath.getParent(), sourceHash);
            if (duplicateFile != null) {
                logger.info("Duplicate file with same hash already exists: {}", duplicateFile);
                return OrganizeResult.skipped("Duplicate already exists at: " + duplicateFile.getFileName());
            }

            // Check for filename collision
            if (Files.exists(targetPath)) {
                return handleCollision(mediaFile, targetPath, settings, sourceHash);
            }

            // Perform copy or move
            boolean isCopyMode = (settings.getMode() == AlbumOrganizerSettings.OrganizeMode.COPY);
            if (isCopyMode) {
                Files.copy(mediaFile.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied file to: {}", targetPath);
                return OrganizeResult.success(targetPath);
            } else {
                return moveFileWithVerification(mediaFile.getAbsolutePath(), targetPath);
            }

        } catch (Exception e) {
            logger.error("Failed to organize file: {}", mediaFile.getFilename(), e);
            return OrganizeResult.error(e.getMessage());
        }
    }

    /**
     * Searches for a file with the given hash in the target directory.
     * Returns the path if found, null otherwise.
     */
    private Path findFileWithHash(Path directory, String targetHash) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return null;
        }

        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            return files
                .filter(Files::isRegularFile)
                .filter(file -> {
                    try {
                        String fileHash = hashService.calculateHash(file);
                        return targetHash.equals(fileHash);
                    } catch (IOException e) {
                        logger.debug("Failed to calculate hash for: {}", file, e);
                        return false;
                    }
                })
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Builds the target path for a media file based on settings.
     */
    private Path buildTargetPath(MediaFile mediaFile, AlbumOrganizerSettings settings) {
        Path path = settings.getTargetFolder();

        // Add temporal hierarchy
        Instant dateTaken = mediaFile.getDateTaken();
        if (dateTaken != null) {
            DateTimeFormatter yearFormatter = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
            DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);
            DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd").withZone(ZoneOffset.UTC);

            if (settings.isCreateYearFolder()) {
                path = path.resolve(yearFormatter.format(dateTaken));
            }
            if (settings.isCreateMonthFolder()) {
                path = path.resolve(monthFormatter.format(dateTaken));
            }
            if (settings.isCreateDayFolder()) {
                path = path.resolve(dayFormatter.format(dateTaken));
            }
        } else {
            // No date available
            path = path.resolve("unknown-date");
        }

        // Add quality classification
        if (settings.isSplitLowRes() || settings.isSplitMedRes()) {
            Dimension resolution = mediaFile.getResolution();
            if (resolution == null) {
                path = path.resolve("unknown-resolution");
            } else {
                int pixels = resolution.width * resolution.height;
                if (pixels < settings.getLowResThresholdPixels()) {
                    path = path.resolve("low-res");
                } else if (pixels < settings.getHiResThresholdPixels()) {
                    path = path.resolve("med-res");
                }
                // Hi-res: no subfolder
            }
        }

        // Add filename
        path = path.resolve(mediaFile.getFilename());

        return path;
    }

    /**
     * Handles collision when target file already exists.
     */
    private OrganizeResult handleCollision(MediaFile mediaFile, Path targetPath, AlbumOrganizerSettings settings, String sourceHash) {
        try {
            // Calculate hash of existing target file
            String targetHash = hashService.calculateHash(targetPath);

            if (sourceHash.equals(targetHash)) {
                // Same content - skip
                logger.info("File already exists with same content, skipping: {}", targetPath);
                return OrganizeResult.skipped("File already exists with identical content");
            }

            // Different content - append full hash to filename
            Path newTargetPath = appendHashToFilename(targetPath, sourceHash);

            // Create parent directories if needed
            Files.createDirectories(newTargetPath.getParent());

            // Perform copy or move
            boolean isCopyMode = (settings.getMode() == AlbumOrganizerSettings.OrganizeMode.COPY);
            if (isCopyMode) {
                Files.copy(mediaFile.getAbsolutePath(), newTargetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied file with hash suffix to: {}", newTargetPath);
                return OrganizeResult.success(newTargetPath);
            } else {
                return moveFileWithVerification(mediaFile.getAbsolutePath(), newTargetPath);
            }

        } catch (Exception e) {
            logger.error("Failed to handle collision for: {}", mediaFile.getFilename(), e);
            return OrganizeResult.error("Collision handling failed: " + e.getMessage());
        }
    }

    /**
     * Appends full hash to filename before extension.
     */
    private Path appendHashToFilename(Path originalPath, String hash) {
        String filename = originalPath.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');

        String nameWithHash;
        if (lastDot > 0) {
            String basename = filename.substring(0, lastDot);
            String extension = filename.substring(lastDot);
            nameWithHash = basename + "_" + hash + extension;
        } else {
            nameWithHash = filename + "_" + hash;
        }

        return originalPath.getParent().resolve(nameWithHash);
    }

    /**
     * Moves file with integrity verification.
     * Copies first, verifies hash, then deletes original only if verification succeeds.
     */
    private OrganizeResult moveFileWithVerification(Path source, Path target) {
        try {
            // Step 1: Copy to target
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

            // Step 2: Calculate hashes
            String sourceHash = hashService.calculateHash(source);
            String targetHash = hashService.calculateHash(target);

            // Step 3: Verify
            if (!sourceHash.equals(targetHash)) {
                // Verification failed - delete target copy, keep original
                Files.deleteIfExists(target);
                logger.error("Move verification failed for: {}. Hash mismatch.", source);
                return OrganizeResult.error("Move verification failed: hash mismatch");
            }

            // Step 4: Delete original only if verification succeeded
            Files.delete(source);
            logger.info("Moved file with verification to: {}", target);
            return OrganizeResult.success(target);

        } catch (Exception e) {
            logger.error("Failed to move file with verification: {}", source, e);
            return OrganizeResult.error("Move failed: " + e.getMessage());
        }
    }

    /**
     * Result of an organize operation.
     */
    public static class OrganizeResult {
        private final boolean success;
        private final boolean skipped;
        private final Path targetPath;
        private final String errorMessage;

        private OrganizeResult(boolean success, boolean skipped, Path targetPath, String errorMessage) {
            this.success = success;
            this.skipped = skipped;
            this.targetPath = targetPath;
            this.errorMessage = errorMessage;
        }

        public static OrganizeResult success(Path targetPath) {
            return new OrganizeResult(true, false, targetPath, null);
        }

        public static OrganizeResult skipped(String reason) {
            return new OrganizeResult(false, true, null, reason);
        }

        public static OrganizeResult error(String errorMessage) {
            return new OrganizeResult(false, false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public Path getTargetPath() {
            return targetPath;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
