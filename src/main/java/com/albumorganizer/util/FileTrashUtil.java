package com.albumorganizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility class for moving files to the system trash/recycle bin.
 */
public class FileTrashUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileTrashUtil.class);
    private static final String OS = System.getProperty("os.name").toLowerCase();

    /**
     * Moves a file to the system trash/recycle bin.
     *
     * @param filePath Path to the file to trash
     * @return true if successful, false otherwise
     */
    public static boolean moveToTrash(Path filePath) {
        if (filePath == null || !filePath.toFile().exists()) {
            logger.warn("Cannot move to trash: file does not exist: {}", filePath);
            return false;
        }

        try {
            File file = filePath.toFile();

            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();

                // Check if moveToTrash is supported (Java 9+)
                if (desktop.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH)) {
                    boolean success = desktop.moveToTrash(file);
                    if (success) {
                        logger.info("Moved to trash: {}", filePath);
                        return true;
                    } else {
                        logger.error("Failed to move to trash: {}", filePath);
                        return false;
                    }
                }
            }

            // Fallback: use platform-specific commands
            return moveToTrashFallback(file);

        } catch (Exception e) {
            logger.error("Error moving file to trash: {}", filePath, e);
            return false;
        }
    }

    /**
     * Fallback method using platform-specific commands.
     */
    private static boolean moveToTrashFallback(File file) throws IOException, InterruptedException {
        ProcessBuilder pb;

        if (OS.contains("mac")) {
            // macOS: use osascript to move to Trash
            pb = new ProcessBuilder(
                "osascript",
                "-e",
                "tell application \"Finder\" to delete POSIX file \"" + file.getAbsolutePath() + "\""
            );
        } else if (OS.contains("win")) {
            // Windows: use PowerShell to move to Recycle Bin
            pb = new ProcessBuilder(
                "powershell.exe",
                "-Command",
                "Add-Type -AssemblyName Microsoft.VisualBasic; " +
                "[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile('" +
                file.getAbsolutePath().replace("'", "''") + "', " +
                "'Microsoft.VisualBasic.FileIO.UIOption.OnlyErrorDialogs', " +
                "'Microsoft.VisualBasic.FileIO.RecycleOption.SendToRecycleBin')"
            );
        } else if (OS.contains("nux") || OS.contains("nix")) {
            // Linux: use gio trash (most modern distros) or trash-cli
            if (isCommandAvailable("gio")) {
                pb = new ProcessBuilder("gio", "trash", file.getAbsolutePath());
            } else if (isCommandAvailable("trash")) {
                pb = new ProcessBuilder("trash", file.getAbsolutePath());
            } else if (isCommandAvailable("trash-put")) {
                pb = new ProcessBuilder("trash-put", file.getAbsolutePath());
            } else {
                logger.error("No trash command found on Linux. Install 'trash-cli' or use 'gio'.");
                return false;
            }
        } else {
            logger.error("Unsupported operating system for trash operation: {}", OS);
            return false;
        }

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            logger.info("Moved to trash using fallback method: {}", file);
            return true;
        } else {
            logger.error("Failed to move to trash (exit code {}): {}", exitCode, file);
            return false;
        }
    }

    /**
     * Checks if a command is available in the system PATH.
     */
    private static boolean isCommandAvailable(String command) {
        try {
            Process process = new ProcessBuilder("which", command).start();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
