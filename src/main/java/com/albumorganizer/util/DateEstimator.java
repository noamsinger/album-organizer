package com.albumorganizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for estimating date/time from filename patterns.
 */
public class DateEstimator {

    private static final Logger logger = LoggerFactory.getLogger(DateEstimator.class);

    // Pattern for YYYYMMDD_HHmmSS or YYYYMMDD-HHmmSS (e.g., "20231225_143022" or "20231225-143022")
    private static final Pattern PATTERN_YYYYMMDD_HHMMSS =
        Pattern.compile("(\\d{4})(\\d{2})(\\d{2})[_-](\\d{2})(\\d{2})(\\d{2})");

    // Pattern for YYYYMMDDHHmmSS (e.g., "20231225143022")
    private static final Pattern PATTERN_YYYYMMDDHHMMSS =
        Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})");

    // Pattern for YYYY?MM?DD?HH?mm?SS with any separator (e.g., "2023-12-25 14:30:22")
    private static final Pattern PATTERN_YYYY_MM_DD_HH_MM_SS =
        Pattern.compile("(\\d{4})\\D+(\\d{2})\\D+(\\d{2})\\D+(\\d{2})\\D+(\\d{2})\\D+(\\d{2})");

    // Pattern for YYYY-MM-DD (e.g., "2023-12-25")
    private static final Pattern PATTERN_YYYY_MM_DD =
        Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");

    // Pattern for YYYYMMDD (e.g., "20231225")
    private static final Pattern PATTERN_YYYYMMDD =
        Pattern.compile("(\\d{4})(\\d{2})(\\d{2})");

    /**
     * Attempts to estimate date/time from filename.
     * Returns null if no valid date pattern is found.
     *
     * @param filename the filename to analyze
     * @return Instant representing estimated date/time, or null if cannot be estimated
     */
    public static Instant estimateFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        // Try patterns in order of specificity (most specific first)

        // 1. Try YYYY?MM?DD?HH?mm?SS with separators
        Instant result = tryPattern(filename, PATTERN_YYYY_MM_DD_HH_MM_SS, true);
        if (result != null) {
            logger.debug("Estimated date from filename '{}' using YYYY?MM?DD?HH?mm?SS pattern", filename);
            return result;
        }

        // 2. Try YYYYMMDD_HHmmSS or YYYYMMDD-HHmmSS
        result = tryPattern(filename, PATTERN_YYYYMMDD_HHMMSS, true);
        if (result != null) {
            logger.debug("Estimated date from filename '{}' using YYYYMMDD-HHmmSS pattern", filename);
            return result;
        }

        // 3. Try YYYYMMDDHHmmSS (no separators)
        result = tryPattern(filename, PATTERN_YYYYMMDDHHMMSS, true);
        if (result != null) {
            logger.debug("Estimated date from filename '{}' using YYYYMMDDHHmmSS pattern", filename);
            return result;
        }

        // 4. Try YYYY-MM-DD (date only, use noon as time)
        result = tryPattern(filename, PATTERN_YYYY_MM_DD, false);
        if (result != null) {
            logger.debug("Estimated date from filename '{}' using YYYY-MM-DD pattern (noon)", filename);
            return result;
        }

        // 5. Try YYYYMMDD (date only, use noon as time)
        result = tryPattern(filename, PATTERN_YYYYMMDD, false);
        if (result != null) {
            logger.debug("Estimated date from filename '{}' using YYYYMMDD pattern (noon)", filename);
            return result;
        }

        logger.debug("Could not estimate date from filename '{}'", filename);
        return null;
    }

    /**
     * Tries to extract date/time using the given pattern.
     *
     * @param filename the filename to analyze
     * @param pattern the regex pattern to try
     * @param hasTime true if pattern includes time components
     * @return Instant if valid date found, null otherwise
     */
    private static Instant tryPattern(String filename, Pattern pattern, boolean hasTime) {
        Matcher matcher = pattern.matcher(filename);
        if (!matcher.find()) {
            return null;
        }

        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));

            // Validate date ranges
            if (!isValidDate(year, month, day)) {
                return null;
            }

            if (hasTime && matcher.groupCount() >= 6) {
                int hour = Integer.parseInt(matcher.group(4));
                int minute = Integer.parseInt(matcher.group(5));
                int second = Integer.parseInt(matcher.group(6));

                // Validate time ranges
                if (!isValidTime(hour, minute, second)) {
                    return null;
                }

                LocalDateTime dateTime = LocalDateTime.of(year, month, day, hour, minute, second);
                return dateTime.toInstant(ZoneOffset.UTC);
            } else {
                // Date only - use noon (12:00:00)
                LocalDateTime dateTime = LocalDateTime.of(year, month, day, 12, 0, 0);
                return dateTime.toInstant(ZoneOffset.UTC);
            }
        } catch (Exception e) {
            // Invalid date/time values
            logger.debug("Invalid date values in filename '{}': {}", filename, e.getMessage());
            return null;
        }
    }

    /**
     * Validates date components.
     */
    private static boolean isValidDate(int year, int month, int day) {
        if (year < 1800 || year > 2099) return false;
        if (month < 1 || month > 12) return false;
        if (day < 1 || day > 31) return false;

        // Check day is valid for the month and year (handles leap years automatically)
        try {
            LocalDate.of(year, month, day);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates time components.
     */
    private static boolean isValidTime(int hour, int minute, int second) {
        return hour >= 0 && hour <= 23 &&
               minute >= 0 && minute <= 59 &&
               second >= 0 && second <= 59;
    }
}
