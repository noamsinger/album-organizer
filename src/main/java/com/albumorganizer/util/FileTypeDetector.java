package com.albumorganizer.util;

import com.albumorganizer.model.MediaType;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Utility class for detecting file types by extension.
 */
public class FileTypeDetector {

    /**
     * Checks if a file is a supported media file (image or video).
     *
     * @param path the file path
     * @return true if the file is a supported media file
     */
    public static boolean isMediaFile(Path path) {
        return isImage(path) || isVideo(path);
    }

    /**
     * Checks if a file is a supported image.
     *
     * @param path the file path
     * @return true if the file is a supported image
     */
    public static boolean isImage(Path path) {
        String extension = getExtension(path);
        return extension != null && Constants.IMAGE_EXTENSIONS.contains(extension);
    }

    /**
     * Checks if a file is a supported video.
     *
     * @param path the file path
     * @return true if the file is a supported video
     */
    public static boolean isVideo(Path path) {
        String extension = getExtension(path);
        return extension != null && Constants.VIDEO_EXTENSIONS.contains(extension);
    }

    /**
     * Gets the media type for a file.
     *
     * @param path the file path
     * @return the media type, or null if not a supported media file
     */
    public static MediaType getMediaType(Path path) {
        if (isImage(path)) {
            return MediaType.IMAGE;
        } else if (isVideo(path)) {
            return MediaType.VIDEO;
        }
        return null;
    }

    /**
     * Extracts the file extension from a path (lowercase, without dot).
     *
     * @param path the file path
     * @return the extension, or null if no extension found
     */
    private static String getExtension(Path path) {
        if (path == null) {
            return null;
        }
        String filename = path.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private FileTypeDetector() {
        // Utility class, no instantiation
    }
}
