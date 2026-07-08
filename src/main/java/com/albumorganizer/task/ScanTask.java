package com.albumorganizer.task;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.ScanResult;
import com.albumorganizer.service.ScannerService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

public class ScanTask extends Task<ScanResult> {

    private static final Logger logger = LoggerFactory.getLogger(ScanTask.class);

    private final ScannerService scannerService;
    private final List<Path> baseFolders;

    public ScanTask(ScannerService scannerService, List<Path> baseFolders) {
        this.scannerService = scannerService;
        this.baseFolders = baseFolders;
    }

    @Override
    protected ScanResult call() throws Exception {
        logger.info("ScanTask started: quick scan of {} folders", baseFolders.size());
        updateTitle("Quick Scan");
        updateMessage("Scanning...");
        updateProgress(0, 100);

        try {
            ScanResult result = scannerService.scanQuick(baseFolders);

            if (isCancelled()) {
                updateMessage("Scan cancelled");
                return new ScanResult();
            }

            updateProgress(100, 100);
            updateMessage(String.format("Scan complete: %d files found", result.getTotalScanned()));
            logger.info("ScanTask completed: {} files in {}", result.getTotalScanned(), result.getScanDuration());
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
