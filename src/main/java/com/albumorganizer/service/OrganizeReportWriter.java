package com.albumorganizer.service;

import com.albumorganizer.model.MediaFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Writes detailed organization reports to log files.
 */
public class OrganizeReportWriter {

    private static final Logger logger = LoggerFactory.getLogger(OrganizeReportWriter.class);
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path reportFile;
    private final BufferedWriter writer;
    private final Instant startTime;

    /**
     * Creates a new report writer with a timestamped filename.
     *
     * @param rootPath the root path being organized
     * @throws IOException if the report file cannot be created
     */
    public OrganizeReportWriter(Path rootPath) throws IOException {
        this.startTime = Instant.now();

        // Create reports directory in ~/.album-organizer/
        Path reportsDir = Paths.get(System.getProperty("user.home"), ".album-organizer");
        Files.createDirectories(reportsDir);

        // Create timestamped filename
        String timestamp = FILENAME_FORMATTER.format(startTime.atZone(ZoneId.systemDefault()));
        String filename = "organization-report-" + timestamp + ".log";
        this.reportFile = reportsDir.resolve(filename);

        // Open file for writing
        this.writer = Files.newBufferedWriter(reportFile);

        // Write header
        writeHeader(rootPath);
    }

    private void writeHeader(Path rootPath) throws IOException {
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("ALBUM ORGANIZER - ORGANIZATION REPORT");
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("Start Time: " + formatTimestamp(startTime));
        writer.newLine();
        writer.write("Root Path: " + rootPath.toAbsolutePath());
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.newLine();
        writer.flush();
    }

    /**
     * Logs the start of processing a new directory.
     */
    public synchronized void logDirectoryStart(Path directory) throws IOException {
        writer.write("[" + formatTimestamp(Instant.now()) + "] Processing directory: " + directory);
        writer.newLine();
        writer.flush();
    }

    /**
     * Logs a successful file operation.
     */
    public synchronized void logSuccess(MediaFile file, Path sourcePath, Path targetPath) throws IOException {
        writer.write("[" + formatTimestamp(Instant.now()) + "] SUCCESS: " + file.getFilename());
        writer.newLine();
        writer.write("  From: " + sourcePath);
        writer.newLine();
        writer.write("  To:   " + targetPath);
        writer.newLine();
        writer.flush();
    }

    /**
     * Logs a skipped file operation.
     */
    public synchronized void logSkipped(MediaFile file, Path sourcePath, String reason) throws IOException {
        writer.write("[" + formatTimestamp(Instant.now()) + "] SKIPPED: " + file.getFilename());
        writer.newLine();
        writer.write("  Path: " + sourcePath);
        writer.newLine();
        writer.write("  Reason: " + reason);
        writer.newLine();
        writer.flush();
    }

    /**
     * Logs a failed file operation.
     */
    public synchronized void logFailure(MediaFile file, Path sourcePath, String error) throws IOException {
        writer.write("[" + formatTimestamp(Instant.now()) + "] FAILED: " + file.getFilename());
        writer.newLine();
        writer.write("  Path: " + sourcePath);
        writer.newLine();
        writer.write("  Error: " + error);
        writer.newLine();
        writer.flush();
    }

    /**
     * Writes the final summary and closes the report.
     */
    public synchronized void writeSummary(int processed, int succeeded, int skipped, int failed) throws IOException {
        Instant endTime = Instant.now();
        long durationSeconds = endTime.getEpochSecond() - startTime.getEpochSecond();

        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("SUMMARY");
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.write("End Time: " + formatTimestamp(endTime));
        writer.newLine();
        writer.write("Duration: " + formatDuration(durationSeconds));
        writer.newLine();
        writer.write("Total Processed: " + processed);
        writer.newLine();
        writer.write("  Succeeded: " + succeeded);
        writer.newLine();
        writer.write("  Skipped:   " + skipped);
        writer.newLine();
        writer.write("  Failed:    " + failed);
        writer.newLine();
        writer.write("=".repeat(80));
        writer.newLine();
        writer.flush();
    }

    /**
     * Closes the report file.
     */
    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
            logger.info("Organization report written to: {}", reportFile);
        } catch (IOException e) {
            logger.error("Failed to close report file", e);
        }
    }

    /**
     * Returns the path to the report file.
     */
    public Path getReportFile() {
        return reportFile;
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
