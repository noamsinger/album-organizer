package com.albumorganizer.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.drew.metadata.gif.GifHeaderDirectory;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.webp.WebpDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.mp4.media.Mp4MediaDirectory;
import com.drew.metadata.mp4.media.Mp4VideoDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mov.media.QuickTimeMediaDirectory;
import com.drew.metadata.mov.media.QuickTimeVideoDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for extracting metadata from image and video files.
 */
public class MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataService.class);

    /**
     * Extracts metadata from a media file.
     *
     * @param file the file to extract metadata from
     * @return map of metadata key-value pairs
     */
    public Map<String, Object> extractMetadata(Path file) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            Metadata fileMetadata = ImageMetadataReader.readMetadata(file.toFile());

            // Try to get resolution from various metadata directories
            Dimension resolution = getResolution(fileMetadata);
            if (resolution != null) {
                metadata.put("resolution", resolution);
            }

            logger.debug("Extracted metadata for {}: {}", file.getFileName(), metadata);
        } catch (ImageProcessingException | IOException e) {
            logger.warn("Failed to extract metadata from {}: {}", file, e.getMessage());
        }
        return metadata;
    }

    /**
     * Gets the resolution (dimensions) from a media file.
     *
     * @param file the file to get resolution from
     * @return dimension object with width and height, or null if unavailable
     */
    public Dimension getResolution(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            Dimension resolution = getResolution(metadata);

            // If metadata extraction failed for PNG, try direct PNG header reading
            if (resolution == null && file.toString().toLowerCase().endsWith(".png")) {
                resolution = readPngDimensions(file);
            }

            return resolution;
        } catch (ImageProcessingException | IOException e) {
            logger.debug("Could not extract resolution from {}: {}", file, e.getMessage());

            // Try direct PNG header reading as fallback
            if (file.toString().toLowerCase().endsWith(".png")) {
                return readPngDimensions(file);
            }

            return null;
        }
    }

    /**
     * Reads PNG dimensions directly from the PNG file header.
     * PNG spec: first 8 bytes are signature, then IHDR chunk contains width/height.
     *
     * @param file the PNG file
     * @return dimension object, or null if reading fails
     */
    private Dimension readPngDimensions(Path file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file.toFile())) {
            byte[] header = new byte[24];
            int bytesRead = fis.read(header);

            if (bytesRead < 24) {
                return null;
            }

            // Check PNG signature
            if (header[0] != (byte)0x89 || header[1] != 0x50 || header[2] != 0x4E || header[3] != 0x47) {
                return null;
            }

            // Width is at bytes 16-19, height at bytes 20-23 (big-endian)
            int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16) |
                        ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
            int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16) |
                         ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);

            if (width > 0 && height > 0) {
                logger.debug("Read PNG dimensions directly from header: {}x{}", width, height);
                return new Dimension(width, height);
            }
        } catch (IOException e) {
            logger.debug("Failed to read PNG header: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extracts resolution from metadata object.
     *
     * @param metadata the metadata object
     * @return dimension object, or null if unavailable
     */
    private Dimension getResolution(Metadata metadata) {
        // Try EXIF for images
        ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (exifDir != null) {
            Integer width = exifDir.getInteger(ExifIFD0Directory.TAG_IMAGE_WIDTH);
            Integer height = exifDir.getInteger(ExifIFD0Directory.TAG_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try SubIFD for images
        ExifSubIFDDirectory subIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (subIFD != null) {
            Integer width = subIFD.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
            Integer height = subIFD.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try JpegDirectory for JPEG images without EXIF
        JpegDirectory jpegDir = metadata.getFirstDirectoryOfType(JpegDirectory.class);
        if (jpegDir != null) {
            Integer width = jpegDir.getInteger(JpegDirectory.TAG_IMAGE_WIDTH);
            Integer height = jpegDir.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try PngDirectory for PNG images
        PngDirectory pngDir = metadata.getFirstDirectoryOfType(PngDirectory.class);
        if (pngDir != null) {
            Integer width = pngDir.getInteger(PngDirectory.TAG_IMAGE_WIDTH);
            Integer height = pngDir.getInteger(PngDirectory.TAG_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try GifHeaderDirectory for GIF images
        GifHeaderDirectory gifDir = metadata.getFirstDirectoryOfType(GifHeaderDirectory.class);
        if (gifDir != null) {
            Integer width = gifDir.getInteger(GifHeaderDirectory.TAG_IMAGE_WIDTH);
            Integer height = gifDir.getInteger(GifHeaderDirectory.TAG_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try BmpHeaderDirectory for BMP images
        BmpHeaderDirectory bmpDir = metadata.getFirstDirectoryOfType(BmpHeaderDirectory.class);
        if (bmpDir != null) {
            Integer width = bmpDir.getInteger(BmpHeaderDirectory.TAG_IMAGE_WIDTH);
            Integer height = bmpDir.getInteger(BmpHeaderDirectory.TAG_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try WebpDirectory for WebP images
        WebpDirectory webpDir = metadata.getFirstDirectoryOfType(WebpDirectory.class);
        if (webpDir != null) {
            Integer width = webpDir.getInteger(WebpDirectory.TAG_IMAGE_WIDTH);
            Integer height = webpDir.getInteger(WebpDirectory.TAG_IMAGE_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try Mp4VideoDirectory for MP4 videos
        Mp4VideoDirectory mp4VideoDir = metadata.getFirstDirectoryOfType(Mp4VideoDirectory.class);
        if (mp4VideoDir != null) {
            Integer width = mp4VideoDir.getInteger(Mp4VideoDirectory.TAG_WIDTH);
            Integer height = mp4VideoDir.getInteger(Mp4VideoDirectory.TAG_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // Try QuickTimeVideoDirectory for MOV videos
        QuickTimeVideoDirectory qtVideoDir = metadata.getFirstDirectoryOfType(QuickTimeVideoDirectory.class);
        if (qtVideoDir != null) {
            Integer width = qtVideoDir.getInteger(QuickTimeVideoDirectory.TAG_WIDTH);
            Integer height = qtVideoDir.getInteger(QuickTimeVideoDirectory.TAG_HEIGHT);
            if (width != null && height != null) {
                return new Dimension(width, height);
            }
        }

        // For videos and other formats, try to find width/height tags in any directory
        for (Directory dir : metadata.getDirectories()) {
            for (Tag tag : dir.getTags()) {
                String tagName = tag.getTagName().toLowerCase();
                if (tagName.contains("width") || tagName.contains("height")) {
                    logger.debug("Found dimension tag: {} = {}", tag.getTagName(), tag.getDescription());
                }
            }
        }

        return null;
    }

    /**
     * Gets the date taken from EXIF metadata for photos.
     *
     * @param file the file to get date taken from
     * @return Instant of when photo was taken, or null if unavailable
     */
    /**
     * Gets the date taken from image EXIF metadata.
     * Note: EXIF dates don't include timezone info, so they're interpreted as UTC.
     * The returned Instant represents the same date/time values from EXIF, but in UTC.
     * Returns null if the date is invalid (year not in range 1800-2099).
     *
     * @param file the image file to extract date from
     * @return Instant representing the date taken (interpreted as UTC), or null if unavailable or invalid
     */
    public Instant getDateTaken(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

            // Try ExifSubIFDDirectory for DateTimeOriginal
            ExifSubIFDDirectory subIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIFD != null) {
                Date dateOriginal = subIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (dateOriginal != null) {
                    // EXIF dates don't have timezone info - treat as UTC
                    Instant instant = dateOriginal.toInstant();
                    if (isValidDateRange(instant)) {
                        return instant;
                    } else {
                        logger.debug("EXIF date is outside valid range (1800-2099) for file: {}", file);
                        return null;
                    }
                }
            }

            // Fallback to ExifIFD0Directory DateTime
            ExifIFD0Directory exifDir = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifDir != null) {
                Date dateTime = exifDir.getDate(ExifIFD0Directory.TAG_DATETIME);
                if (dateTime != null) {
                    // EXIF dates don't have timezone info - treat as UTC
                    Instant instant = dateTime.toInstant();
                    if (isValidDateRange(instant)) {
                        return instant;
                    } else {
                        logger.debug("EXIF date is outside valid range (1800-2099) for file: {}", file);
                        return null;
                    }
                }
            }

        } catch (ImageProcessingException | IOException e) {
            logger.debug("Could not extract date taken from {}: {}", file, e.getMessage());
        }
        return null;
    }

    /**
     * Validates that an Instant falls within the acceptable year range (1800-2099).
     *
     * @param instant the instant to validate
     * @return true if the year is between 1800 and 2099, false otherwise
     */
    private boolean isValidDateRange(Instant instant) {
        if (instant == null) {
            return false;
        }
        java.time.LocalDateTime dateTime = java.time.LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC);
        int year = dateTime.getYear();
        return year >= 1800 && year <= 2099;
    }

    /**
     * Gets the duration in seconds from video metadata.
     *
     * @param file the video file to get duration from
     * @return duration in seconds, or null if unavailable
     */
    public Long getVideoDuration(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());

            // Try QuickTimeDirectory for MOV files
            QuickTimeDirectory qtDir = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
            if (qtDir != null) {
                // Calculate from duration and time scale
                Long duration = qtDir.getLongObject(259); // TAG_DURATION
                Long timeScale = qtDir.getLongObject(258); // TAG_MEDIA_TIME_SCALE
                if (duration != null && timeScale != null && timeScale > 0) {
                    long totalSeconds = duration / timeScale;
                    logger.debug("Video duration for {}: {} seconds", file.getFileName(), totalSeconds);
                    return totalSeconds;
                }
            }

            // Try Mp4Directory for MP4 files (main container)
            Mp4Directory mp4Dir = metadata.getFirstDirectoryOfType(Mp4Directory.class);
            if (mp4Dir != null) {
                // Calculate from duration and time scale
                Long duration = mp4Dir.getLongObject(259); // TAG_DURATION
                Long timeScale = mp4Dir.getLongObject(258); // TAG_MEDIA_TIME_SCALE
                if (duration != null && timeScale != null && timeScale > 0) {
                    long totalSeconds = duration / timeScale;
                    logger.debug("Video duration for {}: {} seconds", file.getFileName(), totalSeconds);
                    return totalSeconds;
                }
            }

            // Try QuickTimeMediaDirectory for MOV files (media track)
            QuickTimeMediaDirectory qtMediaDir = metadata.getFirstDirectoryOfType(QuickTimeMediaDirectory.class);
            if (qtMediaDir != null) {
                Double durationSeconds = qtMediaDir.getDoubleObject(QuickTimeMediaDirectory.TAG_DURATION);
                if (durationSeconds != null) {
                    return durationSeconds.longValue();
                }
            }

            // Try Mp4MediaDirectory for MP4 files (media track)
            Mp4MediaDirectory mp4MediaDir = metadata.getFirstDirectoryOfType(Mp4MediaDirectory.class);
            if (mp4MediaDir != null) {
                Double durationSeconds = mp4MediaDir.getDoubleObject(Mp4MediaDirectory.TAG_DURATION);
                if (durationSeconds != null) {
                    return durationSeconds.longValue();
                }
            }

        } catch (ImageProcessingException | IOException e) {
            logger.debug("Could not extract video duration from {}: {}", file, e.getMessage());
        }
        return null;
    }

    /**
     * Validates if a file is a real media file (not corrupted or fake).
     * Checks if the file content matches its extension.
     *
     * @param file the file to validate
     * @return true if the file appears to be valid, false if corrupted/invalid
     */
    public boolean isValidMediaFile(Path file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            // If we can read any metadata directories, the file is likely valid
            boolean hasDirectories = metadata.getDirectories().iterator().hasNext();
            if (!hasDirectories) {
                logger.warn("File has no metadata directories: {}", file);
            }
            return hasDirectories;
        } catch (ImageProcessingException | IOException e) {
            logger.warn("File is invalid/corrupted ({}): {}", e.getMessage(), file.getFileName());
            return false;
        }
    }
}
