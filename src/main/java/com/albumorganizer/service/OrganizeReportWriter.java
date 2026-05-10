package com.albumorganizer.service;

import com.albumorganizer.model.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Writes detailed organization reports to an in-memory buffer.
 * Content can be retrieved later via getContent().
 */
public class OrganizeReportWriter {

    private static final Logger logger = LoggerFactory.getLogger(OrganizeReportWriter.class);
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringWriter stringWriter;
    private final Instant startTime;

    public OrganizeReportWriter(Path rootPath) throws IOException {
        this.startTime = Instant.now();
        this.stringWriter = new StringWriter();
        writeHeader(rootPath);
    }

    private void writeHeader(Path rootPath) {
        stringWriter.write("=".repeat(80) + "\n");
        stringWriter.write("ALBUM ORGANIZER - ORGANIZATION REPORT\n");
        stringWriter.write("=".repeat(80) + "\n");
        stringWriter.write("Start Time: " + formatTimestamp(startTime) + "\n");
        stringWriter.write("Root Path: " + rootPath.toAbsolutePath() + "\n");
        stringWriter.write("=".repeat(80) + "\n\n");
    }

    public synchronized void logDirectoryStart(Path directory) throws IOException {
        stringWriter.write("[" + formatTimestamp(Instant.now()) + "] Processing directory: " + directory + "\n");
    }

    public synchronized void logSuccess(MediaFile file, Path sourcePath, Path targetPath) throws IOException {
        stringWriter.write("[" + formatTimestamp(Instant.now()) + "] SUCCESS: " + file.getFilename() + "\n");
        stringWriter.write("  From: " + sourcePath + "\n");
        stringWriter.write("  To:   " + targetPath + "\n");
    }

    public synchronized void logSkipped(MediaFile file, Path sourcePath, String reason) throws IOException {
        stringWriter.write("[" + formatTimestamp(Instant.now()) + "] SKIPPED: " + file.getFilename() + "\n");
        stringWriter.write("  Path: " + sourcePath + "\n");
        stringWriter.write("  Reason: " + reason + "\n");
    }

    public synchronized void logFailure(MediaFile file, Path sourcePath, String error) throws IOException {
        stringWriter.write("[" + formatTimestamp(Instant.now()) + "] FAILED: " + file.getFilename() + "\n");
        stringWriter.write("  Path: " + sourcePath + "\n");
        stringWriter.write("  Error: " + error + "\n");
    }

    public synchronized void writeSummary(int processed, int succeeded, int skipped, int failed) throws IOException {
        Instant endTime = Instant.now();
        long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();

        stringWriter.write("\n");
        stringWriter.write("=".repeat(80) + "\n");
        stringWriter.write("SUMMARY\n");
        stringWriter.write("=".repeat(80) + "\n");
        stringWriter.write("End Time: " + formatTimestamp(endTime) + "\n");
        stringWriter.write("Duration: " + formatDuration(durationSeconds) + "\n");
        stringWriter.write("Total Processed: " + processed + "\n");
        stringWriter.write("  Succeeded: " + succeeded + "\n");
        stringWriter.write("  Skipped:   " + skipped + "\n");
        stringWriter.write("  Failed:    " + failed + "\n");
        stringWriter.write("=".repeat(80) + "\n");
    }

    public void close() {
        logger.info("Organization report generated ({} chars)", stringWriter.getBuffer().length());
    }

    public String getContent() {
        return stringWriter.toString();
    }

    public Instant getStartTime() {
        return startTime;
    }

    private String formatTimestamp(Instant instant) {
        return LOG_TIMESTAMP_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d hours, %d minutes, %d seconds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%d minutes, %d seconds", minutes, secs);
        } else {
            return String.format("%d seconds", secs);
        }
    }
}
