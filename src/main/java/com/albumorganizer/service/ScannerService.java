package com.albumorganizer.service;

import com.albumorganizer.model.FileIndexEntry;
import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.ScanResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service for orchestrating scan operations.
 * Delegates to FullScanWithHashStrategy or QuickScanStrategy.
 */
public class ScannerService {

    private static final Logger logger = LoggerFactory.getLogger(ScannerService.class);

    private final FullScanWithHashStrategy fullScanWithHashStrategy;
    private final QuickScanStrategy quickScanStrategy;
    private volatile boolean cancelled = false;
    private Consumer<MediaFile> fileDiscoveryCallback = null;

    public ScannerService() {
        HashService hashService = new HashService();
        MetadataService metadataService = new MetadataService();

        this.fullScanWithHashStrategy = new FullScanWithHashStrategy(hashService, metadataService, this);
        this.quickScanStrategy = new QuickScanStrategy(hashService, metadataService, this);
    }

    public ScannerService(HashService hashService, MetadataService metadataService) {
        this.fullScanWithHashStrategy = new FullScanWithHashStrategy(hashService, metadataService, this);
        this.quickScanStrategy = new QuickScanStrategy(hashService, metadataService, this);
    }

    public void setFileDiscoveryCallback(Consumer<MediaFile> callback) {
        this.fileDiscoveryCallback = callback;
        fullScanWithHashStrategy.setFileDiscoveryCallback(callback);
        quickScanStrategy.setFileDiscoveryCallback(callback);
    }

    /**
     * Performs a full scan with hash of the specified folders.
     * Only recalculates hashes for new, modified, or unhashed files.
     *
     * @param baseFolders list of base folders to scan
     * @param existingFileIndex existing file index for smart hash recalculation
     * @return scan result
     */
    public ScanResult scanFullWithHash(List<Path> baseFolders, Map<String, List<FileIndexEntry>> existingFileIndex) {
        if (baseFolders == null || baseFolders.isEmpty()) {
            logger.warn("No folders specified for full scan with hash");
            return new ScanResult();
        }

        logger.info("Starting full scan with hash of {} folders", baseFolders.size());
        cancelled = false;

        // Pass existing file index to strategy for smart hash recalculation
        fullScanWithHashStrategy.setExistingFileIndex(existingFileIndex);

        ScanResult result = fullScanWithHashStrategy.scan(baseFolders);

        if (cancelled) {
            logger.info("Full scan with hash was cancelled");
        }

        return result;
    }

    /**
     * Performs a quick scan of the specified folders.
     *
     * @param baseFolders list of base folders to scan
     * @return scan result
     */
    public ScanResult scanQuick(List<Path> baseFolders) {
        if (baseFolders == null || baseFolders.isEmpty()) {
            logger.warn("No folders specified for quick scan");
            return new ScanResult();
        }

        logger.info("Starting quick scan of {} folders", baseFolders.size());
        cancelled = false;

        ScanResult result = quickScanStrategy.scan(baseFolders);

        if (cancelled) {
            logger.info("Quick scan was cancelled");
        }

        return result;
    }

    /**
     * Cancels the current scan operation.
     */
    public void cancelScan() {
        logger.info("Cancelling scan operation");
        cancelled = true;
    }

    /**
     * Checks if the scan has been cancelled.
     *
     * @return true if cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
