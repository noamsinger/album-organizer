package com.albumorganizer.service.enhancement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local video upscaling using the video2x CLI.
 * video2x is an open-source wrapper that chains FFmpeg + Real-ESRGAN/Waifu2x/SRMD for video upscaling.
 *
 * Install: pip install video2x  OR  download binary from github.com/k4yt3x/video2x/releases
 * Usage: video2x -i input.mp4 -o output.mp4 -p4 realesrgan
 */
public class Video2xProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(Video2xProvider.class);

    private final String video2xPath;

    public Video2xProvider(String video2xPath) {
        this.video2xPath = video2xPath.isBlank() ? "video2x" : video2xPath;
    }

    @Override public String getName()           { return "video2x (Local)"; }
    @Override public String getShortId()        { return "Video2x"; }
    @Override public boolean supportsPrompt()   { return false; }
    @Override public boolean supportsVideo()    { return true; }
    @Override public String estimatedCostPerImage() { return "Free (local)"; }

    @Override
    public boolean isConfigured() {
        if (video2xPath.equals("video2x")) {
            // Accept "video2x" if it resolves on PATH
            return commandExists("video2x");
        }
        return Files.exists(Paths.get(video2xPath));
    }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        Path input = request.inputPath();
        String filename = input.getFileName().toString();
        Path outputPath = resolveOutputPath(input, ".mp4", request);

        String processor = request.param("processor", "realesrgan");
        String scale = request.param("scale", "4");

        logger.info("video2x: processing {} → {} ({}x {})", filename, outputPath.getFileName(), scale, processor);

        String[] cmd = {
            video2xPath,
            "-i", input.toString(),
            "-o", outputPath.toString(),
            "-p" + scale, processor
        };

        logger.debug("video2x command: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            return EnhancementResult.failure("video2x failed to start — is it installed? (" + e.getMessage() + ")");
        }

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                logger.debug("video2x: {}", line);
            }
        }

        int exit;
        try {
            exit = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EnhancementResult.failure("video2x interrupted");
        }

        if (exit != 0) {
            String detail = output.toString();
            logger.error("video2x exited with code {}: {}", exit, detail);
            return EnhancementResult.failure("video2x failed (exit " + exit + ")", detail, null, null);
        }

        if (!Files.exists(outputPath)) {
            return EnhancementResult.failure("video2x completed but output file not found: " + outputPath);
        }

        logger.info("video2x result saved to {}", outputPath);
        return EnhancementResult.ok(outputPath);
    }

    private boolean commandExists(String cmd) {
        try {
            Process proc = new ProcessBuilder(cmd, "--version").redirectErrorStream(true).start();
            proc.waitFor();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Path resolveOutputPath(Path input, String ext, EnhancementRequest request) {
        if (request.customOutputPath() != null) return request.customOutputPath();
        String base = input.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot >= 0) base = base.substring(0, dot);
        long epoch = System.currentTimeMillis() / 1000;
        String outName = base + "_" + getShortId() + "-" + epoch + ext;
        Path dir = request.outputDir() != null ? request.outputDir() : input.getParent();
        return dir.resolve(outName);
    }
}
