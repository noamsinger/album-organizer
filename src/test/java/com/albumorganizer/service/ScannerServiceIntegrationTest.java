package com.albumorganizer.service;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.MediaType;
import com.albumorganizer.model.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScannerServiceIntegrationTest {

    @TempDir
    Path tempDir;

    private ScannerService scannerService;

    @BeforeEach
    void setUp() {
        scannerService = new ScannerService();
    }

    @Test
    void testFullScanWithHash_EmptyDirectory() {
        ScanResult result = scannerService.scanFullWithHash(List.of(tempDir), null);

        assertNotNull(result);
        assertEquals(0, result.getTotalScanned());
        assertTrue(result.getNewFiles().isEmpty());
    }

    @Test
    void testFullScanWithHash_WithImageFiles() throws IOException {
        createTestFile(tempDir, "photo1.jpg", 1024);
        createTestFile(tempDir, "photo2.png", 2048);
        createTestFile(tempDir, "document.txt", 512); // Should be ignored

        ScanResult result = scannerService.scanFullWithHash(List.of(tempDir), null);

        assertNotNull(result);
        assertEquals(2, result.getTotalScanned());
        assertEquals(2, result.getNewFiles().size());

        MediaFile file1 = findFileByName(result.getNewFiles(), "photo1.jpg");
        assertNotNull(file1);
        assertEquals(MediaType.IMAGE, file1.getType());
        assertNotNull(file1.getSha1Hash());
        assertEquals(1024, file1.getSizeBytes());

        MediaFile file2 = findFileByName(result.getNewFiles(), "photo2.png");
        assertNotNull(file2);
        assertEquals(MediaType.IMAGE, file2.getType());
    }

    @Test
    void testFullScanWithHash_WithVideoFiles() throws IOException {
        createTestFile(tempDir, "video1.mp4", 10240);
        createTestFile(tempDir, "video2.mov", 20480);

        ScanResult result = scannerService.scanFullWithHash(List.of(tempDir), null);

        assertNotNull(result);
        assertEquals(2, result.getTotalScanned());

        MediaFile file1 = findFileByName(result.getNewFiles(), "video1.mp4");
        assertNotNull(file1);
        assertEquals(MediaType.VIDEO, file1.getType());

        MediaFile file2 = findFileByName(result.getNewFiles(), "video2.mov");
        assertNotNull(file2);
        assertEquals(MediaType.VIDEO, file2.getType());
    }

    @Test
    void testFullScanWithHash_NestedDirectories() throws IOException {
        Path subDir1 = Files.createDirectory(tempDir.resolve("subdir1"));
        Path subDir2 = Files.createDirectory(tempDir.resolve("subdir2"));

        createTestFile(tempDir, "photo1.jpg", 1024);
        createTestFile(subDir1, "photo2.jpg", 2048);
        createTestFile(subDir2, "video1.mp4", 4096);

        ScanResult result = scannerService.scanFullWithHash(List.of(tempDir), null);

        assertNotNull(result);
        assertEquals(3, result.getTotalScanned());
        assertEquals(3, result.getNewFiles().size());
    }

    @Test
    void testFullScanWithHash_HashesAreCalculated() throws IOException {
        createTestFile(tempDir, "photo1.jpg", 1024);

        ScanResult result = scannerService.scanFullWithHash(List.of(tempDir), null);

        MediaFile file = findFileByName(result.getNewFiles(), "photo1.jpg");
        assertNotNull(file);
        assertNotNull(file.getSha1Hash());
        assertFalse(file.getSha1Hash().isEmpty());
        assertEquals(40, file.getSha1Hash().length()); // SHA-1 is 40 hex chars
    }

    @Test
    void testQuickScan_ReturnsAllFiles() throws IOException {
        createTestFile(tempDir, "photo1.jpg", 1024);
        createTestFile(tempDir, "photo2.jpg", 2048);

        ScanResult result = scannerService.scanQuick(List.of(tempDir));

        assertNotNull(result);
        assertEquals(2, result.getTotalScanned());
        assertEquals(2, result.getNewFiles().size());
    }

    @Test
    void testQuickScan_NoHashCalculated() throws IOException {
        createTestFile(tempDir, "photo1.jpg", 1024);

        ScanResult result = scannerService.scanQuick(List.of(tempDir));

        assertNotNull(result);
        assertEquals(1, result.getTotalScanned());
        MediaFile file = findFileByName(result.getNewFiles(), "photo1.jpg");
        assertNotNull(file);
        assertNull(file.getSha1Hash()); // Quick scan skips hash calculation
    }

    @Test
    void testQuickScan_EmptyDirectory() {
        ScanResult result = scannerService.scanQuick(List.of(tempDir));

        assertNotNull(result);
        assertEquals(0, result.getTotalScanned());
        assertTrue(result.getNewFiles().isEmpty());
    }

    // Helper methods

    private Path createTestFile(Path dir, String filename, int size) throws IOException {
        Path file = dir.resolve(filename);
        byte[] content = new byte[size];
        for (int i = 0; i < size; i++) {
            content[i] = (byte) (i % 256);
        }
        Files.write(file, content);
        return file;
    }

    private MediaFile findFileByName(List<MediaFile> files, String filename) {
        return files.stream()
                    .filter(f -> f.getFilename().equals(filename))
                    .findFirst()
                    .orElse(null);
    }
}
