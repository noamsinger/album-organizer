package com.albumorganizer.util;

import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Application-wide constants.
 */
public class Constants {

    // Supported file extensions
    public static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "jpg", "jpeg", "png", "gif", "bmp", "tiff", "tif", "webp", "heic", "raw"
    );

    public static final Set<String> VIDEO_EXTENSIONS = Set.of(
        "mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v", "mpg", "mpeg"
    );

    // Hash algorithm
    public static final String HASH_ALGORITHM = "SHA-1";
    public static final int HASH_BUFFER_SIZE = 8192; // 8KB

    private Constants() {
        // Utility class, no instantiation
    }
}
