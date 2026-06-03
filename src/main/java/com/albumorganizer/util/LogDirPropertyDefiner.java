package com.albumorganizer.util;

import ch.qos.logback.core.PropertyDefinerBase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogDirPropertyDefiner extends PropertyDefinerBase {
    @Override
    public String getPropertyValue() {
        Path logDir = AppDirs.logsDir();
        try {
            Files.createDirectories(logDir);
            migrateOldLogs(logDir);
        } catch (IOException ignored) {}
        return logDir.toString();
    }

    private void migrateOldLogs(Path newDir) throws IOException {
        Path oldDir = Paths.get(System.getProperty("user.home"), ".config", "album-organizer", "logs");
        if (!Files.isDirectory(oldDir) || oldDir.equals(newDir)) return;
        try (var stream = Files.newDirectoryStream(oldDir)) {
            for (Path src : stream) {
                Path dest = newDir.resolve(src.getFileName());
                if (!Files.exists(dest)) Files.copy(src, dest);
            }
        }
    }
}
