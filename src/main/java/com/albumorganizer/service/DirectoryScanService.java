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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class DirectoryScanService {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryScanService.class);

    private final MetadataService metadataService;

    public DirectoryScanService() {
        this.metadataService = new MetadataService();
    }

    public DirectoryScanService(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    public List<MediaFile> scanDirectory(Path directory) {
        return doScanDirectory(directory, null);
    }

    public List<MediaFile> scanDirectory(Path directory, BiConsumer<Integer, Integer> progressCallback) {
        return doScanDirectory(directory, progressCallback);
    }

    private List<MediaFile> doScanDirectory(Path directory, BiConsumer<Integer, Integer> progressCallback) {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) {
            logger.warn("Invalid directory: {}", directory);
            return new ArrayList<>();
        }

        logger.debug("Scanning directory: {}", directory);
        List<MediaFile> mediaFiles = new ArrayList<>();

        try {
            List<Path> filesInDir = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry)) filesInDir.add(entry);
                }
            }

            for (int i = 0; i < filesInDir.size(); i++) {
                Path file = filesInDir.get(i);
                String filename = file.getFileName().toString();

                if (progressCallback != null) progressCallback.accept(i + 1, filesInDir.size());

                MediaType type = FileTypeDetector.getMediaType(file);
                if (type == null) continue;

                try {
                    long sizeBytes = Files.size(file);
                    MediaFile mediaFile = new MediaFile(filename, file, type, sizeBytes);
                    mediaFile.setLocation(directory.toString());

                    try {
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
                                Instant estimated = com.albumorganizer.util.DateEstimator.estimateFromFilename(filename);
                                if (estimated != null) {
                                    mediaFile.setDateTaken(estimated);
                                    mediaFile.setDateEstimated(true);
                                }
                            }

                            if (type == MediaType.VIDEO) {
                                Long duration = metadataService.getVideoDuration(file);
                                mediaFile.setDurationSeconds(duration);
                                int rotation = metadataService.getVideoRotation(file);
                                if (rotation != 0) mediaFile.setOrientation(rotation);
                            } else {
                                mediaFile.setOrientation(metadataService.getOrientation(file));
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

    public int getMediaFileCount(Path directory) {
        if (directory == null || !Files.exists(directory) || !Files.isDirectory(directory)) return 0;
        try {
            int count = 0;
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path entry : stream) {
                    if (Files.isRegularFile(entry) && FileTypeDetector.getMediaType(entry) != null) count++;
                }
            }
            return count;
        } catch (IOException e) {
            logger.warn("Error counting files in: {}", directory, e);
            return 0;
        }
    }
}
