package com.albumorganizer.service;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class ScannerService {

    private static final Logger logger = LoggerFactory.getLogger(ScannerService.class);

    private final QuickScanStrategy quickScanStrategy;
    private volatile boolean cancelled = false;

    public ScannerService() {
        MetadataService metadataService = new MetadataService();
        this.quickScanStrategy = new QuickScanStrategy(metadataService, this);
    }

    public ScannerService(MetadataService metadataService) {
        this.quickScanStrategy = new QuickScanStrategy(metadataService, this);
    }

    public void setFileDiscoveryCallback(Consumer<MediaFile> callback) {
        quickScanStrategy.setFileDiscoveryCallback(callback);
    }

    public ScanResult scanQuick(List<Path> baseFolders) {
        if (baseFolders == null || baseFolders.isEmpty()) {
            logger.warn("No folders specified for quick scan");
            return new ScanResult();
        }
        logger.info("Starting quick scan of {} folders", baseFolders.size());
        cancelled = false;
        ScanResult result = quickScanStrategy.scan(baseFolders);
        if (cancelled) logger.info("Quick scan was cancelled");
        return result;
    }

    public void cancelScan() {
        logger.info("Cancelling scan operation");
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
