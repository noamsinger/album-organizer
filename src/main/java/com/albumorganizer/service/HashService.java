package com.albumorganizer.service;

import com.albumorganizer.util.Constants;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

/**
 * Service for calculating SHA-1 hashes of files.
 * Supports parallel batch processing for performance.
 */
public class HashService {

    private static final Logger logger = LoggerFactory.getLogger(HashService.class);
    private final ForkJoinPool forkJoinPool;

    public HashService() {
        int parallelism = Runtime.getRuntime().availableProcessors();
        this.forkJoinPool = new ForkJoinPool(parallelism);
        logger.info("HashService initialized with parallelism: {}", parallelism);
    }

    /**
     * Calculates SHA-1 hash for a single file.
     *
     * @param file the file to hash
     * @return hex string of SHA-1 hash
     * @throws IOException if file cannot be read
     */
    public String calculateHash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(Constants.HASH_ALGORITHM);
            try (InputStream fis = Files.newInputStream(file)) {
                byte[] buffer = new byte[Constants.HASH_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return Hex.encodeHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            // Should never happen for SHA-1
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    /**
     * Calculates hashes for multiple files in parallel.
     *
     * @param files the files to hash
     * @return map of file path to hash string
     */
    public Map<Path, String> calculateHashBatch(List<Path> files) {
        if (files.isEmpty()) {
            return new HashMap<>();
        }
        HashBatchTask task = new HashBatchTask(files, 0, files.size());
        return forkJoinPool.invoke(task);
    }

    /**
     * Shuts down the fork-join pool.
     */
    public void shutdown() {
        forkJoinPool.shutdown();
    }

    /**
     * RecursiveTask for parallel hash calculation.
     */
    private class HashBatchTask extends RecursiveTask<Map<Path, String>> {
        private static final int THRESHOLD = 10; // Process directly if less than threshold
        private final List<Path> files;
        private final int start;
        private final int end;

        public HashBatchTask(List<Path> files, int start, int end) {
            this.files = files;
            this.start = start;
            this.end = end;
        }

        @Override
        protected Map<Path, String> compute() {
            int length = end - start;

            if (length <= THRESHOLD) {
                // Process directly
                Map<Path, String> result = new HashMap<>();
                for (int i = start; i < end; i++) {
                    Path file = files.get(i);
                    try {
                        String hash = calculateHash(file);
                        result.put(file, hash);
                    } catch (IOException e) {
                        logger.warn("Failed to hash file: {}", file, e);
                    }
                }
                return result;
            } else {
                // Split task
                int mid = start + length / 2;
                HashBatchTask leftTask = new HashBatchTask(files, start, mid);
                HashBatchTask rightTask = new HashBatchTask(files, mid, end);

                leftTask.fork();
                Map<Path, String> rightResult = rightTask.compute();
                Map<Path, String> leftResult = leftTask.join();

                leftResult.putAll(rightResult);
                return leftResult;
            }
        }
    }
}
