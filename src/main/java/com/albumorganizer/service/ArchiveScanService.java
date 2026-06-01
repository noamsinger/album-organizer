package com.albumorganizer.service;

import com.albumorganizer.model.MediaFile;
import com.albumorganizer.model.MediaType;
import com.albumorganizer.util.Constants;
import com.albumorganizer.util.FileTypeDetector;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class ArchiveScanService {

    private static final Logger logger = LoggerFactory.getLogger(ArchiveScanService.class);

    /**
     * Scans an archive (ZIP or RAR) and returns a list of MediaFile stubs for each
     * image/video entry found inside. MediaFiles have a virtual path: the archive path
     * with an internal entry path appended via a '#' separator.
     */
    public List<MediaFile> scanArchive(Path archivePath) throws IOException {
        String ext = getExtension(archivePath);
        if ("zip".equals(ext)) {
            return scanZip(archivePath);
        } else if ("rar".equals(ext)) {
            return scanRar(archivePath);
        }
        return List.of();
    }

    private List<MediaFile> scanZip(Path archivePath) throws IOException {
        List<MediaFile> result = new ArrayList<>();
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            Enumeration<? extends org.apache.commons.compress.archivers.zip.ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                Path entryPath = Path.of(entry.getName());
                if (!FileTypeDetector.isMediaFile(entryPath)) continue;

                MediaFile mf = buildMediaFile(archivePath, entry.getName(), entry.getSize(),
                    entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().toInstant() : null);
                result.add(mf);
            }
        }
        logger.info("ZIP scan of {} found {} media entries", archivePath.getFileName(), result.size());
        return result;
    }

    private List<MediaFile> scanRar(Path archivePath) throws IOException {
        List<MediaFile> result = new ArrayList<>();
        try (InputStream fis = new BufferedInputStream(Files.newInputStream(archivePath));
             ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(fis)) {
            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                Path entryPath = Path.of(entry.getName());
                if (!FileTypeDetector.isMediaFile(entryPath)) continue;

                MediaFile mf = buildMediaFile(archivePath, entry.getName(), entry.getSize(),
                    entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().toInstant() : null);
                result.add(mf);
            }
        } catch (org.apache.commons.compress.archivers.ArchiveException e) {
            throw new IOException("Failed to read archive: " + archivePath, e);
        }
        logger.info("RAR scan of {} found {} media entries", archivePath.getFileName(), result.size());
        return result;
    }

    private MediaFile buildMediaFile(Path archivePath, String entryName, long size, Instant lastModified) {
        // Virtual path: archive#entryName  (used as unique identifier)
        String virtualPathStr = archivePath.toString() + "#" + entryName;
        Path virtualPath = Path.of(virtualPathStr);
        Path entryFilename = Path.of(entryName).getFileName();

        MediaFile mf = new MediaFile();
        mf.setAbsolutePath(virtualPath);
        mf.setFilename(entryFilename != null ? entryFilename.toString() : entryName);
        mf.setSizeBytes(size < 0 ? 0 : size);
        mf.setLastModified(lastModified != null ? lastModified : Instant.EPOCH);

        String ext = getExtension(Path.of(entryName));
        if (Constants.IMAGE_EXTENSIONS.contains(ext)) {
            mf.setType(MediaType.IMAGE);
        } else {
            mf.setType(MediaType.VIDEO);
        }
        return mf;
    }

    private static String getExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    /** Returns true if the given virtual path string contains an archive separator '#'. */
    public static boolean isArchiveEntry(Path path) {
        return path != null && path.toString().contains("#");
    }

    /**
     * Parses a virtual archive path (archive.zip#entry/name) into [archivePath, entryName].
     * Returns null if the path is not a valid archive entry path.
     */
    public static String[] splitArchivePath(Path virtualPath) {
        String s = virtualPath.toString();
        int hash = s.indexOf('#');
        if (hash < 0) return null;
        return new String[]{s.substring(0, hash), s.substring(hash + 1)};
    }

    /**
     * Extracts raw bytes for a single entry from a ZIP or RAR archive.
     * @param archivePath path to the archive file
     * @param entryName   entry path inside the archive (as returned by scanArchive)
     * @return byte array of the entry contents, or null if not found
     */
    public byte[] extractEntry(Path archivePath, String entryName) throws IOException {
        String ext = getExtension(archivePath);
        if ("zip".equals(ext)) {
            return extractZipEntry(archivePath, entryName);
        } else if ("rar".equals(ext)) {
            return extractRarEntry(archivePath, entryName);
        }
        return null;
    }

    private byte[] extractZipEntry(Path archivePath, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(archivePath.toFile())) {
            var entry = zip.getEntry(entryName);
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                return readAll(in);
            }
        }
    }

    private byte[] extractRarEntry(Path archivePath, String entryName) throws IOException {
        try (InputStream fis = new BufferedInputStream(Files.newInputStream(archivePath));
             ArchiveInputStream ais = new ArchiveStreamFactory().createArchiveInputStream(fis)) {
            ArchiveEntry entry;
            while ((entry = ais.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return readAll(ais);
                }
            }
        } catch (org.apache.commons.compress.archivers.ArchiveException e) {
            throw new IOException("Failed to read archive: " + archivePath, e);
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        int n;
        while ((n = in.read(block)) != -1) buf.write(block, 0, n);
        return buf.toByteArray();
    }
}
