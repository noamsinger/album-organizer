package com.albumorganizer.util;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Utility class for formatting values for UI display.
 */
public class FormatUtils {

    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,##0.0");
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_TAKEN_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm:ss'Z'")
            .withZone(ZoneId.of("UTC"));

    /**
     * Formats file size in bytes to human-readable format (KB, MB, GB).
     *
     * @param bytes the size in bytes
     * @return formatted size string
     */
    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return SIZE_FORMAT.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return SIZE_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    /**
     * Formats an Instant to locale-specific date/time string.
     *
     * @param instant the instant to format
     * @return formatted date/time string
     */
    public static String formatDateTime(Instant instant) {
        if (instant == null) {
            return "";
        }
        return DATE_FORMATTER.format(instant);
    }

    /**
     * Formats date taken with the format YYYY/MM/DD-HH:mm:SSZ (UTC).
     * If estimated is true, prepends "=> ".
     *
     * @param instant the instant to format (will be displayed in UTC)
     * @param estimated true if the date was estimated from filename
     * @return formatted date string in UTC
     */
    public static String formatDateTaken(Instant instant, boolean estimated) {
        if (instant == null) {
            return "";
        }
        String formatted = DATE_TAKEN_FORMATTER.format(instant);
        return estimated ? "=> " + formatted : formatted;
    }

    /**
     * Formats a dimension (resolution) as "widthxheight".
     *
     * @param width  the width
     * @param height the height
     * @return formatted resolution string
     */
    public static String formatResolution(int width, int height) {
        return width + "x" + height;
    }

    /**
     * Truncates a string to max length with ellipsis.
     *
     * @param str       the string to truncate
     * @param maxLength the maximum length
     * @return truncated string
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Formats duration in seconds to HH:MM:SS or MM:SS format.
     *
     * @param seconds duration in seconds
     * @return formatted string like "1:23:45" or "2:15"
     */
    public static String formatDuration(Long seconds) {
        if (seconds == null) {
            return "";
        }

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%d:%02d", minutes, secs);
        }
    }

    private FormatUtils() {
        // Utility class, no instantiation
    }
}
