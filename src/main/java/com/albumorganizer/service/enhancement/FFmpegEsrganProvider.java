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
 * Local video upscaling using FFmpeg + Real-ESRGAN NCNN Vulkan CLI.
 * Pipeline: FFmpeg extracts frames → realesrgan-ncnn-vulkan upscales each frame → FFmpeg reassembles.
 * Both binaries must be installed; paths configured in settings.
 *
 * Install: brew install ffmpeg && brew install realesrgan-ncnn-vulkan
 * Or download realesrgan-ncnn-vulkan from github.com/xinntao/Real-ESRGAN/releases
 */
public class FFmpegEsrganProvider implements EnhancementProvider {

    private static final Logger logger = LoggerFactory.getLogger(FFmpegEsrganProvider.class);

    private final String ffmpegPath;
    private final String esrganPath;

    public FFmpegEsrganProvider(String ffmpegPath, String esrganPath) {
        this.ffmpegPath = ffmpegPath.isBlank() ? "ffmpeg" : ffmpegPath;
        this.esrganPath = esrganPath;
    }

    @Override public String getName()           { return "FFmpeg + Real-ESRGAN (Local)"; }
    @Override public String getShortId()        { return "FFmpegESRGAN"; }
    @Override public boolean supportsPrompt()   { return false; }
    @Override public boolean supportsVideo()    { return true; }
    @Override public String estimatedCostPerImage() { return "Free (local)"; }

    @Override
    public boolean isConfigured() {
        return esrganPath != null && !esrganPath.isBlank() && Files.exists(Paths.get(esrganPath));
    }

    @Override
    public EnhancementResult enhance(EnhancementRequest request) throws IOException {
        Path input = request.inputPath();
        String filename = input.getFileName().toString();
        String scale = request.param("scale", "4");
        String model = request.param("model", "realesrgan-x4plus");

        logger.info("FFmpeg+ESRGAN: processing {} ({}x upscale)", filename, scale);

        // Create temp dirs for frames
        Path tempDir = Files.createTempDirectory("album-ffmpeg-esrgan-");
        Path framesIn  = tempDir.resolve("frames_in");
        Path framesOut = tempDir.resolve("frames_out");
        Files.createDirectories(framesIn);
        Files.createDirectories(framesOut);

        try {
            // Step 1: extract frames
            String framePattern = framesIn.resolve("frame%06d.png").toString();
            runProcess("Extract frames",
                ffmpegPath, "-y", "-i", input.toString(),
                "-q:v", "1", framePattern);

            // Step 2: upscale frames with Real-ESRGAN
            runProcess("Upscale frames",
                esrganPath,
                "-i", framesIn.toString(),
                "-o", framesOut.toString(),
                "-n", model,
                "-s", scale,
                "-f", "png");

            // Step 3: get original frame rate
            String fps = getFrameRate(input);

            // Step 4: reassemble video
            String upscaledPattern = framesOut.resolve("frame%06d.png").toString();
            Path outputPath = resolveOutputPath(input, ".mp4", request);
            runProcess("Reassemble video",
                ffmpegPath, "-y",
                "-framerate", fps,
                "-i", upscaledPattern,
                "-i", input.toString(),
                "-map", "0:v", "-map", "1:a?",
                "-c:v", "libx264", "-crf", "18", "-preset", "slow",
                "-c:a", "copy",
                "-pix_fmt", "yuv420p",
                outputPath.toString());

            logger.info("FFmpeg+ESRGAN result saved to {}", outputPath);
            return EnhancementResult.ok(outputPath);

        } finally {
            deleteDir(tempDir);
        }
    }

    private String getFrameRate(Path input) {
        try {
            Process proc = new ProcessBuilder(
                ffmpegPath, "-i", input.toString()
            ).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("fps")) {
                        // e.g. "29.97 fps" or "30 fps"
                        for (String token : line.split(",")) {
                            token = token.trim();
                            if (token.endsWith(" fps")) {
                                return token.replace(" fps", "").trim();
                            }
                        }
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            logger.debug("Could not detect frame rate: {}", e.getMessage());
        }
        return "30";
    }

    private void runProcess(String label, String... cmd) throws IOException {
        logger.debug("FFmpeg+ESRGAN [{}]: {}", label, String.join(" ", cmd));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new IOException(label + " failed to start: " + e.getMessage(), e);
        }
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exit;
        try {
            exit = proc.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(label + " interrupted");
        }
        if (exit != 0) {
            throw new IOException(label + " failed (exit " + exit + "):\n" + output);
        }
    }

    private void deleteDir(Path dir) {
        try {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {}
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
