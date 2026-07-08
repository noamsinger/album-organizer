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

public class FileOrganizeService {

    private static final Logger logger = LoggerFactory.getLogger(FileOrganizeService.class);

    public OrganizeResult organizeFile(MediaFile mediaFile, AlbumOrganizerSettings settings) {
        logger.debug("Organizing file: {}", mediaFile.getFilename());

        try {
            Path targetPath = buildTargetPath(mediaFile, settings);
            Files.createDirectories(targetPath.getParent());

            // Filename collision → rename with counter
            if (Files.exists(targetPath)) {
                return handleCollision(mediaFile, targetPath, settings);
            }

            boolean isCopyMode = (settings.getMode() == AlbumOrganizerSettings.OrganizeMode.COPY);
            if (isCopyMode) {
                Files.copy(mediaFile.getAbsolutePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                logger.debug("Copied file to: {}", targetPath);
                return OrganizeResult.success(targetPath);
            } else {
                return moveFile(mediaFile.getAbsolutePath(), targetPath);
            }

        } catch (Exception e) {
            logger.error("Failed to organize file: {}", mediaFile.getFilename(), e);
            return OrganizeResult.error(e.getMessage());
        }
    }

    private Path buildTargetPath(MediaFile mediaFile, AlbumOrganizerSettings settings) {
        Path path = settings.getTargetFolder();

        Instant dateTaken = mediaFile.getDateTaken();
        if (dateTaken != null) {
            DateTimeFormatter yearFmt  = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
            DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);
            DateTimeFormatter dayFmt   = DateTimeFormatter.ofPattern("dd").withZone(ZoneOffset.UTC);

            if (settings.isCreateYearFolder())  path = path.resolve(yearFmt.format(dateTaken));
            if (settings.isCreateMonthFolder()) path = path.resolve(monthFmt.format(dateTaken));
            if (settings.isCreateDayFolder())   path = path.resolve(dayFmt.format(dateTaken));
        } else {
            path = path.resolve("unknown-date");
        }

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
            }
        }

        return path.resolve(mediaFile.getFilename());
    }

    /** Filename collision: append _2, _3, … until a free path is found. */
    private OrganizeResult handleCollision(MediaFile mediaFile, Path targetPath, AlbumOrganizerSettings settings) {
        try {
            String filename = targetPath.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            String base = dot > 0 ? filename.substring(0, dot) : filename;
            String ext  = dot > 0 ? filename.substring(dot)   : "";
            Path parent = targetPath.getParent();

            Path newTarget = targetPath;
            int counter = 2;
            while (Files.exists(newTarget)) {
                newTarget = parent.resolve(base + "_" + counter + ext);
                counter++;
            }

            Files.createDirectories(newTarget.getParent());

            boolean isCopyMode = (settings.getMode() == AlbumOrganizerSettings.OrganizeMode.COPY);
            if (isCopyMode) {
                Files.copy(mediaFile.getAbsolutePath(), newTarget, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Copied with counter suffix to: {}", newTarget);
                return OrganizeResult.success(newTarget);
            } else {
                return moveFile(mediaFile.getAbsolutePath(), newTarget);
            }
        } catch (Exception e) {
            logger.error("Failed to handle collision for: {}", mediaFile.getFilename(), e);
            return OrganizeResult.error("Collision handling failed: " + e.getMessage());
        }
    }

    /** Moves a file: copy then delete original. */
    private OrganizeResult moveFile(Path source, Path target) {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.delete(source);
            logger.debug("Moved file to: {}", target);
            return OrganizeResult.success(target);
        } catch (Exception e) {
            logger.error("Failed to move file: {}", source, e);
            // Try to clean up partial copy
            try { Files.deleteIfExists(target); } catch (IOException ignored) {}
            return OrganizeResult.error("Move failed: " + e.getMessage());
        }
    }

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

        public static OrganizeResult success(Path targetPath) { return new OrganizeResult(true, false, targetPath, null); }
        public static OrganizeResult skipped(String reason)   { return new OrganizeResult(false, true, null, reason); }
        public static OrganizeResult error(String msg)        { return new OrganizeResult(false, false, null, msg); }

        public boolean isSuccess()     { return success; }
        public boolean isSkipped()     { return skipped; }
        public Path getTargetPath()    { return targetPath; }
        public String getErrorMessage(){ return errorMessage; }
    }
}
