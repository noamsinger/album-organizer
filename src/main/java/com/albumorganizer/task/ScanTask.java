package com.albumorganizer.task;

import com.albumorganizer.model.FileIndexEntry;
import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.ScanResult;
import com.albumorganizer.service.ScannerService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * JavaFX Task for running scan operations in the background.
 */
public class ScanTask extends Task<ScanResult> {

    private static final Logger logger = LoggerFactory.getLogger(ScanTask.class);

    private final ScannerService scannerService;
    private final List<Path> baseFolders;
    private final boolean isFullScanWithHash;
    private final Map<String, List<FileIndexEntry>> existingFileIndex;

    public ScanTask(ScannerService scannerService, List<Path> baseFolders, boolean isFullScanWithHash,
                    Map<String, List<FileIndexEntry>> existingFileIndex) {
        this.scannerService = scannerService;
        this.baseFolders = baseFolders;
        this.isFullScanWithHash = isFullScanWithHash;
        this.existingFileIndex = existingFileIndex;
    }

    @Override
    protected ScanResult call() throws Exception {
        logger.info("ScanTask started: {} scan of {} folders",
                    isFullScanWithHash ? "Full Scan with Hash" : "Quick", baseFolders.size());

        updateTitle(isFullScanWithHash ? "Full Scan with Hash" : "Quick Scan");
        updateMessage("Initializing scan...");
        updateProgress(0, 100);

        try {
            ScanResult result;

            if (isFullScanWithHash) {
                updateMessage("Scanning all files...");
                result = scannerService.scanFullWithHash(baseFolders, existingFileIndex);
            } else {
                updateMessage("Checking for changes...");
                result = scannerService.scanQuick(baseFolders);
            }

            if (isCancelled()) {
                updateMessage("Scan cancelled");
                return new ScanResult();
            }

            updateProgress(100, 100);
            updateMessage(String.format("Scan complete: %d files found", result.getTotalScanned()));

            logger.info("ScanTask completed: {} files scanned in {}",
                       result.getTotalScanned(), result.getScanDuration());

            return result;
        } catch (Exception e) {
            logger.error("Error during scan", e);
            updateMessage("Scan failed: " + e.getMessage());
            throw e;
        }
    }

    @Override
    protected void cancelled() {
        super.cancelled();
        logger.info("ScanTask cancelled");
        scannerService.cancelScan();
        updateMessage("Scan cancelled");
    }

    @Override
    protected void failed() {
        super.failed();
        logger.error("ScanTask failed", getException());
        updateMessage("Scan failed");
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        logger.info("ScanTask succeeded");
    }
}
